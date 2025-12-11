#!/bin/bash

echo "🔍 检查 CMFA Tasker 版本构建环境"
echo "=================================="
echo ""

# 检查 Java
echo "1️⃣  检查 Java 版本..."
if [ -d "/opt/homebrew/opt/openjdk@21" ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
    JAVA_VER=$($JAVA_HOME/bin/java -version 2>&1 | head -1 | cut -d'"' -f2)
    if [[ $JAVA_VER == 21* ]]; then
        echo "   ✅ Java 21 已安装: $JAVA_VER"
    else
        echo "   ❌ 需要 Java 21，当前: $JAVA_VER"
        exit 1
    fi
else
    echo "   ❌ Java 21 未安装"
    echo "   安装命令: brew install openjdk@21"
    exit 1
fi

# 检查 Golang
echo ""
echo "2️⃣  检查 Golang..."
if command -v go &> /dev/null; then
    GO_VER=$(go version | awk '{print $3}')
    echo "   ✅ Golang 已安装: $GO_VER"
else
    echo "   ❌ Golang 未安装"
    echo "   安装命令: brew install go"
    exit 1
fi

# 检查 NDK
echo ""
echo "3️⃣  检查 Android NDK..."
if [ -z "$ANDROID_HOME" ]; then
    echo "   ❌ ANDROID_HOME 环境变量未设置"
    exit 1
fi

NDK_VERSION="27.2.12479018"
if [ -d "$ANDROID_HOME/ndk/$NDK_VERSION" ]; then
    echo "   ✅ NDK $NDK_VERSION 已安装"
else
    echo "   ❌ NDK $NDK_VERSION 未安装"
    echo "   请使用 Android Studio SDK Manager 安装"
    exit 1
fi

# 检查 Git 子模块
echo ""
echo "4️⃣  检查 Git 子模块..."
if [ -f "core/src/foss/golang/clash/go.mod" ]; then
    echo "   ✅ Git 子模块已初始化"
else
    echo "   ❌ Git 子模块未初始化"
    echo "   运行命令: git submodule update --init --recursive"
    exit 1
fi

# 检查配置文件
echo ""
echo "5️⃣  检查项目配置..."
if [ -f "local.properties" ]; then
    echo "   ✅ local.properties 已配置"
else
    echo "   ⚠️  local.properties 不存在（使用默认配置）"
fi

if [ -f "signing.properties" ]; then
    echo "   ✅ signing.properties 已配置"
else
    echo "   ⚠️  signing.properties 不存在（将使用 debug 签名）"
fi

echo ""
echo "=================================="
echo "✅ 所有环境检查通过！"
echo ""
echo "🚀 可以开始构建："
echo "   ./build.sh"
echo ""
echo "   或者手动构建："
echo "   export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
echo "   ./gradlew app:assembleAlphaRelease"
