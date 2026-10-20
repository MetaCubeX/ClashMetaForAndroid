package nota.ui.settings

/**
 * Immersive preference group card corners.
 * ref: immersive_mz_preference_singleline_{head,middle,foot,alone}.xml
 */
enum class PreferenceGroupPosition {
    /** Top card: outer top padding + top 10dp smooth corners. */
    Head,

    /** Mid card: inner padding, square corners. */
    Middle,

    /** Bottom card: outer bottom padding + bottom 10dp smooth corners. */
    Foot,

    /** Single-item card: outer padding + 10dp smooth corners on all sides. */
    Alone,
}
