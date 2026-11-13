package org.totschnig.myexpenses.next.feature.setup

import org.totschnig.myexpenses.next.data.setup.SetupOffer
import org.totschnig.myexpenses.next.data.setup.SetupReminder
import org.totschnig.myexpenses.next.data.setup.SetupStep

/** What the user can do in the setup, besides deciding on a card, see [PayeeCardActions] */
interface SetupActions {

    /** The transactions that can be categorized for sure get their category, or not */
    fun answerBacklog(accept: Boolean)

    /** Takes back the last decision of this visit, its payee is shown again */
    fun back()

    /** The user goes on: the payees put off come again, else those beyond the target */
    fun goOn()

    /** The user went through [step], or [skipped] it and it stays open */
    fun endStep(step: SetupStep, skipped: Boolean)

    /** The user finished or skipped the whole setup, which is not offered anymore */
    fun close()
}

/**
 * What the user can do on the card of a payee: choose an answer and a category, which only fill
 * in the card, then confirm it with [confirm] ("Next") or put the payee off with [later]
 */
interface PayeeCardActions {

    /** The card of [card] is shown, from now on the time to decide counts */
    fun cardShown(card: PayeeCard)

    /** The user chose [answer] for [card], see [PayeeAnswer] */
    fun answer(card: PayeeCard, answer: PayeeAnswer)

    /**
     * The category picker is opened for [card], see [picked]. With [transactionIds] only these
     * debits of the payee get the category at once, without a rule: they are for different things
     * ("Unterschiedlich").
     */
    fun pickCategory(card: PayeeCard, transactionIds: List<Long>? = null)

    /**
     * The category picker returned [categoryId], null for none: it fills in the card of
     * [pickCategory], or the cash withdrawals, or gives the debits chosen their category
     */
    fun picked(categoryId: Long?)

    /** The suggestion offered on [card] is filled in, see [PayeeCard.offer] */
    fun takeSuggestion(card: PayeeCard)

    /** Whether a choice also applies to later transactions of the payee ("Immer so machen") */
    fun setAlways(always: Boolean)

    /**
     * The answer of [card] is confirmed ("Next"): its debits get the category, or the payee is one
     * the user knows no category for; [PayeeAnswer.DIFFERS] opens its debits instead
     */
    fun confirm(card: PayeeCard)

    /** The payee comes again after the others ("Später") */
    fun later(card: PayeeCard)
}

/** What the user can do with the cash withdrawals in the setup, see [CashCard] */
interface CashActions {

    /** The category picker is opened for the cash withdrawals, its choice shows on the card */
    fun pickCashCategory()

    /** The suggestion offered on [card] is filled in, see [CashCard.offer] */
    fun takeCashSuggestion(card: CashCard)

    /** The user chose to keep cash in a new cash account, or else as spending */
    fun chooseCashAccount(toAccount: Boolean)

    /** The user typed [text] as what the cash account holds now */
    fun setCashBalance(text: String)

    /**
     * The cash withdrawals so far get the category of [card] ("Next"). With [balance], what the
     * user holds in cash now in minor units of the home currency, a cash account is created for
     * later ones; otherwise they get the category too.
     */
    fun keepCash(card: CashCard, balance: Long?)

    /** The cash withdrawals come again after the payees ("Später") */
    fun leaveCash()
}

/** What the user can do with the card of the setup on the overview, see [SetupOffer] */
interface SetupCardActions {

    /** The setup was opened, from now on its card is small */
    fun opened()

    /** The small card is put away for a while, see [SetupReminder.dismissed] */
    fun dismissCard()
}
