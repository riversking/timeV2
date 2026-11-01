#!/usr/bin/env python3
"""修复 MySQL 表/字段注释的 UTF-8 -> latin1 二次编码乱码。

成因：用 mysql CLI 执行含中文的 DDL 时未加 --default-character-set=utf8mb4，
中文按 latin1(=cp1252) 二次编码写入，Navicat 里注释显示为 "ç¯å..."。
注释原文是无损可逆的，本脚本按 cp1252 -> UTF-8 逆变换还原。

用法（在 timeV2 目录下）：
    python3 sql/fix_mojibake_comments.py                 # 只列出可疑注释（默认 dry-run）
    python3 sql/fix_mojibake_comments.py --print-only    # 额外打印将执行的 ALTER 语句
    python3 sql/fix_mojibake_comments.py --apply         # 执行修复
    python3 sql/fix_mojibake_comments.py --apply --schemas timer_nba,timer_moment
    python3 sql/fix_mojibake_comments.py --verify        # 回读全部注释，确认是否还有乱码

密码优先级：环境变量 MYSQL_PWD > 交互输入（不回显），不会写入任何文件。
"""

from __future__ import annotations

import argparse
import getpass
import os
import re
import subprocess
import sys

SYSTEM_SCHEMAS = ("information_schema", "mysql", "performance_schema", "sys")
# 中文 UTF-8 首字节落在 E4-E9，经 latin1 误读后正好是 ä å æ ç è é（UTF-8 编码 C3A4-C3A9）
MOJIBAKE_HINT = "C3A[4-9]"
NUMERIC_TYPES = {
    "tinyint", "smallint", "mediumint", "int", "integer", "bigint",
    "decimal", "numeric", "float", "double", "real", "year",
}
COLUMNS_SQL = """
SELECT table_schema, table_name, column_name, column_comment, column_type,
       is_nullable, (column_default IS NULL) AS default_is_null, column_default,
       IFNULL(extra, '') AS extra, IFNULL(character_set_name, '') AS charset,
       IFNULL(collation_name, '') AS collation_name,
       IFNULL(generation_expression, '') AS generation_expression, data_type
FROM information_schema.columns
WHERE table_schema {schema_filter}
  AND column_comment <> ''
  {hint_filter}
ORDER BY table_schema, table_name, ordinal_position;
"""
TABLES_SQL = """
SELECT table_schema, table_name, table_comment
FROM information_schema.tables
WHERE table_schema {schema_filter}
  AND table_type = 'BASE TABLE'
  AND table_comment <> ''
  {hint_filter}
ORDER BY table_schema, table_name;
"""


def q_ident(name: str) -> str:
    return "`" + name.replace("`", "``") + "`"


def q_str(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


# MySQL 的 latin1 实为 cp1252：0x80-0x9F 中已定义的字节会被读成 € ” – 等字符，
# 未定义的字节（0x81/0x8D/0x8F/0x90/0x9D）则保留为同名控制字符。
CP1252_REVERSE: dict[str, int] = {}
for _byte in range(0x80, 0xA0):
    try:
        CP1252_REVERSE[bytes([_byte]).decode("cp1252")] = _byte
    except UnicodeDecodeError:
        pass


def mojibake_bytes(text: str) -> bytes | None:
    out = bytearray()
    for ch in text:
        code = ord(ch)
        if code < 0x100:
            out.append(code)
        elif ch in CP1252_REVERSE:
            out.append(CP1252_REVERSE[ch])
        else:
            return None
    return bytes(out)


def repair_text(mojibake: str) -> str | None:
    """cp1252 误读的字符串还原为 UTF-8；不可还原（非该乱码模式）时返回 None。"""
    if not mojibake:
        return None
    raw = mojibake_bytes(mojibake)
    if raw is None:
        return None
    try:
        fixed = raw.decode("utf-8")
    except UnicodeDecodeError:
        return None
    return fixed if fixed != mojibake else None


class MySql:
    def __init__(self, host: str, port: str, user: str, password: str):
        self.base = [
            "mysql", "--protocol=TCP", "-h", host, "-P", port, "-u", user,
            "--default-character-set=utf8mb4", "--connect-timeout=5",
            "-N", "-B", "--raw",
        ]
        self.env = {**os.environ, "MYSQL_PWD": password}

    def query(self, sql: str) -> list[list[str]]:
        proc = subprocess.run(self.base, input=sql, capture_output=True,
                              text=True, env=self.env, check=False)
        if proc.returncode != 0:
            raise RuntimeError(proc.stderr.strip() or "mysql 执行失败")
        return [line.split("\t") for line in proc.stdout.splitlines() if line]

    def execute(self, sql: str) -> None:
        proc = subprocess.run(self.base, input=sql, capture_output=True,
                              text=True, env=self.env, check=False)
        if proc.returncode != 0:
            raise RuntimeError(proc.stderr.strip() or "mysql 执行失败")
        if proc.stdout.strip():
            print(proc.stdout.rstrip())


def schema_filter(schemas: list[str]) -> str:
    if not schemas:
        return "NOT IN (" + ",".join(q_str(s) for s in SYSTEM_SCHEMAS) + ")"
    return "IN (" + ",".join(q_str(s) for s in schemas) + ")"


def column_definition(row: dict[str, str], comment: str) -> str:
    data_type = row["data_type"].lower()
    extra = row["extra"] or ""
    parts = [q_ident(row["column_name"]), row["column_type"]]
    if row["charset"]:
        parts += ["CHARACTER SET", row["charset"], "COLLATE", row["collation"]]
    if row["generation"]:
        kind = "STORED" if "STORED GENERATED" in extra.upper() else "VIRTUAL"
        parts += ["GENERATED ALWAYS AS (" + row["generation"] + ")", kind, "COMMENT " + q_str(comment)]
        return " ".join(parts)
    parts.append("NULL" if row["nullable"] == "YES" else "NOT NULL")
    default = row["default"]
    if default is not None:
        is_expr = "DEFAULT_GENERATED" in extra.upper()
        if is_expr or data_type in NUMERIC_TYPES or data_type in ("bit", "binary", "varbinary"):
            parts.append("DEFAULT " + default)
        elif re.fullmatch(r"CURRENT_TIMESTAMP(\(\d+\))?", default, re.IGNORECASE):
            parts.append("DEFAULT " + default)
        else:
            parts.append("DEFAULT " + q_str(default))
    tail = extra.replace("DEFAULT_GENERATED", "").strip()
    if tail:
        parts.append(tail)
    parts.append("COMMENT " + q_str(comment))
    return " ".join(parts)


def has_cjk(text: str) -> bool:
    return any("\u4e00" <= ch <= "\u9fff" for ch in text)


def verify(db: MySql, where: str) -> int:
    tables = db.query(TABLES_SQL.format(schema_filter=where, hint_filter=""))
    columns = db.query(COLUMNS_SQL.format(schema_filter=where, hint_filter=""))
    garbled: list[str] = []
    normal = 0
    other = 0
    for schema, table, comment in tables:
        where_name = f"{schema}.{table}(表)"
        if repair_text(comment) is not None:
            garbled.append(f"{where_name}: {comment}")
        elif has_cjk(comment):
            normal += 1
        else:
            other += 1
    for values in columns:
        schema, table, column, comment = values[0], values[1], values[2], values[3]
        where_name = f"{schema}.{table}.{column}"
        if repair_text(comment) is not None:
            garbled.append(f"{where_name}: {comment}")
        elif has_cjk(comment):
            normal += 1
        else:
            other += 1
    print(f"正常中文注释 {normal} 条，非中文/其他 {other} 条，仍乱码 {len(garbled)} 条。")
    for line in garbled:
        print("  仍乱码 " + line)
    if garbled:
        print("存在仍乱码的注释：请先执行 --apply。若已执行过仍出现，把以上内容发我。")
        return 2
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="修复 MySQL 注释乱码（UTF-8 被按 latin1 二次编码）")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default="3306")
    parser.add_argument("--user", default="root")
    parser.add_argument("--schemas", default="", help="逗号分隔；默认扫描全部非系统库")
    parser.add_argument("--apply", action="store_true", help="实际执行修复（默认只列出）")
    parser.add_argument("--print-only", action="store_true", help="只打印 ALTER 语句，不连接执行")
    parser.add_argument("--verify", action="store_true", help="回读全部注释，检查修复结果")
    args = parser.parse_args()

    schemas = [s.strip() for s in args.schemas.split(",") if s.strip()]
    where = schema_filter(schemas)
    hint_filter = "" if args.verify else f"AND HEX(column_comment) REGEXP '{MOJIBAKE_HINT}'"
    table_hint_filter = "" if args.verify else f"AND HEX(table_comment) REGEXP '{MOJIBAKE_HINT}'"

    password = os.environ.get("MYSQL_PWD")
    if not password:
        password = getpass.getpass(f"{args.user}@{args.host} 的 MySQL 密码: ")
    db = MySql(args.host, args.port, args.user, password)

    if args.verify:
        return verify(db, where)

    tables = db.query(TABLES_SQL.format(schema_filter=where, hint_filter=table_hint_filter))
    columns = db.query(COLUMNS_SQL.format(schema_filter=where, hint_filter=hint_filter))
    if not tables and not columns:
        print("未发现疑似乱码注释。")
        return 0

    statements: list[str] = []
    skipped = 0
    print("== 表注释 ==")
    for schema, table, comment in tables:
        fixed = repair_text(comment)
        if fixed is None:
            skipped += 1
            print(f"  跳过（不可还原） {schema}.{table}: {comment}")
            continue
        print(f"  {schema}.{table}: {comment}  ->  {fixed}")
        statements.append(f"ALTER TABLE {q_ident(schema)}.{q_ident(table)} COMMENT = {q_str(fixed)};")

    print("== 字段注释 ==")
    for values in columns:
        row = dict(zip(
            ("schema", "table", "column_name", "comment", "column_type", "nullable",
             "default_is_null", "default", "extra", "charset", "collation",
             "generation", "data_type"), values))
        for key in ("extra", "charset", "collation", "generation"):
            if row[key] == "NULL":  # mysql CLI 把 SQL NULL 输出为字符串
                row[key] = ""
        fixed = repair_text(row["comment"])
        if fixed is None or row["generation"]:
            skipped += 1
            print(f"  跳过（不可还原/生成列） {row['schema']}.{row['table']}.{row['column_name']}: {row['comment']}")
            continue
        if row["default_is_null"] == "1":
            row["default"] = None
        print(f"  {row['schema']}.{row['table']}.{row['column_name']}: {row['comment']}  ->  {fixed}")
        definition = column_definition(row, fixed)
        statements.append(
            f"ALTER TABLE {q_ident(row['schema'])}.{q_ident(row['table'])} "
            f"MODIFY COLUMN {definition};"
        )

    print(f"\n共 {len(statements)} 条修复语句，跳过 {skipped} 条。")
    if args.print_only:
        print("\n".join(statements))
        print("（--print-only，未连接执行）")
        return 0
    if not args.apply:
        print("（dry-run，未执行；加 --print-only 查看 ALTER 语句，确认后加 --apply 执行）")
        return 0
    if not statements:
        return 0
    db.execute("SET NAMES utf8mb4;\n" + "\n".join(statements))
    print(f"已执行 {len(statements)} 条修复语句。请刷新 Navicat 确认。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
