package universe.agentic.ui.search

/**
 * Layout tokens for [SearchField] (values in density-independent pixels).
 *
 * Stored as [Float] so commonMain stays Compose-free; compose converts via `.dp`.
 */
data class SearchFieldDimens(
    val barHeightDefault: Float = 36f,
    val touchTargetMin: Float = 40f,
    val cornerPopup: Float = 8f,
    val spaceM: Float = 12f,
    val spaceS: Float = 8f,
    val spaceXs: Float = 4f,
    val iconSize: Float = 16f,
    val clearButtonSize: Float = 24f,
) {
    companion object {
        val Default: SearchFieldDimens = SearchFieldDimens()
    }
}
