package org.totschnig.myexpenses.next.feature.setup

import org.totschnig.myexpenses.next.data.setup.SetupStep

/** What the user can do in the setup */
interface SetupActions {

    /** The transactions that can be categorized for sure get their category, or not */
    fun answerBacklog(accept: Boolean)

    /** The card of [card] is shown, from now on the time to decide counts */
    fun cardShown(card: PayeeCard)

    /** The debits of the payee get the suggested category */
    fun acceptSuggestion(card: PayeeCard)

    /**
     * The category picker is opened for [card], see [picked]. With [transactionIds] only these
     * debits of the payee get the category, without a rule: they are for different things
     * ("Unterschiedlich").
     */
    fun pickCategory(card: PayeeCard, transactionIds: List<Long>? = null)

    /** The category picker returned [categoryId] for the card of [pickCategory], null for none */
    fun picked(categoryId: Long?)

    /** Whether a choice also applies to later transactions of the payee ("Immer so machen") */
    fun setAlways(always: Boolean)

    /**
     * The payee stays without category: it comes again after the others ("Später"), or with
     * [unclear] the user knows no category for it ("Weiß ich nicht")
     */
    fun leave(card: PayeeCard, unclear: Boolean)

    /** Takes back the last decision of this visit, its payee is shown again */
    fun back()

    /** The user goes on: the payees put off come again, else those beyond the target */
    fun goOn()

    /** The user went through [step], or [skipped] it and it stays open */
    fun endStep(step: SetupStep, skipped: Boolean)

    /** The user finished or skipped the whole setup, which is not offered anymore */
    fun close()
}
