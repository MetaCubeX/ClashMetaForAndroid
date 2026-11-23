---
title: "列表适配器"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-24
summary: "adapter 包只留下两个列表编辑适配器。屏幕列表和短选项选择改由 Compose 绘制"
---

# 列表适配器

包路径是 `design/.../adapter/`。屏幕上的列表由 `ios` 包的 Compose 绘制，不再经过这里。应用集合的点选见 [访问控制](../../docs/systems/access-control/INDEX.md)。弹层里的确定、重置和取消见 [设置项控件](preferences.md)。

`EditableTextListAdapter` 给 `editableTextList` 的全屏列表。它绑定 `MutableList<T>`，经 `TextAdapter` 显示，删除按钮移走该项。

`EditableTextMapAdapter` 给 `editableTextMap`。它绑定 `MutableList<Pair<K, V>>`，键和值各有一个 `TextAdapter`。

两个适配器都是 `RecyclerView.Adapter`。短选项选择的 `PopupListAdapter` 随 `selectableList` 一起删除，现在由 Compose 的 `IosPopupMenu` 负责。
