package org.totschnig.myexpenses.next.data.tips

import org.totschnig.myexpenses.next.core.model.words.Words
import org.totschnig.myexpenses.next.data.contracts.Contract

/** E.g. a mobile phone insurance is no mobile contract */
private val INSURANCE = listOf("versicherung", "insurance")

/**
 * What a contract is for, finer than its [org.totschnig.myexpenses.next.contracts.ContractArea].
 * Only kinds that can be told apart reliably by name are recognized. Providers offering several
 * kinds, e.g. Telekom or Stadtwerke, give no hint.
 *
 * @param patterns matched at the start of a word, may span several words, e.g. "apple music"
 * @param words matched as whole words only, for short ones like "gas", which starts "Gastronomie"
 * @param excluded rule the kind out wherever they appear, also within a word, e.g. "Kfz-Haftpflicht"
 * is no private liability insurance, "Handyversicherung" no mobile contract
 * @param isUsuallyOne one contract of the kind is usually enough, unlike e.g. one car insurance per car
 * @param isSwitchable providers compete on price, staying with one for years often costs more
 * @param isSubscription often cheaper when paid for a year at once
 */
enum class ContractKind(
    private val patterns: List<String>,
    private val words: List<String> = emptyList(),
    private val excluded: List<String> = emptyList(),
    val isUsuallyOne: Boolean = true,
    val isSwitchable: Boolean = false,
    val isSubscription: Boolean = false,
) {
    CAR_INSURANCE(
        patterns = listOf(
            "kfz versicherung", "kfzversicherung", "kfz haftpflicht", "kfzhaftpflicht", "autoversicherung", "teilkasko",
            "vollkasko", "car insurance", "motor insurance", "vehicle insurance"
        ),
        isUsuallyOne = false,
        isSwitchable = true
    ),
    LIABILITY_INSURANCE(
        patterns = listOf("privathaftpflicht", "haftpflicht", "personal liability"),
        excluded = listOf(
            "kfz", "auto", "fahrzeug", "motorrad", "tierhalter", "hunde", "pferde", "bauherr", "berufshaftpflicht",
            "diensthaftpflicht", "amtshaftpflicht", "vermögensschaden", "gewässer", "vehicle"
        )
    ),
    CONTENTS_INSURANCE(listOf("hausrat", "home contents", "contents insurance")),
    LEGAL_INSURANCE(listOf("rechtsschutz", "legal expenses", "legal protection")),
    VIDEO_STREAMING(
        listOf(
            "netflix", "disney", "dazn", "paramount", "joyn", "rtl+", "rtl plus", "sky ticket", "wow tv", "magenta tv",
            "magentatv", "waipu", "apple tv", "prime video", "crunchyroll", "mubi", "hbo"
        ),
        isSubscription = true
    ),
    MUSIC_STREAMING(listOf("spotify", "deezer", "tidal", "apple music", "youtube music", "amazon music"), isSubscription = true),
    CLOUD_STORAGE(listOf("icloud", "google one", "dropbox", "onedrive"), isSubscription = true),
    MOBILE(
        listOf(
            "mobilfunk", "handy", "congstar", "aldi talk", "fraenk", "winsim", "simyo", "blau de", "lidl connect",
            "klarmobil", "smartmobil", "mobile phone", "cell phone"
        ),
        excluded = INSURANCE,
        isSwitchable = true
    ),
    INTERNET(listOf("dsl", "vdsl", "glasfaser", "internet", "broadband"), excluded = INSURANCE, isSwitchable = true),
    ELECTRICITY(listOf("strom", "electricity", "vattenfall", "yello", "eprimo"), isSwitchable = true),
    GAS(listOf("erdgas", "gasabschlag", "gasversorgung"), words = listOf("gas"), isSwitchable = true),
    GYM(listOf("fitnessstudio", "mcfit", "fitx", "clever fit", "john reed", "fitness first", "urban sports"), words = listOf("gym"));

    /** @param text see [Words.textOf] */
    private fun matches(text: String) =
        (patterns.any { Words.startsWord(text, it) } || words.any { Words.isWord(text, it) }) &&
                excluded.none { Words.contains(text, it) }

    companion object {
        /**
         * From the name the user gave, the payee, the purpose and the category of the last payment
         */
        fun of(contract: Contract): ContractKind? = textOf(contract).let { text -> entries.firstOrNull { it.matches(text) } }

        /** See [Words.textOf] */
        internal fun textOf(contract: Contract): String {
            val last = contract.lastTransaction
            return Words.textOf(contract.customName, last.payeeName, last.comment, last.categoryPath)
        }
    }
}
