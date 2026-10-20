---
title: "设置项控件"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-16
summary: "preference 包仍提供文本、列表和映射编辑器。设置子页已改成 Compose，不再调用 preferenceScreen"
---

# 设置项控件

包路径是 `design/.../preference/`。这些控件改绑定的 `KMutableProperty0`，不往 `Design.requests` 发请求。设置子页已经改成 Compose，不再调用 `preferenceScreen`。覆写和 Meta 的单行文本、列表和映射仍打开这里的编辑器。哪个屏幕使用它们，见 [屏幕与请求通道](screens.md)。

## 屏幕

`preferenceScreen` 建一个垂直的 `LinearLayout`，并把它做成 `PreferenceScreen`。这个接口同时是调用方的 `CoroutineScope`，在 `*Design` 里就是 `Design` 的 `Unconfined` 作用域。`addElement` 把控件视图加进去，宽 `MATCH_PARENT`，高 `WRAP_CONTENT`。

`Preference` 有 `view` 和 `enabled`。默认的 `enabled` 会改 `isEnabled`、`isClickable`、`isFocusable`；禁用时 `alpha` 为 0.33，启用时为 1。`OnChangedListener` 只有 `onChanged()`。

## 控件

`category` 把字符串资源填进分类布局，没有点击，也没有属性。它的 `enabled` 只改 `isEnabled`。

`tips` 用 `context.getHtml` 把字符串资源设成提示文字，返回 `TipsPreference`。它的 `enabled` 同样只改 `isEnabled`。

`clickable` 是一行标题。图标为 `null` 时图标隐藏，摘要为 `null` 时摘要隐藏。`clicked` 设置这行的点击监听。它自己不读写属性。

`switch` 绑定 `KMutableProperty0<Boolean>`，可选图标、标题、摘要。先在 `Dispatchers.IO` 读初值，再在主线程勾上开关并装点击。点击会翻转勾选，在 IO 写回属性，然后调用 `listener`。点击监听在初值读完之后才装上。

`selectableList` 建在 `clickable` 上，绑定一个属性、取值数组和字符串资源数组。摘要是当前下标对应的文案。点击后弹出模态 `ListPopupWindow`，选中一项就关掉弹层，在 IO 写入 `values[position]`，更新下标并调用 `listener`。初值同样先在 IO 读取，下标是 `values.indexOf` 的结果。

`editableText` 也建在 `clickable` 上，用 `NullableTextAdapter` 做转换。摘要：值为 `null` 时用 `placeholder`，空字符串用 `empty`，否则显示文本。点击打开 `requestModelTextInput`，并带上「重置」。确定且校验通过时返回输入；校验失败、取消或对话框关闭时返回打开前的文本；重置返回 `null`。返回值经 `adapter.to` 后在 IO 写回属性，再用 `adapter.from` 刷新摘要。取消也会走这次写回。

`editableTextList` 绑定 `List<T>?`，用 `TextAdapter`。摘要：`null` 用 `placeholder`，空列表用 `R.string.empty`，否则用 `R.string.format_elements` 和元素个数。点击打开全屏列表对话框。新增一项时再要一次文本输入，空白文本不加入。确定采用列表里的当前值，重置得到 `null`，取消或直接关掉得到打开前的列表。三种结果都会在 IO 写回属性。

`editableTextMap` 与列表相同，绑定的是 `Map<K, V>?`，键和值各有一个 `TextAdapter`。新增时对话框收集键和值，去掉空白后两者都非空才加入。确定把当前条目收成 `Map`，重置得到 `null`，取消得到打开前的映射，然后写回属性。

全屏列表对话框在 `requestEditableListOverlay`。新增按钮调用传入的挂起函数。重置以 `Reset` 结束，确定以 `Apply` 结束，取消按钮或尚未结束就被关掉时是 `Cancel`。协程取消时对话框关闭。

## 文本转换

`NullableTextAdapter.String` 和 `TextAdapter.String` 原样来回。

`NullableTextAdapter.Port`：`from` 在 `null` 时返回 `null`，大于 0 时返回十进制字符串，否则返回空字符串。`to` 在 `null` 时返回 `null`，否则 `toIntOrNull`，解析不了就是 0。
