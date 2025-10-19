package com.github.kr328.clash.design.util

/**
 * 值与索引/标签的映射工具，统一下拉的选中值与展示文案处理。
 */
class SelectionMapping<T>(
    private val values: List<T?>,
    private val labels: List<String>,
) {
    init {
        require(values.size == labels.size) { "values and labels must be same size" }
    }

    fun indexOf(value: T?): Int = values.indexOf(value).let { if (it == -1) labels.lastIndex else it }

    fun valueOf(index: Int): T? = values.getOrNull(index)

    fun allLabels(): List<String> = labels
}





