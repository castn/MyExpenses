package org.totschnig.myexpenses.designsystem

/** Where a step of a guide is, see [GuideStepRow] */
enum class GuideStepStatus {
    /** Not started yet */
    OPEN,

    /** The step to go on with */
    CURRENT,

    /** Gone through to the end */
    DONE,

    /** Skipped by the user, can still be done */
    SKIPPED
}
