package org.totschnig.myexpenses.next.data.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagLearnerTest {

    private val dog = 1L
    private val trip = 2L
    private val household = 3L
    private var nextId = 1L

    private fun transaction(counterpart: String?, vararg tags: Long) =
        TagInput(nextId++, counterpart, counterpart?.substringAfter(':'), tags.toSet())

    private val learnable: (Long) -> Boolean = { it != trip }

    @Test
    fun tagOfMostTransactionsIsSuggestedForTheOthers() {
        val tagged = (1..4).map { transaction("p:fressnapf", dog) }
        val untagged = transaction("p:fressnapf")
        val suggestions = TagLearner.suggest(tagged + untagged + transaction("p:rewe"), learnable)
        val suggestion = suggestions.values.single()
        assertEquals(untagged.id, suggestion.transactionId)
        assertEquals(dog, suggestion.tagId)
        assertEquals("fressnapf", suggestion.counterpartName)
        assertEquals(4, suggestion.tagged)
        assertEquals(listOf(untagged.id), suggestion.others)
    }

    @Test
    fun tooFewOrMixedTagsAreNotLearned() {
        val few = (1..2).map { transaction("p:fressnapf", dog) } + transaction("p:fressnapf")
        assertTrue(TagLearner.suggest(few, learnable).isEmpty())
        val mixed = (1..3).map { transaction("p:obi", dog) } + (1..3).map { transaction("p:obi", household) } +
                transaction("p:obi")
        assertTrue(TagLearner.suggest(mixed, learnable).isEmpty())
    }

    @Test
    fun situationalTagsAndUnknownCounterpartsAreNotLearned() {
        val trips = (1..4).map { transaction("p:hotel", trip) } + transaction("p:hotel")
        val noCounterpart = (1..4).map { transaction(null, dog) } + transaction(null)
        assertTrue(TagLearner.suggest(trips + noCounterpart, learnable).isEmpty())
    }

    @Test
    fun rejectedSuggestionIsDropped() {
        val tagged = (1..3).map { transaction("p:fressnapf", dog) }
        val untagged = transaction("p:fressnapf")
        assertTrue(TagLearner.suggest(tagged + untagged, learnable, rejected = setOf("${untagged.id}:$dog")).isEmpty())
    }

    @Test
    fun rulesTagNewTransactionsOnly() {
        val old = transaction("p:fressnapf")
        val rule = TagRule("p:fressnapf", dog, afterId = old.id)
        val new = transaction("p:fressnapf")
        val alreadyTagged = transaction("p:fressnapf", dog)
        val other = transaction("p:rewe")
        assertEquals(mapOf(new.id to setOf(dog)), TagLearner.applyRules(listOf(old, new, alreadyTagged, other), listOf(rule)))
    }

    @Test
    fun rulesRoundTrip() {
        val rules = listOf(TagRule("p:12", 7, 5003), TagRule("m:zalando", 9, 10))
        assertEquals(rules, TagRule.decode(TagRule.encode(rules)))
        assertEquals(listOf(TagRule("n:obi", 1, 2)), TagRule.decode("n:obi|1|2;broken;|3|4;p:1|x|2"))
    }
}
