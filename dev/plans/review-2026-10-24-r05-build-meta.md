---
title: "审查发现 r05 build-meta"
type: reference
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "5 条；最严重的是预发布在新 APK 上传前删除旧 Release，以及签名口令被写入日志"
---

# 审查发现 r05 build-meta

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。

范围只覆盖上面列出的构建与脚本路径。下面 5 条都来自这次读到的文件。

## r05-1
- 严重度：高
- 症状：每次预发布在新 APK 上传之前就删掉标签 `Prerelease-alpha` 及其 Release。后续打标签或上传失败时，上一份已发布的预发布包已经不在。
- 代码：
```71:92:.github/workflows/build-pre-release.yaml
      # Delete old Prerelease-alpha
      - uses: dev-drprasad/delete-tag-and-release@v1.1
        with:
          tag_name: Prerelease-alpha
          github_token: ${{ secrets.GITHUB_TOKEN }}
          delete_release: true

      - name: Tag Repo
        uses: richardsimko/update-tag@v3
        with:
          tag_name: Prerelease-alpha
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

      - name: Upload Alpha
        uses: softprops/action-gh-release@v3
        if: ${{  success() }}
        with:
          tag_name: Prerelease-alpha
          files: app/build/outputs/apk/alpha/release/*
          prerelease: true
          generate_release_notes: true
```
- 为什么是问题：`delete_release: true` 在构建成功后立刻执行，上传步骤还在它后面。删除和上传不是同一次成功提交。这个工作流还会在推送到 `main` 时自动跑，所以每次主干构建都会先拆掉当前的预发布资产。
- 方向：新 Release 上传成功之后再删旧标签和旧资产。

## r05-2
- 严重度：高
- 症状：正式发布和预发布把 keystore 口令、别名、key 口令写进 `signing.properties` 后，整文件打印到 Actions 日志；口令里的 `$` 或反引号还会在写入前被 shell 展开。
- 代码：
```92:104:.github/workflows/build-release.yaml
      - name: Signing properties
        env:
          SIGNING_STORE_PASSWORD: ${{ secrets.SIGNING_STORE_PASSWORD }}
          SIGNING_KEY_ALIAS: ${{ secrets.SIGNING_KEY_ALIAS }}
          SIGNING_KEY_PASSWORD: ${{ secrets.SIGNING_KEY_PASSWORD }}
        run: |
          touch signing.properties
          echo keystore.password="$SIGNING_STORE_PASSWORD" > signing.properties
          echo key.alias="$SIGNING_KEY_ALIAS" >> signing.properties
          echo key.password="$SIGNING_KEY_PASSWORD" >> signing.properties
            
          echo "cat signing.properties"
          cat signing.properties
```
- 为什么是问题：同一段在 `.github/workflows/build-pre-release.yaml` 第 53–65 行，而且预发布会跟着 `main` 的 push 跑。双引号里的 `$` 和反引号会展开，写进文件的口令已经偏离 secret；展开后的文本也对不上 Actions 的日志打码。`build.gradle.kts` 第 144–151 行用 `Properties.load` 读这个文件，`\` 会再被当成转义吃掉。
- 方向：用不会做 shell 展开的方式写入签名配置，并且不要把该文件打到日志。

## r05-3
- 严重度：中
- 症状：四个构建相关工作流在安装 Go 1.26 后套用全部 `.github/patch/*.patch`。其中 32 位时间补丁的上下文在 Go 1.26 的 `os_linux32.go` 里已经不存在，`patch` 失败后 Gradle 不会运行。
- 代码：
```14:31:.github/patch/remove_64bits_syscall_on_32bit_linux.patch
@@ -24,9 +24,6 @@ var use64bitsTimeOn32bits bool
 
 //go:nosplit
 func futex(addr unsafe.Pointer, op int32, val uint32, ts *timespec, addr2 unsafe.Pointer, val3 uint32) int32 {
-	if use64bitsTimeOn32bits {
-		return futex_time64(addr, op, val, ts, addr2, val3)
-	}
 	// Downgrade ts.
 	var ts32 timespec32
 	var pts32 *timespec32
@@ -45,9 +42,6 @@ func timer_settime64(timerid int32, flags int32, new, old *itimerspec) int32
 
 //go:nosplit
 func timer_settime(timerid int32, flags int32, new, old *itimerspec) int32 {
-	if use64bitsTimeOn32bits {
-		return timer_settime64(timerid, flags, new, old)
-	}
```
- 为什么是问题：`.github/workflows/build-debug.yaml` 第 33–38 行、`build-pre-release.yaml` 第 33–36 行、`build-release.yaml` 第 37–40 行、`update-dependencies.yaml` 第 30–33 行都是 `go-version: '1.26'`，然后 `patch -p 1` 套用该目录下每一个补丁。MetaCubeX/go 标签 `go1.26.0` 的 `src/runtime/os_linux32.go` 已改成 `isFutexTime32bitOnly` 探测失败再回退，没有 `use64bitsTimeOn32bits`。上下文对不上时 `patch` 退出码为 1，这一步失败，后面的组装不会执行。
- 方向：按 Go 1.26 的 `os_linux32.go` 更新或移除这个补丁，避免一个 32 位 hunk 失败拖垮整次构建。

## r05-4
- 严重度：中
- 症状：Debug 工作流打出来的是混淆过的 alpha release，签名落到 debug 证书，工件名却是 Debug Unsigned；armeabi-v7a、x86_64、x86 三条上传对不上实际产物。
- 代码：
```69:95:.github/workflows/build-debug.yaml
      - name: Build
        if: success()
        run: ./gradlew --no-daemon app:assembleAlphaRelease
          
      - name: Upload Aritfact (universal)
        uses: actions/upload-artifact@v7
        if: ${{  success() }}
        with:
          name: CMFA Debug Unsigned APK (universal) 
          path: |
            app/build/outputs/apk/alpha/release/*-universal-*.apk
      
      - name: Upload Aritfact (arm64-v8a)
        uses: actions/upload-artifact@v7
        if: ${{  success() }}
        with:
          name: CMFA Debug Unsigned APK (arm64-v8a)
          path: |
            app/build/outputs/apk/alpha/release/*-arm64-v8a-*.apk
      
      - name: Upload Aritfact (armeabi-v7a)
        uses: actions/upload-artifact@v7
        if: ${{  success() }}
        with:
          name: CMFA Debug Unsigned APK (armeabi-v7a)
          path: |
            app/build/outputs/apk/alpha/release/*-armeabi-v7a-*.apk
```
- 为什么是问题：`assembleAlphaRelease` 走的是 release。`build.gradle.kts` 第 157–160 行对 app 的 release 打开混淆和资源压缩，没有 release 签名配置时改用 debug 证书；第 166–168 行的 `.debug` 后缀只加在 debug 构建类型上。第 67–73 行和第 180–186 行只保留 `arm64-v8a`（另有 universal）。同文件第 97–111 行继续上传 x86_64 和 x86，这些路径没有对应 APK。
- 方向：让构建变体、签名和上传路径与实际要发布的 ABI、构建类型一致。

## r05-5
- 严重度：中
- 症状：正式发布在 APK 编译之前就把版本提交和 `v<version>` 标签推到远端。编译失败时，仓库里已经有这次版本号和标签，对应的 Release 包还没有上传。
- 代码：
```71:108:.github/workflows/build-release.yaml
      # If any change found, commit it and push
      - name: Commit and push if changes
        run: |
          changes=$(git diff --name-only origin/main | wc -l)
          if [ $changes -gt 0 ]
          then
            newVersionName=${{ steps.process-version.outputs.versonName }}
            newVersionCode=${{ steps.process-version.outputs.versonCode }}
            git config --global user.name 'GitHub Action'
            git config --global user.email 'action@github.com'
            git add build.gradle.kts
            git commit -am "Bump version to $newVersionName ($newVersionCode)"
            git tag "v$newVersionName"
            git push --follow-tags
          fi

      - name: Update CA
        run: |
          sudo apt-get update && sudo apt-get install ca-certificates
          sudo update-ca-certificates
          cp -f /etc/ssl/certs/ca-certificates.crt core/src/foss/golang/clash/component/ca/ca-certificates.crt

      - name: Signing properties
        env:
          SIGNING_STORE_PASSWORD: ${{ secrets.SIGNING_STORE_PASSWORD }}
          SIGNING_KEY_ALIAS: ${{ secrets.SIGNING_KEY_ALIAS }}
          SIGNING_KEY_PASSWORD: ${{ secrets.SIGNING_KEY_PASSWORD }}
        run: |
          touch signing.properties
          echo keystore.password="$SIGNING_STORE_PASSWORD" > signing.properties
          echo key.alias="$SIGNING_KEY_ALIAS" >> signing.properties
          echo key.password="$SIGNING_KEY_PASSWORD" >> signing.properties
            
          echo "cat signing.properties"
          cat signing.properties

      - name: Release Build
        if: success()
        run: ./gradlew --no-daemon app:assembleMetaRelease
```
- 为什么是问题：`git commit -am` 会提交工作区里所有已跟踪文件的改动，然后 `git push --follow-tags` 把提交和标签送上去。`assembleMetaRelease` 在第 108 行，上传在第 117–123 行。推送被拒绝或 `origin/main` 不存在时，`git diff` 或 `git push` 失败，同样到不了编译。
- 方向：先完成签名包再提交版本并推送标签。
