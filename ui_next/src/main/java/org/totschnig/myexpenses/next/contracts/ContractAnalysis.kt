package org.totschnig.myexpenses.next.contracts

import org.totschnig.myexpenses.next.balance.SalaryChoice
import java.time.LocalDate

/**
 * Contracts and regular incomes found in [transactions]: confirmed ones from the rules of the user,
 * suggestions from [ContractDetector] for the payments no rule covers, and the payments the user
 * declared as no contract.
 *
 * @param contracts confirmed contracts and suggestions
 * @param dismissed payments of rules of kind [ContractRule.Kind.IGNORE], one entry per rule
 */
class ContractAnalysis(
    val transactions: List<ContractTransaction>,
    val contracts: List<Contract>,
    val dismissed: List<Contract>,
) {
    val suggestions: List<Contract> get() = contracts.filter { !it.isConfirmed }

    companion object {
        fun of(transactions: List<ContractTransaction>, rules: List<ContractRule>, today: LocalDate): ContractAnalysis {
            val match = RuleMatcher(today).apply(rules, transactions)
            val rulesById = rules.associateBy { it.id }
            return ContractAnalysis(
                transactions = transactions,
                contracts = match.confirmed + ContractDetector(today).detect(match.remaining),
                dismissed = match.ignored.map { (id, payments) ->
                    val rule = rulesById.getValue(id)
                    contractOf(rule.key, payments, rule.interval, rule.direction, today)
                        .copy(customName = rule.name)
                }
            )
        }
    }
}

/**
 * Rules for the suggestions that are certain enough to be confirmed without asking the user.
 * Only active ones: the payee of an ended contract may later be paid for other things.
 *
 * @param existing rules already stored: suggestions with payments they cover get no rule, so that
 * nothing is added twice, e.g. when the analysis ran again before the stored rules arrived
 */
fun ContractAnalysis.automaticRules(existing: List<ContractRule>): List<ContractRule> =
    suggestions
        .filter { it.isConfident && it.isActive }
        .filter { contract -> contract.transactions.none { payment -> existing.any { it.matches(payment) } } }
        .map { ContractRule.of(it, transactions) }

/**
 * Takes over the decisions the user made before there were rules: they were stored by the
 * signature of a detected contract, now they belong to a rule.
 *
 * @param analysis of all transactions without any rules
 * @return the rules, and the salary choice with signatures replaced by the keys of the rules
 */
fun migrateToRules(settings: ContractSettings, analysis: ContractAnalysis): Pair<List<ContractRule>, SalaryChoice> {
    val salarySignatures = (settings.salary as? SalaryChoice.Fixed)?.signatures.orEmpty()
    val keys = mutableMapOf<String, String>()
    val rules = analysis.contracts.mapNotNull { contract ->
        val signature = contract.signature
        val kind = when {
            signature in settings.dismissed -> ContractRule.Kind.IGNORE
            signature in settings.names || signature in settings.areas || signature in salarySignatures ->
                ContractRule.Kind.CONTRACT

            else -> return@mapNotNull null
        }
        ContractRule.of(
            contract.copy(customName = settings.names[signature], areaChoice = settings.areaChoice(signature)),
            analysis.transactions,
            kind
        ).also { keys[signature] = it.key }
    }
    val salary = when (val choice = settings.salary) {
        is SalaryChoice.Fixed -> choice.signatures.mapNotNull { keys[it] }.toSet()
            .let { if (it.isEmpty()) SalaryChoice.Automatic else SalaryChoice.Fixed(it) }

        else -> choice
    }
    return rules to salary
}

/**
 * Applies [change] to the rule of [contract], creating one for a suggestion, which confirms it
 *
 * @return all rules, and the rule of the contract
 */
fun List<ContractRule>.withRuleFor(
    contract: Contract,
    transactions: List<ContractTransaction>,
    change: (ContractRule) -> ContractRule = { it },
): Pair<List<ContractRule>, ContractRule> {
    val existing = find { it.key == contract.signature }
    return if (existing != null) {
        val changed = change(existing)
        map { if (it === existing) changed else it } to changed
    } else {
        val created = change(ContractRule.of(contract, transactions))
        this + created to created
    }
}

/**
 * The user declares [contract] as no contract: its rule, if confirmed, turns into one that ignores its payments
 */
fun List<ContractRule>.dismissing(contract: Contract, transactions: List<ContractTransaction>): List<ContractRule> {
    val existing = find { it.key == contract.signature }
    return if (existing != null) map { if (it === existing) it.copy(kind = ContractRule.Kind.IGNORE) else it }
    else this + ContractRule.of(contract, transactions, ContractRule.Kind.IGNORE)
}

/**
 * The user takes back that the payments of [contract] (from [ContractAnalysis.dismissed]) are no contract,
 * which says they are one: the rule is confirmed, keeping name and category
 */
fun List<ContractRule>.restoring(contract: Contract): List<ContractRule> =
    map { if (it.key == contract.signature) it.copy(kind = ContractRule.Kind.CONTRACT) else it }
