#!/bin/bash

# 设置 Java 21 并构建
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

echo "✅ 使用 Java 21："
java -version

echo ""
echo "🔨 开始构建 Alpha Release 版本..."
./gradlew app:assembleAlphaRelease

if [ $? -eq 0 ]; then
    echo ""
    echo "🎉 构建成功！"
    echo ""
    echo "📦 APK 文件位置："
    ls -lh app/build/outputs/apk/alpha/release/*.apk
    echo ""
    echo "📱 安装命令："
    echo "adb install -r app/build/outputs/apk/alpha/release/app-alpha-universal-release.apk"
else
    echo ""
    echo "❌ 构建失败！"
fi
