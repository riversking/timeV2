#!/usr/bin/env python3

import sys
import re

from google.protobuf.compiler import plugin_pb2 as plugin

def process_java_file(content):
    """处理单个 Java 文件的内容，去掉 repeated 字段方法名中的 List 后缀"""

    # 替换 getter 方法名中的 "List" 后缀
    # 例如: getRequestsList() -> getRequests()
    content = re.sub(
        r'public\s+([\w.]+)\s+get(\w+)List\(\)',
        r'public \1 get\2()',
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

def main():
    """主函数"""

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

if __name__ == '__main__':
    main()
