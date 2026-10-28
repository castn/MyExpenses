package org.totschnig.myexpenses.next.data.budgets

import androidx.annotation.StringRes
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.words.Words

/**
 * A pot to start from, e.g. "Ausgehen", with the categories of the user it is likely to hold
 *
 * @param icon of the [org.totschnig.myexpenses.next.icons.IconCatalog], taken over by the new pot
 * @param keywords found in the labels of categories, in German and English, at the start of a word;
 * one starting with "=" only as a whole word, for short ones like "bar"
 */
enum class PotTemplate(
    @StringRes val nameRes: Int,
    val kind: PotKind,
    val icon: String,
    private vararg val keywords: String,
) {
    DAILY(R.string.next_pot_daily, PotKind.RUNNING, "cart-shopping", "lebensmittel", "supermarkt", "bäcker", "metzger", "wochenmarkt", "getränke", "drogerie", "groceries", "food"),
    GOING_OUT(R.string.next_pot_going_out, PotKind.RUNNING, "martini-glass", "essen gehen", "restaurant", "café", "cafe", "imbiss", "ausgehen", "=bar", "kino", "konzert", "dining", "cinema"),
    RESTAURANTS(R.string.next_pot_restaurants, PotKind.RUNNING, "utensils", "restaurant", "café", "cafe", "=bar", "=bars", "dining"),
    DELIVERY(R.string.next_pot_delivery, PotKind.RUNNING, "pizza-slice", "lieferdienst", "delivery"),
    MOBILITY(R.string.next_pot_mobility, PotKind.RUNNING, "car", "mobilität", "kraftstoff", "tanken", "öpnv", "=bahn", "taxi", "carsharing", "parken", "laden e auto", "transport", "fuel"),
    SHOPPING(R.string.next_pot_shopping, PotKind.RUNNING, "bag-shopping", "kleidung", "schuhe", "accessoires", "elektronik", "shopping", "clothing"),
    LEISURE(R.string.next_pot_leisure, PotKind.RUNNING, "puzzle-piece", "freizeit", "hobby", "kino", "konzerte", "museen", "bücher", "leisure", "hobbies"),
    FITNESS(R.string.next_pot_fitness, PotKind.RUNNING, "dumbbell", "fitness", "sport", "vereinsbeiträge", "gym"),
    PET(R.string.next_pot_pet, PotKind.RUNNING, "paw", "haustier", "futter", "tierarzt", "pet", "pets"),
    CARE(R.string.next_pot_care, PotKind.RUNNING, "pump-soap", "körperpflege", "friseur", "kosmetik", "personal care"),
    CHILDREN(R.string.next_pot_children, PotKind.RUNNING, "children", "kinder", "children", "kids"),
    GAMING(R.string.next_pot_gaming, PotKind.RUNNING, "gamepad", "gaming", "games"),
    GIFTS(R.string.next_pot_gifts, PotKind.YEARLY, "gift", "geschenke", "feiern", "gifts"),
    CLOTHING(R.string.next_pot_clothing, PotKind.YEARLY, "shirt", "kleidung", "schuhe", "clothing"),
    CHRISTMAS(R.string.next_pot_christmas, PotKind.YEARLY, "gifts", "weihnacht", "christmas"),
    CAR(R.string.next_pot_car, PotKind.YEARLY, "oil-can", "werkstatt", "tüv", "wartung", "car repair"),
    HEALTH(R.string.next_pot_health, PotKind.YEARLY, "heart-pulse", "arzt", "zahnarzt", "apotheke", "brille", "doctor", "pharmacy"),
    HOME(R.string.next_pot_home, PotKind.YEARLY, "couch", "möbel", "einrichtung", "haushaltsgeräte", "haushaltswaren", "furniture"),
    HOLIDAYS(R.string.next_pot_holidays, PotKind.YEARLY, "umbrella-beach", "urlaub", "reisen", "vacation", "travel"),
    EDUCATION(R.string.next_pot_education, PotKind.YEARLY, "graduation-cap", "bildung", "kurse", "weiterbildung", "education"),
    DONATIONS(R.string.next_pot_donations, PotKind.YEARLY, "hand-holding-heart", "spenden", "donations", "charity"),
    GOAL(R.string.next_pot_goal, PotKind.GOAL, "piggy-bank"),
    PROJECT(R.string.next_pot_project, PotKind.PROJECT, "suitcase-rolling"),
    ITEM_GROUP(R.string.next_pot_item_group, PotKind.ITEM_GROUP, "basket-shopping");

    /**
     * The categories of [categories] this template holds: a main category if it matches, else the
     * matching subcategories, so that each is listed once with what lies below it
     */
    fun categoriesIn(categories: Collection<CategoryInfo>): Set<Long> {
        if (keywords.isEmpty()) return emptySet()
        val matching = categories.filter { it.isExpense && matches(it.label) }.mapTo(HashSet()) { it.id }
        val byId = categories.associateBy { it.id }
        fun hasMatchingAncestor(category: CategoryInfo): Boolean =
            generateSequence(category.parentId?.let(byId::get)) { it.parentId?.let(byId::get) }.any { it.id in matching }
        return categories.filter { it.id in matching && !hasMatchingAncestor(it) }.mapTo(LinkedHashSet()) { it.id }
    }

    private fun matches(label: String): Boolean {
        val text = Words.textOf(label)
        return keywords.any { keyword ->
            if (keyword.startsWith("=")) Words.isWord(text, keyword.substring(1)) else Words.startsWord(text, keyword)
        }
    }
}
