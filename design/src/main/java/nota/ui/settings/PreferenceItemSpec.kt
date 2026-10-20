package nota.ui.settings

import nota.ui.theme.ShellCorner

/**
 * Geometry SSOT for settings preference rows.
 * Values are logical dp (compose applies `.dp`).
 * ref: meizu-common AAR `mz_preference*.xml` / `immersive_mz_preference_singleline_*.xml`
 */
data class PreferenceItemSpec(
    /** ref: mz_page_padding_default — non-immersive `mz_preference.xml` */
    val horizontalPadding: Float = 20f,
    /** ref: immersive_mz_preference_* paddingStart / paddingEnd — literal 32dp */
    val immersiveHorizontalPadding: Float = 32f,
    /**
     * ref: immersive_item_bkg_{head,middle,foot,alone} `app:immersiveItemPadding` = 16dp;
     * `ImmersiveListItemDrawable.onBoundsChange` insets the card by it on both sides, so
     * [immersiveHorizontalPadding] is measured from the item, not the visible card edge.
     */
    val immersiveCardInset: Float = 16f,
    /**
     * Title-only vertical padding.
     * AAR `mz_preference_singleline` uses level3 = 14dip (developer doc says 18).
     */
    val verticalPaddingTitleOnly: Float = 14f,
    /** ref: mz_list_item_content_vertical_padding_level4 via Preference.ContentLinearLayout */
    val verticalPaddingWithSummary: Float = 16f,
    /** ref: mz_list_summary_item_text_padding_top_level1 / immersive summary marginTop */
    val titleSummaryGap: Float = 2f,
    /** ref: fd_sys_typescale_body_large_size */
    val titleTextSize: Float = 17f,
    /** ref: mz_list_item_summary_text_size */
    val summaryTextSize: Float = 14f,
    /** ref: mz_list_desc_line_spacing_extra */
    val summaryLineSpacingExtra: Float = 4f,
    /** ref: mz_list_item_divider_height */
    val dividerThickness: Float = 0.67f,
    /** ref: mz_page_padding_default */
    val dividerInsetStart: Float = 20f,
    /** ref: mz_page_padding_default */
    val dividerInsetEnd: Float = 20f,
    /**
     * Deliberate deviation. Origin `ImageFontCompatView` resolves `fontTextSize` through
     * `mzIconFontCompatViewStyle` → `Widget.Flyme.IconFontStyle` = 24dp (class fallback
     * `DEFAULT_TEXT_SIZE_IF_FONT` = 25 when the host theme is not a Flyme theme). Shell's
     * chevron is a vector path rather than an icon glyph, so it uses a smaller box.
     */
    val arrowSize: Float = 16f,
    /**
     * `layout-v24/mz_preference.xml` widget_frame declares no `minWidth`; the 43dp
     * `preference_widget_width` only survives in the pre-v24 layout, which no v24+ device loads.
     */
    val widgetMinWidth: Float = 0f,
    /** ref: mz_list_item_divider_margin_end — `layout-v24/mz_preference.xml` widget_frame paddingStart */
    val widgetGap: Float = 20f,
    /** ref: preference_item_padding_inner — immersive layouts put it on the title `paddingEnd` */
    val widgetGapImmersive: Float = 14f,
    /** ref: mz_preference_icon_margin_horizontal / mz_list_icon_and_text_padding */
    val leadingGap: Float = 16f,
    /** ref: mz_list_icon_medium_width — `mz_preference.xml` icon */
    val iconSize: Float = 46f,
    /** ref: mz_preference_singleline_image_size */
    val iconSizeSingleline: Float = 32f,
    /** ref: mz_list_icon_item_icon_corner_radius */
    val iconCornerRadius: Float = 7f,
    /**
     * Deliberate deviation. `mz_preference_singleline_height` (56dip) is only read by
     * `mz_preference_edittext_singleline.xml`; the modelled `mz_preference_singleline.xml`
     * root is `wrap_content` with no `minHeight`. Kept as a library-own touch-target floor.
     */
    val minHeightTitleOnly: Float = 56f,
    /**
     * Deliberate deviation. `mz_preference_min_height` (66dip) is only read by
     * `mz_preference_edittext.xml`; `mz_preference.xml` sets no `minHeight`.
     */
    val minHeightWithSummary: Float = 66f,
    /**
     * ref: fd_sys_radius_corner_smooth_small — immersive head/foot/alone.
     * Origin builds the shape with Material `RoundedCornerTreatment`
     * (`ImmersiveListItemDrawable.inflate`) despite the dimen name, so the curve is a
     * plain arc rather than a smooth corner.
     */
    val groupCornerRadius: Float = ShellCorner.Small,
    /** ref: immersive_mz_preference_singleline_head/alone paddingTop */
    val groupPaddingOuter: Float = 26f,
    /** ref: immersive_mz_preference_singleline_middle padding / head bottom / foot top */
    val groupPaddingInner: Float = 18f,
    /** ref: layout-v24/immersive_mz_preference_head/alone paddingTop — summary variant */
    val groupPaddingOuterWithSummary: Float = 24f,
    /** ref: layout-v24/immersive_mz_preference_middle padding — summary variant */
    val groupPaddingInnerWithSummary: Float = 16f,
)

/** Vertical content padding for one preference row edge (top or bottom). */
fun resolvePreferenceVerticalPadding(
    hasSummary: Boolean,
    spec: PreferenceItemSpec = PreferenceItemSpec(),
): Float =
    if (hasSummary) spec.verticalPaddingWithSummary else spec.verticalPaddingTitleOnly

/** Minimum row height including vertical padding and text block. */
fun resolvePreferenceMinHeight(
    hasSummary: Boolean,
    spec: PreferenceItemSpec = PreferenceItemSpec(),
): Float =
    if (hasSummary) spec.minHeightWithSummary else spec.minHeightTitleOnly

/** Horizontal padding measured from the item edge, as the origin layouts declare it. */
fun resolvePreferenceHorizontalPadding(
    spec: PreferenceItemSpec = PreferenceItemSpec(),
    groupPosition: PreferenceGroupPosition? = null,
): Float =
    if (groupPosition != null) spec.immersiveHorizontalPadding else spec.horizontalPadding

/**
 * Horizontal padding measured from the visible card edge. The immersive background is
 * inset by [PreferenceItemSpec.immersiveCardInset] on both sides, so the layout's 32dp
 * leaves 32 − 16 = 16dp between the card edge and the text.
 */
fun resolvePreferenceContentHorizontalPadding(
    spec: PreferenceItemSpec = PreferenceItemSpec(),
    groupPosition: PreferenceGroupPosition? = null,
): Float =
    resolvePreferenceHorizontalPadding(spec, groupPosition) -
        if (groupPosition != null) spec.immersiveCardInset else 0f

/** Gap between the text block and the trailing widget. */
fun resolvePreferenceWidgetGap(
    spec: PreferenceItemSpec = PreferenceItemSpec(),
    groupPosition: PreferenceGroupPosition? = null,
): Float = if (groupPosition != null) spec.widgetGapImmersive else spec.widgetGap

fun resolvePreferencePaddingTop(
    hasSummary: Boolean,
    spec: PreferenceItemSpec = PreferenceItemSpec(),
    groupPosition: PreferenceGroupPosition? = null,
): Float =
    when (groupPosition) {
        PreferenceGroupPosition.Head, PreferenceGroupPosition.Alone ->
            resolvePreferenceGroupPaddingOuter(hasSummary, spec)
        PreferenceGroupPosition.Middle, PreferenceGroupPosition.Foot ->
            resolvePreferenceGroupPaddingInner(hasSummary, spec)
        null -> resolvePreferenceVerticalPadding(hasSummary, spec)
    }

fun resolvePreferencePaddingBottom(
    hasSummary: Boolean,
    spec: PreferenceItemSpec = PreferenceItemSpec(),
    groupPosition: PreferenceGroupPosition? = null,
): Float =
    when (groupPosition) {
        PreferenceGroupPosition.Foot, PreferenceGroupPosition.Alone ->
            resolvePreferenceGroupPaddingOuter(hasSummary, spec)
        PreferenceGroupPosition.Head, PreferenceGroupPosition.Middle ->
            resolvePreferenceGroupPaddingInner(hasSummary, spec)
        null -> resolvePreferenceVerticalPadding(hasSummary, spec)
    }

/**
 * Outer edge padding of an immersive card: 26dp title-only
 * (`immersive_mz_preference_singleline_*`), 24dp with summary
 * (`layout-v24/immersive_mz_preference_*`).
 */
private fun resolvePreferenceGroupPaddingOuter(
    hasSummary: Boolean,
    spec: PreferenceItemSpec,
): Float = if (hasSummary) spec.groupPaddingOuterWithSummary else spec.groupPaddingOuter

/** Inner edge padding of an immersive card: 18dp title-only, 16dp with summary. */
private fun resolvePreferenceGroupPaddingInner(
    hasSummary: Boolean,
    spec: PreferenceItemSpec,
): Float = if (hasSummary) spec.groupPaddingInnerWithSummary else spec.groupPaddingInner

/** Top-edge smooth-corner radius for [groupPosition]; middle / null → 0. */
fun resolvePreferenceGroupCornerTop(
    groupPosition: PreferenceGroupPosition?,
    spec: PreferenceItemSpec = PreferenceItemSpec(),
): Float =
    when (groupPosition) {
        PreferenceGroupPosition.Head, PreferenceGroupPosition.Alone -> spec.groupCornerRadius
        else -> 0f
    }

/** Bottom-edge smooth-corner radius for [groupPosition]; middle / null → 0. */
fun resolvePreferenceGroupCornerBottom(
    groupPosition: PreferenceGroupPosition?,
    spec: PreferenceItemSpec = PreferenceItemSpec(),
): Float =
    when (groupPosition) {
        PreferenceGroupPosition.Foot, PreferenceGroupPosition.Alone -> spec.groupCornerRadius
        else -> 0f
    }
