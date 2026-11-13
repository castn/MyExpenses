package org.totschnig.myexpenses.next.data.setup

/** The steps of the guided setup, in their order */
enum class SetupStep {
    /** Confirming the regular incomes detected and choosing the salary, see [SetupContracts] */
    INCOME,

    /** Categories for the payees with the most money, see [categoryQueue] */
    CATEGORIES,

    /** Confirming the contracts detected, see [SetupContracts] */
    CONTRACTS
}

/**
 * How far the user is in the guided setup
 *
 * @param finished steps the user went through to the end
 * @param skipped steps the user skipped, offered again from the overview
 * @param closed the user finished or skipped the whole setup, which is not offered anymore
 * @param pace how fast the user decides, see [Pace]
 * @param reminder what the user did with the card on the overview, see [offer]
 */
data class SetupProgress(
    val finished: Set<SetupStep> = emptySet(),
    val skipped: Set<SetupStep> = emptySet(),
    val closed: Boolean = false,
    val pace: Pace = Pace(),
    val reminder: SetupReminder = SetupReminder()
) {
    /** The steps of [available] not finished yet, skipped ones included, in their order */
    fun open(available: Collection<SetupStep>): List<SetupStep> =
        SetupStep.entries.filter { it in available && it !in finished }

    /** The step to go on with: the first open one not skipped, else the first skipped one */
    fun next(available: Collection<SetupStep>): SetupStep? =
        open(available).let { open -> open.firstOrNull { it !in skipped } ?: open.firstOrNull() }
}
