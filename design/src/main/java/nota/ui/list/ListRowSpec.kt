package nota.ui.list

import nota.ui.theme.ShellSpacing

/**
 * Geometry SSOT for list rows. Values are logical dp (compose applies `.dp`).
 * ref: meizu-common AAR `mz_basics_list_item_*` + `values.xml` dimen
 */
data class ListRowSpec(
    /** ref: Widget.Flyme.List.Layout paddingStart/End → mz_list_publish_padding */
    val horizontalPadding: Float = ShellSpacing.Large,
    /** ref: mz_list_item_content_vertical_padding_level5 (single-line) */
    val verticalPaddingTitleOnly: Float = 18f,
    /**
     * DoubleLine aligns with `mz_basics_list_item_double_line_large.xml`
     * (`ContainerLinearLayout.Large` → level4 = 16dip). The normal variant is
     * asymmetric 12/13 and is not a separate [ListRowVariant] yet.
     */
    val verticalPaddingWithSummary: Float = 16f,
    /** ref: mz_list_summary_item_text_padding_top_level1 */
    val titleSummaryGap: Float = 2f,
    /** ref: fd_sys_typescale_body_large_size / mz_list_item_title_text_size */
    val titleTextSize: Float = 17f,
    /**
     * ref: TextAppearance.Flyme.Body.Mini → fd_sys_typescale_body_mini_size, the summary
     * style `layout-v24/mz_preference.xml` applies. The pre-v24 layout uses
     * `mz_list_item_text_3_size` = 12sp, which no v24+ device loads.
     */
    val summaryTextSize: Float = 14f,
    /**
     * Library-own touch-target floor, not Flyme parity: every `mz_basics_list_item_*` root
     * is `wrap_content`. 56 is the arithmetic of 2 × level5(18dip) + 17sp.
     */
    val minHeight: Float = 56f,
    /** ref: mz_list_item_divider_height */
    val dividerThickness: Float = 0.67f,
    /** ref: mz_page_padding_default (mz_preference_category_no_title divider) */
    val dividerInsetStart: Float = 20f,
    /** ref: mz_page_padding_default */
    val dividerInsetEnd: Float = 20f,
    /**
     * Deliberate deviation. `mz_basics_list_item_single_line_arrow.xml` uses an
     * `ImageFontCompatView`, whose `fontTextSize` resolves through
     * `mzIconFontCompatViewStyle` → `Widget.Flyme.IconFontStyle` = 24dp (class fallback
     * `DEFAULT_TEXT_SIZE_IF_FONT` = 25 off a Flyme theme). Shell draws a vector chevron
     * rather than an icon glyph, so it uses a smaller box.
     */
    val arrowSize: Float = 16f,
    /** ref: mz_basics_list_item_single_line_arrow 20dp spacer View */
    val trailingGap: Float = 20f,
    /**
     * Library-own: no `mz_basics_list_item_*` layout has an icon node. Value follows the
     * nearest Flyme icon gap, `mz_preference_icon_margin_horizontal` = 16dip.
     */
    val leadingGap: Float = 16f,
)
