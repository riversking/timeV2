#!/bin/bash

# 检查是否提供了 proto 文件参数
if [ $# -eq 0 ]; then
    echo "用法: $0 <proto文件路径>"
    echo "示例: $0 /path/to/your/example.proto"
    exit 1
fi

# 获取 proto 文件路径
PROTO_FILE_PATH="$1"

# 检查 proto 文件是否存在
if [ ! -f "${PROTO_FILE_PATH}" ]; then
    echo "错误: 文件 ${PROTO_FILE_PATH} 不存在。"
    exit 1
fi

# 获取 proto 文件名（不含路径和扩展名）
PROTO_FILE=$(basename "${PROTO_FILE_PATH}" .proto)

# 设置项目目录
PROJECT_DIR="$(pwd)"
BUILD_DIR="${PROJECT_DIR}/build"
CLASSES_DIR="${BUILD_DIR}/classes"
LIB_DIR="${BUILD_DIR}/lib"
PROTO_DIR="${PROJECT_DIR}/src/main/proto"

# 创建必要的目录
mkdir -p "${CLASSES_DIR}"
mkdir -p "${LIB_DIR}"
mkdir -p "${PROTO_DIR}"

# 复制 proto 文件到项目目录
cp "${PROTO_FILE_PATH}" "${PROTO_DIR}/"

# 检查 protoc 是否安装
if ! command -v protoc &> /dev/null; then
    echo "错误: protoc 未安装。请先安装 Protobuf 编译器。"
    exit 1
fi

# 检查 Java 编译器是否安装
if ! command -v javac &> /dev/null; then
    echo "错误: javac 未安装。请先安装 JDK。"
    exit 1
fi

# 检查 Protobuf Java 库是否存在
PROTOBUF_VERSION="3.21.7"
PROTOBUF_JAR="${LIB_DIR}/protobuf-java-${PROTOBUF_VERSION}.jar"
if [ ! -f "${PROTOBUF_JAR}" ]; then
    echo "下载 Protobuf Java 库..."
    wget -O "${PROTOBUF_JAR}" "https://repo1.maven.org/maven2/com/google/protobuf/protobuf-java/${PROTOBUF_VERSION}/protobuf-java-${PROTOBUF_VERSION}.jar"
    if [ $? -ne 0 ]; then
        echo "错误: 下载 Protobuf Java 库失败。"
        exit 1
    fi
fi

# 编译 .proto 文件
echo "编译 .proto 文件..."
protoc --java_out="${CLASSES_DIR}" "${PROTO_DIR}/${PROTO_FILE}.proto"
if [ $? -ne 0 ]; then
    echo "错误: .proto 文件编译失败。"
    exit 1
fi

# 编译 Java 文件
echo "编译 Java 文件..."
javac -cp "${PROTOBUF_JAR}" -d "${CLASSES_DIR}" $(find "${CLASSES_DIR}" -name "*.java")
if [ $? -ne 0 ]; then
    echo "错误: Java 文件编译失败。"
    exit 1
fi

# 创建 Manifest 文件
echo "Class-Path: lib/protobuf-java-${PROTOBUF_VERSION}.jar" > "${BUILD_DIR}/MANIFEST.MF"

# 创建 JAR 包
echo "创建 JAR 包..."
jar cfm "${BUILD_DIR}/${PROTO_FILE}.jar" "${BUILD_DIR}/MANIFEST.MF" -C "${CLASSES_DIR}" .
if [ $? -ne 0 ]; then
    echo "错误: JAR 包创建失败。"
    exit 1
fi

echo "构建完成！JAR 包已创建: ${BUILD_DIR}/${PROTO_FILE}.jar"
