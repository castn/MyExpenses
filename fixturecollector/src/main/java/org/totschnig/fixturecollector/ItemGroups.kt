package org.totschnig.fixturecollector

import androidx.annotation.StringRes

/**
 * Ids and labels of ItemGroup in ui_next, which the app cannot depend on.
 * Keep in sync when a group is added there.
 */
enum class ItemGroups(val id: String, @StringRes val label: Int) {
    PRODUCE("produce", R.string.group_produce),
    BAKERY("bakery", R.string.group_bakery),
    DAIRY("dairy", R.string.group_dairy),
    MEAT_FISH("meat_fish", R.string.group_meat_fish),
    PANTRY("pantry", R.string.group_pantry),
    READY_MEALS("ready_meals", R.string.group_ready_meals),
    SWEETS_SNACKS("sweets_snacks", R.string.group_sweets_snacks),
    DRINKS("drinks", R.string.group_drinks),
    ALCOHOL("alcohol", R.string.group_alcohol),
    TOBACCO("tobacco", R.string.group_tobacco),
    DRUGSTORE("drugstore", R.string.group_drugstore),
    HOUSEHOLD("household", R.string.group_household),
    PET("pet", R.string.group_pet),
    BABY("baby", R.string.group_baby),
    NON_FOOD("non_food", R.string.group_non_food),
    DEPOSIT("deposit", R.string.group_deposit),
    OTHER("other", R.string.group_other);

    companion object {
        fun ofId(id: String?) = entries.firstOrNull { it.id == id }
    }
}
