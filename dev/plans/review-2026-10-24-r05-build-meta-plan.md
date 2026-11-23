---
title: "审查方案 r05 build-meta"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "hold 3、drop 1、weaken 1"
---

# 审查方案 r05 build-meta

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。


hold 3、drop 1、weaken 1。

drop：r05-3。工作流安装的是 MetaCubeX/go 发布资产 `go1.26.linux-amd64.tar.gz`（`VERSION` 为 `go1.26.10`，资产更新于 2026-10-21）。`go-version: '1.26'` 配上下载基址 `https://github.com/MetaCubeX/go/releases/download/build` 时，版本列表地址返回 404，setup-go 按文件名直接下载这份包。其中 `src/runtime/os_linux32.go` 仍有 `use64bitsTimeOn32bits`。本机 GNU patch 2.7.6 对 `.github/patch/remove_64bits_syscall_on_32bit_linux.patch` 两个 hunk 都成功（偏移 30 行，第二处 fuzz 1），退出码 0，两个 `if use64bitsTimeOn32bits` 被删掉，函数其余部分还在。同目录的 `disable_pidfd_on_android.patch` 也对 `go1.26.10` 成功。标签 `go1.26.0` 的源文件用的是 `isFutexTime32bitOnly`，那不是这四个工作流安装的工具链。没有需要按这条落地的改动。

## r05-1

结论：hold。严重度仍为高。引用的 `.github/workflows/build-pre-release.yaml` 第 71–92 行仍是删除、打标签、再上传。

`on.push.branches` 仍包含 `main`（第 3–5 行），`workflow_dispatch` 也在。构建步骤是第 67–69 行的 `app:assembleAlphaRelease`。第 72–76 行 `dev-drprasad/delete-tag-and-release@v1.1` 使用 `tag_name: Prerelease-alpha` 和 `delete_release: true`，没有 `if`。第 79–83 行 `richardsimko/update-tag@v3` 把同一标签移到当前提交。第 85–92 行 `softprops/action-gh-release@v3` 才上传 `app/build/outputs/apk/alpha/release/*`。删除发生在上传之前。上传或移标签失败时，上一个 `Prerelease-alpha` Release 已经删掉。

问题类：预发布资产的发布顺序。

不变量：`Upload Alpha` 没有成功之前，已发布的 `Prerelease-alpha` Release 及其资产还在。新 APK 成为该标签上的已发布资产之后，旧资产才可以消失。

要动的符号或文件：只动 `.github/workflows/build-pre-release.yaml` 里这三步的相对顺序：`delete-tag-and-release`（现第 72–76 行）、`Tag Repo`（现第 79–83 行）、`Upload Alpha`（现第 85–92 行）。上传和删除用的是同一个标签名。上传成功后再删 `Prerelease-alpha`，会把刚传上的 Release 一起删掉。落点是让 `action-gh-release` 更新这个标签上已有 Release 的资产，上传前不运行 `delete-tag-and-release`。若必须去掉旧标签，先把新包发到另一个标签，确认上传成功，再删旧标签。

明确不改什么：不改 `main` 推送和 `workflow_dispatch` 触发，不改 `app:assembleAlphaRelease`，不改 `prerelease: true`，不改标签名 `Prerelease-alpha` 作为对外预发布标签，不改正式发布工作流。

本地验证：`scripts/`、`gradle/`、`.github/` 里没有这项测试。应新增的断言意图：`build-pre-release.yaml` 里，`delete-tag-and-release` 不出现在上传 `app/build/outputs/apk/alpha/release/*` 的 `softprops/action-gh-release` 之前；若删除步骤仍在，它的标签不是刚上传成功的那个标签，并且只在上传步骤成功之后运行。

## r05-2

结论：weaken。严重度由高降为中。`.github/workflows/build-release.yaml` 第 92–104 行、`.github/workflows/build-pre-release.yaml` 第 53–65 行、根 `build.gradle.kts` 第 144–151 行仍是发现稿引用的那段行为。

仍成立的部分：两个工作流都把 `SIGNING_STORE_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD` 写入 `signing.properties`，然后执行 `cat signing.properties`。根 `build.gradle.kts` 第 144–146 行用 `Properties.load` 读取，第 149–151 行把 `keystore.password`、`key.alias`、`key.password` 交给 `storePassword`、`keyAlias`、`keyPassword`。`Properties.load` 会处理反斜杠转义。本地用 JDK 21 对同一规则读入 `a\b\n\u0041`，得到的是 `a`、`b`、换行、`A` 四个字符；`\x41` 变成 `x41`。口令里的 `\` 进到 Gradle 时已经不是 secret 原文。

反证：发现稿说双引号里的 `$` 和反引号会在写入前被 shell 展开，并因此对不上 Actions 打码。当前脚本是 `echo keystore.password="$SIGNING_STORE_PASSWORD"`，secret 经 `env:` 进入环境变量。本机 `bash -eo pipefail` 下，值为 `p$NOT_A_VAR`、反引号命令和 `\x41` 时，文件里这些字符原样保留，反引号命令没有执行。`$` 和反引号再经 `Properties.load` 也保持原样。展开导致口令偏离 secret、以及展开后打码失效，这两点不成立。仓库里没有关闭日志打码的配置，也没有一份 Actions 日志证明 `cat` 之后口令以明文留下。

问题类：签名口令进入 `signing.properties` 后的保真，以及该文件被打进步骤日志。

不变量：`signingConfigs.release` 读到的 `storePassword`、`keyAlias`、`keyPassword` 与三个 secret 的原文相同，包括 `$`、反引号和 `\`。`signing.properties` 的内容不进入步骤日志。

要动的符号或文件：`.github/workflows/build-release.yaml` 与 `.github/workflows/build-pre-release.yaml` 的步骤 `Signing properties`。去掉两处 `echo "cat signing.properties"` 和 `cat signing.properties`。写入方式要让现有 `Properties.load` 读回原文：按 `java.util.Properties` 的转义规则写出 `\`、换行和 `\u`，或改用 `Properties.store` 的等价输出。键名保持 `keystore.password`、`key.alias`、`key.password`。根 `build.gradle.kts` 的 `load` 可以不动；只有写入侧无法完成往返时才改第 144–151 行的读取。

明确不改什么：不改 `release.keystore` 路径，不改这三个键名，不改 `assembleMetaRelease` / `assembleAlphaRelease`，不启用 `.github/workflows/build-debug.yaml` 第 55–67 行已注释的签名步骤。

本地验证：`scripts/`、`gradle/`、`.github/` 里没有这项测试。应新增的断言意图：用含 `$`、反引号、单个 `\`、以及反斜杠加 `n` 这两个字符的夹具生成 `signing.properties`，再经 `Properties.load`，三个属性与夹具原文相等。静态检查两处 `Signing properties` 步骤不再包含 `cat signing.properties`。

## r05-4

结论：hold。严重度仍为中。`.github/workflows/build-debug.yaml` 第 69–95 行仍是 `app:assembleAlphaRelease`，以及 universal、arm64-v8a、armeabi-v7a 三份名为 `CMFA Debug Unsigned APK` 的上传。第 97–111 行仍上传 x86_64 和 x86。

根 `build.gradle.kts` 第 30 行 `isApp = name == "app"`。第 157–160 行对 app 的 release 打开 `isMinifyEnabled`、`isShrinkResources`，并把 `signingConfig` 设为 `signingConfigs.findByName("release") ?: signingConfigs["debug"]`。第 166–168 行的 `versionNameSuffix = ".debug"` 只在 `debug` 构建类型上。第 67–69 行 `ndk.abiFilters` 和第 73 行 `cmake.abiFilters` 只保留 `arm64-v8a`，`armeabi-v7a`、`x86`、`x86_64` 在注释里。第 180–186 行 `splits.abi` 打开 universal，`include` 只有 `arm64-v8a`。Debug 工作流第 55–67 行的签名步骤整段注释，这次构建不会创建 `signing.properties`，release 签名配置不存在，第 160 行会使用 debug 证书。工件名中的 Debug、Unsigned 与这次任务不符。armeabi-v7a、x86_64、x86 的 glob 没有对应产物。`actions/upload-artifact@v7` 的 `if-no-files-found` 默认是 `warn`，缺文件本身不会让该步骤失败；对不上的是名称和实际上传内容。

问题类：Debug 工作流的构建变体、签名描述和上传路径不一致。

不变量：`build-debug.yaml` 每个 `upload-artifact` 的 `path` 都能对上 `app:assembleAlphaRelease` 在当前 ABI 配置下的产物，也就是 `arm64-v8a` 与 universal。工件名与 alpha release、以及 debug 证书签名一致。

要动的符号或文件：只动 `.github/workflows/build-debug.yaml` 的 `Build`（现第 69–71 行）所对应的上传步骤（现第 73–111 行）。保留 universal 与 `*-arm64-v8a-*.apk`。去掉 armeabi-v7a、x86_64、x86 三个上传。工件名去掉 `Debug Unsigned`，改成能看出 alpha release 且使用 debug 证书的名字。

明确不改什么：不改根 `build.gradle.kts` 的 `abiFilters`、`splits.include("arm64-v8a")`、`isMinifyEnabled`、`isShrinkResources`，不把任务改成 `assembleAlphaDebug`，不把输出目录改到 `apk/alpha/debug`，不取消第 55–67 行签名步骤的注释，不把 release 口令引进这个工作流。

本地验证：`scripts/`、`gradle/`、`.github/` 里没有这项测试。应新增的断言意图：`build-debug.yaml` 上传路径里的 ABI 只有 `arm64-v8a` 和 `universal`，与 `splits.abi.include("arm64-v8a")` 加上 universal 相同，并且不包含仍处于注释中的 `armeabi-v7a`、`x86`、`x86_64`。可选的本地构建核对：在没有 `signing.properties` 时运行 `./gradlew :app:assembleAlphaRelease`，`app/build/outputs/apk/alpha/release/` 里的 apk 能被保留的 glob 匹配，不能被删掉的三条 glob 匹配。

## r05-5

结论：hold。严重度仍为中。`.github/workflows/build-release.yaml` 第 71–108 行仍是先提交并推送，再更新 CA、写签名、最后 `app:assembleMetaRelease`。上传仍在第 117–123 行。

第 71–84 行在 `changes` 大于 0 时执行 `git add build.gradle.kts`、`git commit -am`、`git tag "v$newVersionName"`、`git push --follow-tags`。`-am` 会把当时工作区里已跟踪文件的修改一并提交。这一步在第 106–108 行的 `assembleMetaRelease` 之前。第 110–115 行的 `richardsimko/update-tag@v3` 还在第 117–123 行的 `Upload Release` 之前。编译或上传失败时，版本提交和 `v<version>` 标签可以已经在远端，Release 资产还没有。`git diff --name-only origin/main` 或 `git push` 失败时，同一步骤失败，后面的编译不会跑。

问题类：正式版本提交和标签的推送顺序。

不变量：`assembleMetaRelease` 未成功，或 `Upload Release` 未成功时，远端没有这次新的版本提交，也没有新的 `v<version>` 标签。编译不依赖 `git diff origin/main` 或 `git push` 成功。

要动的符号或文件：`.github/workflows/build-release.yaml` 的 `Commit and push if changes`（现第 71–84 行）、`Tag Repo`（现第 110–115 行）、`Upload Release`（现第 117–123 行）。本地改写版本之后先编译并上传；上传成功后再提交并推送。`git commit` 去掉 `-a`，只提交 `build.gradle.kts`。`git tag "v$newVersionName"` 和 `update-tag` 的远端生效放在上传成功之后。

明确不改什么：不改 `process-version` 的版本号算法，也不改输出名 `versonName`、`versonCode`。不改 `assembleMetaRelease`，不改 flavor `meta`，不改 CA 证书复制（现第 86–90 行）的内容。签名文件的写入归 r05-2，不在这一条里改键名或 `cat`。

本地验证：`scripts/`、`gradle/`、`.github/` 里没有这项测试。应新增的断言意图：`build-release.yaml` 中 `git push` 和 `richardsimko/update-tag` 都出现在 `./gradlew --no-daemon app:assembleMetaRelease` 与上传 `app/build/outputs/apk/meta/release/*` 的步骤之后；版本提交命令不带 `-a`，提交路径只有 `build.gradle.kts`。
