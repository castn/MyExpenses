package org.totschnig.myexpenses.next.core.ui.receipts

import androidx.annotation.StringRes
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.receipts.ItemGroup

/** The name of the group shown to the user */
@get:StringRes
val ItemGroup.labelRes: Int
    get() = when (this) {
        ItemGroup.PRODUCE -> R.string.next_item_group_produce
        ItemGroup.BAKERY -> R.string.next_item_group_bakery
        ItemGroup.DAIRY -> R.string.next_item_group_dairy
        ItemGroup.MEAT_FISH -> R.string.next_item_group_meat_fish
        ItemGroup.PANTRY -> R.string.next_item_group_pantry
        ItemGroup.READY_MEALS -> R.string.next_item_group_ready_meals
        ItemGroup.SWEETS_SNACKS -> R.string.next_item_group_sweets_snacks
        ItemGroup.DRINKS -> R.string.next_item_group_drinks
        ItemGroup.ALCOHOL -> R.string.next_item_group_alcohol
        ItemGroup.TOBACCO -> R.string.next_item_group_tobacco
        ItemGroup.DRUGSTORE -> R.string.next_item_group_drugstore
        ItemGroup.HOUSEHOLD -> R.string.next_item_group_household
        ItemGroup.PET -> R.string.next_item_group_pet
        ItemGroup.BABY -> R.string.next_item_group_baby
        ItemGroup.NON_FOOD -> R.string.next_item_group_non_food
        ItemGroup.DEPOSIT -> R.string.next_item_group_deposit
        ItemGroup.OTHER -> R.string.next_item_group_other
    }
