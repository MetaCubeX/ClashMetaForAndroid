package nota.ui.list

enum class ListRowVariant {
    /** ref: mz_basics_list_item_single_line_normal */
    SingleLine,

    /** ref: mz_basics_list_item_single_line_arrow */
    SingleLineArrow,

    /**
     * Title + optional summary.
     * Geometry aligns with `mz_basics_list_item_double_line_large.xml` (16/16, title 17sp),
     * not `..._double_line_normal.xml` (12/13, title 14sp).
     */
    DoubleLine,
}
