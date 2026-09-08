#!/usr/bin/env python3
"""Keep runtime business SQL in MyBatis XML; retain BaseMapper/typed wrappers."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
# Datasource bootstrap and framework SQL rewriting are infrastructure, not business queries.
MODULES = ("scx-ocpx-admin", "scx-ocpx-engine", "scx-system", "scx-auth", "scx-visual")
TOKEN = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/')
FORBIDDEN_CODE = re.compile(
    r"org\.springframework\.jdbc\.(?:core|object)|"
    r"@(?:Select|Insert|Update|Delete)(?:Provider)?\s*\(|"
    r"\.(?:last|apply|inSql|notInSql|notExists|eqSql|gtSql|geSql|ltSql|leSql|setSql)\s*\(|"
    r"\.(?:prepareStatement|createStatement|executeQuery|executeUpdate)\s*\("
)
SQL = re.compile(
    r"\b(?:SELECT\s+(?:\*|COUNT\s*\(|[\w.`]+\s*(?:,|FROM\b))|"
    r"INSERT\s+(?:IGNORE\s+)?INTO\b|UPDATE\s+[\w`]+\s+SET\b|"
    r"DELETE\s+(?:FROM\b|[\w`]+\s+FROM\b)|REPLACE\s+INTO\b|"
    r"FROM\s+(?:scx_|sys_)|JOIN\s+(?:scx_|sys_)|FOR\s+UPDATE\b|LIMIT\s+\d)", re.I
)
errors = []
for module in MODULES:
    for path in (ROOT / "scx-backend" / module).glob("**/src/main/java/**/*.java"):
        source = path.read_text()
        code = TOKEN.sub(lambda match: " " * len(match[0]), source)
        for match in FORBIDDEN_CODE.finditer(code):
            errors.append((path, source.count("\n", 0, match.start()) + 1, "SQL execution/fragment API"))
        for token in TOKEN.finditer(source):
            if token[0].startswith('"') and SQL.search(token[0]):
                errors.append((path, source.count("\n", 0, token.start()) + 1, "business SQL literal"))
if errors:
    for path, line, reason in errors:
        print(f"{path.relative_to(ROOT)}:{line}: {reason}; move SQL to Mapper XML", file=sys.stderr)
    sys.exit(1)
print("Business SQL XML boundary: PASS")
