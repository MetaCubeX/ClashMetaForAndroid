package com.github.kr328.clash.service.util

/**
 * Classifies the engine's verdict on a merged subscription config, so we can tell
 * "our merge broke a valid subscription" apart from "the fetched subscription was
 * already broken". Pure + unit-testable; the native validation happens in
 * ProfileProcessor.
 */
enum class MergeEngineVerdict {
    /** Merged config is engine-valid. */
    Ok,

    /** Fetched config was valid, but the merge produced an invalid config — our bug. */
    MergeIntroduced,

    /** Fetched config was already invalid; the merge is not at fault. */
    PreexistingBroken;

    companion object {
        /**
         * @param fetchedError engine error for the freshly fetched config, or null if valid
         * @param mergedError  engine error for the merged config, or null if valid
         */
        fun classify(fetchedError: String?, mergedError: String?): MergeEngineVerdict = when {
            mergedError == null -> Ok
            fetchedError == null -> MergeIntroduced
            else -> PreexistingBroken
        }
    }
}
