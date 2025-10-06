#!/usr/bin/env python3

import sys
import re
import os
import argparse

from google.protobuf.compiler import plugin_pb2 as plugin

def process_java_file(content):
    """处理单个 Java 文件的内容，去掉 repeated 字段方法名中的 List 后缀"""

    # 替换 getter 方法名中的 "List" 后缀
    # 例如: getRequestsList() -> getRequests()
    content = re.sub(
        r'public\s+java\.util\.List<[^>]+>\s+get(\w+)List\(\)',
        r'public java.util.List<\1> get\2()',
        content
    )

    # 替换 setter 方法名中的 "List" 后缀
    # 例如: setRequestsList(List<HelloRequest> value) -> setRequests(List<HelloRequest> value)
    content = re.sub(
        r'public\s+Builder\s+set(\w+)List\(',
        r'public Builder set\1(',
        content
    )

    # 替换 add 方法名中的 "List" 后缀
    # 例如: addRequestsList(HelloRequest value) -> addRequests(HelloRequest value)
    content = re.sub(
        r'public\s+Builder\s+add(\w+)List\(',
        r'public Builder add\1(',
        content
    )

    # 替换 addAll 方法名中的 "List" 后缀
    # 例如: addAllRequestsList(Iterable<? extends HelloRequest> values) -> addAllRequests(Iterable<? extends HelloRequest> values)
    content = re.sub(
        r'public\s+Builder\s+addAll(\w+)List\(',
        r'public Builder addAll\1(',
        content
    )

    # 替换 clear 方法名中的 "List" 后缀
    # 例如: clearRequestsList() -> clearRequests()
    content = re.sub(
        r'public\s+Builder\s+clear(\w+)List\(\)',
        r'public Builder clear\1()',
        content
    )

    return content

def process_code(request, response):
    """处理代码生成请求"""

    # 遍历所有要生成的文件
    for response_file in response.file:
        # 只处理 Java 文件
        if not response_file.name.endswith('.java'):
            continue

        # 处理文件内容
        response_file.content = process_java_file(response_file.content)

def process_file(input_file, output_file):
    """直接处理单个文件"""
    with open(input_file, 'r') as f:
        content = f.read()

    # 处理文件内容
    content = process_java_file(content)

    # 写入输出文件
    with open(output_file, 'w') as f:
        f.write(content)

def main_as_plugin():
    """作为 protoc 插件运行"""
    # 读取请求
    data = sys.stdin.buffer.read()

    # 解析请求
    request = plugin.CodeGeneratorRequest()
    request.ParseFromString(data)

    # 创建响应
    response = plugin.CodeGeneratorResponse()

    # 处理代码
    process_code(request, response)

    # 输出响应
    output = response.SerializeToString()
    sys.stdout.buffer.write(output)

def main_as_file_processor(input_dir, output_dir):
    """作为文件处理器运行"""
    # 确保输出目录存在
    os.makedirs(output_dir, exist_ok=True)

    # 处理所有 Java 文件
    for root, _, files in os.walk(input_dir):
        for file in files:
            if file.endswith('.java'):
                input_file = os.path.join(root, file)
                # 计算相对路径
                rel_path = os.path.relpath(input_file, input_dir)
                output_file = os.path.join(output_dir, rel_path)

                # 确保输出目录存在
                os.makedirs(os.path.dirname(output_file), exist_ok=True)

                # 处理文件
                process_file(input_file, output_file)
                print(f"Processed: {input_file} -> {output_file}")

def main():
    """主函数"""
    parser = argparse.ArgumentParser(description='Remove List suffix from repeated field methods.')
    parser.add_argument('--mode', choices=['plugin', 'file'], default='plugin',
                        help='运行模式: plugin (作为 protoc 插件) 或 file (直接处理文件)')
    parser.add_argument('--input-dir', help='输入目录 (仅在 file 模式下需要)')
    parser.add_argument('--output-dir', help='输出目录 (仅在 file 模式下需要)')
    parser.add_argument('--debug', action='store_true', help='启用调试模式')
    args = parser.parse_args()

    if args.mode == 'plugin':
        main_as_plugin()
    elif args.mode == 'file':
        if not args.input_dir or not args.output_dir:
            print("错误: file 模式需要指定 --input-dir 和 --output-dir")
            sys.exit(1)
        main_as_file_processor(args.input_dir, args.output_dir)

if __name__ == '__main__':
    main()
