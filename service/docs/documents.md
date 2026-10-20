---
title: "文件提供者"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-17
summary: "后台进程的 FilesProvider 把已导入配置的 config.yaml 和 providers 暴露成文档树。providers 始终是目录。"
---

# 文件提供者

`FilesProvider` 继承 `DocumentsProvider`，跑在 `:background`。树里只有已导入配置的 `config.yaml` 和 `providers/`。不列日志，也不列 `filesDir/clash`。Room 表和目录归属见 [配置库与 ServiceStore](profiles-store.md)。组件放在哪个进程见 [进程与远程接口](process.md)。

## 清单

`service/src/main/AndroidManifest.xml` 里这个 Provider：

| 属性 | 值 |
|:-----|:---|
| `android:name` | `.FilesProvider` |
| `android:authorities` | `${applicationId}.files` |
| `android:process` | `:background` |
| `android:exported` | `true` |
| `android:grantUriPermissions` | `true` |
| `android:permission` | `android.permission.MANAGE_DOCUMENTS` |

intent-filter 的动作是 `android.content.action.DOCUMENTS_PROVIDER`。

界面用的 authority 是 `Authorities.FILES_PROVIDER`，值为 `"$packageName.files"`。它和清单里的 `${applicationId}.files` 是同一条。

## 旁边的类

`document/` 供 `FilesProvider` 使用。

| 类 | 作用 |
|:---|:-----|
| `Document` | `id`、`name`、`mimeType`、`size`、`updatedAt`、`flags` |
| `FileDocument` | 磁盘上的文件或目录。目录的 MIME 是 `MIME_TYPE_DIR`，否则是 `text/plain`。`size` 与 `updatedAt` 来自该文件 |
| `VirtualDocument` | 不指向单个文件的节点 |
| `Flag` | `Writable`、`Deletable`、`Virtual` |
| `Path` | `uuid`、`scope`、`relative`。`scope` 只有 `Configuration` 和 `Providers` |
| `Paths` | `CONFIGURATION_ID` 为 `config.yaml`，`PROVIDERS_ID` 为 `providers`。`resolve` 把字符串收成 `Path` |
| `Picker` | `list` 与 `pick`。可写打开前先克隆到 `pending` |

`onCreate` 只返回 true。`Picker` 第一次用到时才拿 Provider 的 `context` 创建。

## 路径怎么拆

`Paths.resolve` 按 `/` 切开，丢掉空段、`.` 和 `..`。

| 段数 | 结果 |
|:-----|:-----|
| 0 | 根。三个字段都是 null。`toString()` 为 `/` |
| 1 | 一个 UUID。`UUID.fromString`，格式不对会抛 |
| 2 | 只能是 `config.yaml` 或 `providers`。别的第二段抛 `IllegalArgumentException("unknown scope ...")` |
| 更多 | 第二段仍只能是上面两个。其余进 `relative` |

`scope` 为 `Configuration` 且 `relative` 非空时，`pick` 抛 `FileNotFoundException("invalid path")`。`config.yaml` 下面没有子路径。

落盘只看 Provider `context` 的 `filesDir`，也就是应用自己的文件目录：

- 有 `pending` 行：`pending/<uuid>/`
- 否则有 `imported` 行：`imported/<uuid>/`
- 两行都没有：`FileNotFoundException("profile not found")`

`processing/` 不在这棵树里。谁写入 `imported` 和 `pending` 见 [配置库与 ServiceStore](profiles-store.md)。

## 列出来的节点

根是 `VirtualDocument`。名字用 `R.string.clash_meta_for_android`，MIME 为目录，`size` 和 `updatedAt` 为 0，标志只有 `Virtual`。`list` 只遍历 `ImportedDao().queryAllUUIDs()`。仅存在于 `pending` 的 UUID 不会出现在根上。直接 `pick` 一个已知 UUID 时，仍可以读到它的 `pending` 行。

UUID 节点也是 `VirtualDocument`。`id` 是 UUID 字符串。显示名先取 `pending.name`，没有再取 `imported.name`。标志只有 `Virtual`。它的两个子项是拼出来的，不扫描磁盘：

- `config.yaml`：`FileDocument`，文件为该目录下的 `config.yaml`。`id` 固定为 `config.yaml`，显示名是 `R.string.configuration_yaml`。
- `providers`：`FileDocument`，指向该目录下的 `providers`。这一层还不是目录时先 `mkdirs()`。`id` 固定为 `providers`，显示名是 `R.string.provider_files`，标志只有 `Virtual`。目录本身，以及带 `Virtual` 的这一层，MIME 都是目录。因此还没建成目录时，界面仍把它当目录打开，而不是按文本交给系统查看器。

`config.yaml` 的标志看类型。`Url` 为空。`File` 和 `External` 为 `Writable`。可写 `pick` 时类型不是 `File`，抛 `IllegalArgumentException("invalid open mode")`。`External` 的游标因此带可写标志，按写打开仍会失败。

`providers` 下每一层的文件和子目录都是 `FileDocument`，标志为 `Writable` 与 `Deletable`。只调用当前目录的 `list()`，不递归。根上的 `providers` 在列出之前已经创建，缺目录时得到的是空目录。类型是 `Url` 也不挡住这一层的可写打开。

可写 `pick` 会先 `cloneToPending`。已有 `pending` 行就返回。否则必须已有 `imported` 行：插入一行 `Pending`，`upload`、`download`、`total`、`expire` 写 0，`createdAt` 用构造默认值，其余列从已导入行复制。然后删除 `pending/<uuid>`，再把 `imported/<uuid>` 整目录复制过去。列定义见 [配置库与 ServiceStore](profiles-store.md)。

对 UUID 节点做可写 `pick` 也会先克隆，随后抛 `invalid open mode`。

## 游标里的标志

`applyDocument` 把 `Flag` 合成 `COLUMN_FLAGS`：

| `Flag` | 写入 |
|:-------|:-----|
| `Writable` | `FLAG_SUPPORTS_WRITE` |
| `Deletable` | `FLAG_SUPPORTS_DELETE` |
| `Virtual` | API 24 及以上为 `FLAG_VIRTUAL_DOCUMENT`，否则为 0 |

没有写入 `FLAG_SUPPORTS_RENAME`。`renameDocument` 仍然被重写。

## 查询、打开、删除

`queryRoots` 只返回一行。根 id 是 `"0"`。标志是 `FLAG_LOCAL_ONLY` 与 `FLAG_SUPPORTS_IS_CHILD` 的按位或。图标是 `R.drawable.ic_logo_service`。标题是 `R.string.clash_meta_for_android`，摘要是 `R.string.profiles_and_providers`。文档 id 是 `/`。默认列是 `ROOT_ID`、`FLAGS`、`ICON`、`TITLE`、`SUMMARY`、`DOCUMENT_ID`。代码还 `add` 了 `MIME_TYPES`，默认投影里没有这一列。

`queryDocument` 用 `pick(path, false)`。成功时一行。`COLUMN_DOCUMENT_ID` 用调用方传入的 id；传入 null 时写成 `/`。不是 `Path.toString()`。任何异常都返回空游标。

`queryChildDocuments` 用 `picker.list`。子 id 是 `"$parentDocumentId/${document.id}"`。父 id 为 `/` 时，子 id 会变成 `//<uuid>`。`resolve` 丢掉空段，仍能对上该 UUID。`sortOrder` 未使用。异常同样变成空游标。

默认文档列是 `DOCUMENT_ID`、`DISPLAY_NAME`、`MIME_TYPE`、`LAST_MODIFIED`、`SIZE`、`FLAGS`。调用方传了投影就用那一组。

`openDocument` 先 `ParcelFileDescriptor.parseMode(mode)`。`mode` 含 `w`（忽略大小写）则按可写 `pick`，否则按只读。结果必须是 `FileDocument`，否则抛 `FileNotFoundException("invalid path ...")`。接着 `ParcelFileDescriptor.open`。`CancellationSignal` 未使用。根和 UUID 是 `VirtualDocument`，打不开。`providers` 目录本身是 `FileDocument`。

没有重写 `createDocument`。新内容不经过创建接口。

`deleteDocument` 在 `relative == null` 时抛 `IllegalArgumentException("invalid path ...")`。根、UUID、`config.yaml` 和 `providers` 目录本身因此删不掉。更深的路径按可写 `pick`，确认是 `FileDocument` 后 `deleteRecursively()`。

`renameDocument` 要求新名字匹配 `PatternFileName`，即 `[^*&%\n\r/]+`，否则抛 `IllegalArgumentException("invalid name ...")`。空名字不匹配。`.` 和 `..` 匹配，这里没有再拒绝。`relative == null` 抛 `unable to rename`。然后可写 `pick`，必须是 `FileDocument` 且父目录非空，再 `renameTo(parent.resolve(name))`。返回的新 id 是改过最后一段 `relative` 的 `Path.toString()`。`renameTo` 的返回值没有检查。

`isChildDocument` 在任一 id 为 null 时返回 false。否则只判断 `documentId.startsWith(parentDocumentId)`。

会调用 `Picker` 的方法都包在 `runBlocking` 里。`queryRoots` 和 `isChildDocument` 不包。

## 界面进程怎么拿

界面在默认进程。它不靠 `RemoteService` 列这些文件。`app` 的 `FilesClient` 用 `DocumentsContract` 访问 `Authorities.FILES_PROVIDER`。只有 `FilesActivity` 创建它。方向如下。

| `FilesClient` | 落到 Provider |
|:--------------|:--------------|
| `list` | `queryChildDocuments`。URI 由 `buildChildDocumentsUri` 生成 |
| `renameDocument` | `DocumentsContract.renameDocument` |
| `deleteDocument` | `DocumentsContract.deleteDocument` |
| `importDocument` 和两个 `copyDocument` | `copyContentTo`。写入是 `openOutputStream(target, "rwt")`，也就是可写的 `openDocument` |

`importDocument` 的目标 id 是 `"$parentDocumentId/$name"`。`buildDocumentUri` 用同一个 authority。

`FilesActivity` 从配置 UUID 起列，不从文档根 `/` 起列。栈、空的 `config.yaml` 只留一项，以及 URL 类型在界面上不可改，都是界面自己的逻辑。调用细节见 [远程调用](../../app/docs/remote.md)，屏幕行为见 [Activity](../../app/docs/activities.md)。
