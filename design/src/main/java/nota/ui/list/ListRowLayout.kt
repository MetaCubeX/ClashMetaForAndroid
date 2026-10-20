package nota.ui.list

/** Vertical padding (one side) from summary presence. */
fun resolveListRowVerticalPadding(
    hasSummary: Boolean,
    spec: ListRowSpec = ListRowSpec(),
): Float =
    if (hasSummary) spec.verticalPaddingWithSummary else spec.verticalPaddingTitleOnly

/** Vertical padding (one side) from [ListRowVariant]. */
fun resolveListRowVerticalPaddingForVariant(
    variant: ListRowVariant,
    spec: ListRowSpec = ListRowSpec(),
): Float =
    resolveListRowVerticalPadding(
        hasSummary = variant == ListRowVariant.DoubleLine,
        spec = spec,
    )

fun listRowHorizontalPadding(spec: ListRowSpec = ListRowSpec()): Float = spec.horizontalPadding

fun listRowMinHeight(spec: ListRowSpec = ListRowSpec()): Float = spec.minHeight

fun listRowTitleTextSize(spec: ListRowSpec = ListRowSpec()): Float = spec.titleTextSize

fun listRowSummaryTextSize(spec: ListRowSpec = ListRowSpec()): Float = spec.summaryTextSize

fun listRowTitleSummaryGap(spec: ListRowSpec = ListRowSpec()): Float = spec.titleSummaryGap

fun listRowDividerThickness(spec: ListRowSpec = ListRowSpec()): Float = spec.dividerThickness

fun listRowDividerInsetStart(spec: ListRowSpec = ListRowSpec()): Float = spec.dividerInsetStart

fun listRowDividerInsetEnd(spec: ListRowSpec = ListRowSpec()): Float = spec.dividerInsetEnd

fun listRowArrowSize(spec: ListRowSpec = ListRowSpec()): Float = spec.arrowSize

fun listRowTrailingGap(spec: ListRowSpec = ListRowSpec()): Float = spec.trailingGap

fun listRowLeadingGap(spec: ListRowSpec = ListRowSpec()): Float = spec.leadingGap
