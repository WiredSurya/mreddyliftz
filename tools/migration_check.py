#!/usr/bin/env python3
"""
Replays every Room migration against real SQLite and checks the result matches the schema Room
expects, WITHOUT needing a device.

Room only validates a migration when the app opens a real database on a phone, which makes a bad
ALTER TABLE a crash-on-launch for whoever updates first. This closes that gap on the desktop:

  1. build the OLD version's tables from app/schemas/<n>.json
  2. put a row in each, so a NOT NULL column with no usable default would fail here
  3. run the migration SQL scraped out of Migrations.kt
  4. compare the resulting column set against app/schemas/<n+1>.json

Run: python3 tools/migration_check.py
"""
import json
import os
import re
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCHEMA_DIR = os.path.join(ROOT, "app/schemas/com.mreddy.liftz.data.db.LiftzDatabase")
MIGRATIONS_KT = os.path.join(ROOT, "app/src/main/java/com/mreddy/liftz/data/db/Migrations.kt")


def load_schema(version):
    with open(os.path.join(SCHEMA_DIR, f"{version}.json")) as fh:
        return json.load(fh)["database"]


def create_sql(entity):
    return entity["createSql"].replace("${TABLE_NAME}", entity["tableName"])


def scrape_migrations():
    """Pull the execSQL bodies out of each `migration(a, b) { ... }` block."""
    src = open(MIGRATIONS_KT).read()
    # Strip block and line comments first. The KDoc in Migrations.kt contains a worked EXAMPLE
    # migration, and without this the scraper happily "finds" that example and reports the real
    # migration as broken.
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    src = re.sub(r"//[^\n]*", "", src)
    out = {}
    for m in re.finditer(r"migration\((\d+),\s*(\d+)\)\s*\{(.*?)\n    \}", src, re.S):
        frm, to, body = int(m.group(1)), int(m.group(2)), m.group(3)
        out[(frm, to)] = re.findall(r'execSQL\(\s*"(.*?)"\s*\)', body, re.S)
    return out


def columns(conn, table):
    return {r[1]: (r[2].upper(), bool(r[3])) for r in conn.execute(f"PRAGMA table_info(`{table}`)")}


def expected_columns(db, table):
    for e in db["entities"]:
        if e["tableName"] == table:
            return {f["columnName"]: (f["affinity"].upper(), f["notNull"]) for f in e["fields"]}
    return None


def main():
    versions = sorted(int(f.split(".")[0]) for f in os.listdir(SCHEMA_DIR) if f.endswith(".json"))
    migrations = scrape_migrations()
    failures = []

    for old, new in zip(versions, versions[1:]):
        step = migrations.get((old, new))
        if step is None:
            failures.append(f"v{old} -> v{new}: no migration found in Migrations.kt")
            continue

        conn = sqlite3.connect(":memory:")
        old_db = load_schema(old)
        for entity in old_db["entities"]:
            conn.execute(create_sql(entity))
            # A row of real data, so the migration has to survive non-empty tables the way a
            # phone with history would.
            cols = [f["columnName"] for f in entity["fields"]]
            vals = [1 if f["affinity"].upper() == "INTEGER" else "x" for f in entity["fields"]]
            placeholders = ",".join("?" * len(cols))
            try:
                conn.execute(
                    f'INSERT INTO `{entity["tableName"]}` '
                    f'({",".join("`" + c + "`" for c in cols)}) VALUES ({placeholders})',
                    vals,
                )
            except sqlite3.Error:
                pass  # FK-constrained child tables; the parent rows above are what matter

        try:
            for stmt in step:
                conn.execute(stmt)
        except sqlite3.Error as exc:
            failures.append(f"v{old} -> v{new}: SQL failed: {exc}")
            conn.close()
            continue

        new_db = load_schema(new)
        for entity in new_db["entities"]:
            table = entity["tableName"]
            want = expected_columns(new_db, table)
            got = columns(conn, table)
            if not got:
                continue  # table created fresh by Room on install, not by migration
            missing = set(want) - set(got)
            extra = set(got) - set(want)
            if missing:
                failures.append(f"v{old} -> v{new}: `{table}` missing {sorted(missing)}")
            if extra:
                failures.append(f"v{old} -> v{new}: `{table}` has unexpected {sorted(extra)}")
            for col, (aff, notnull) in want.items():
                if col in got and got[col][1] != notnull:
                    failures.append(
                        f"v{old} -> v{new}: `{table}`.`{col}` nullability "
                        f"{got[col][1]} != expected {notnull}"
                    )

        # The rows we seeded must still be there. A migration that drops history is the exact
        # failure this whole scaffold exists to prevent.
        for entity in old_db["entities"]:
            table = entity["tableName"]
            n = conn.execute(f"SELECT COUNT(*) FROM `{table}`").fetchone()[0]
            if n == 0 and table in ("daily_logs", "goals", "increments", "exercises"):
                failures.append(f"v{old} -> v{new}: `{table}` lost its rows")

        conn.close()
        if not failures:
            print(f"PASS v{old} -> v{new} ({len(step)} statement(s))")

    if failures:
        print()
        for f in failures:
            print("FAIL " + f)
        sys.exit(1)
    print(f"\nAll {len(versions) - 1} migration(s) verified against real SQLite.")


if __name__ == "__main__":
    main()
