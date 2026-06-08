#!/usr/bin/env python3
"""
Fetch Monobank transactions and classify them using mono-category-mapping.yml.

Outputs:
  - mono-category-report.json  (full classification + stats)
  - mono-category-report.md    (human-readable summary)

Usage:
  python3 scripts/analyze_mono_categories.py [--months 3] [--skip-fetch]
"""

from __future__ import annotations

import argparse
import json
import re
import ssl
import sys
import time
import urllib.error
import urllib.request
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path

try:
    import yaml
except ImportError:
    print("PyYAML required: pip install pyyaml", file=sys.stderr)
    sys.exit(1)

ROOT = Path(__file__).resolve().parent.parent
SETTINGS_PATH = ROOT / "settings.yml"
MAPPING_PATH = ROOT / "mono-category-mapping.yml"
MCC_PATH = ROOT / "src/main/resources/mcc.json"
REPORT_JSON = ROOT / "mono-category-report.json"
REPORT_MD = ROOT / "mono-category-report.md"
CACHE_PATH = ROOT / "mono-txns-cache.json"

MONO_API = "https://api.monobank.ua/personal/statement"
RATE_LIMIT_SEC = 65
CHUNK_DAYS = 25


def load_accounts(settings_path: Path) -> list[dict]:
    text = settings_path.read_text()
    accounts: list[dict] = []
    # Only parse the accounts.settings block (stops before top-level mcc:)
    accounts_block = re.search(
        r"accounts:\s*\n\s*settings:\s*\n(.*?)(?=\nmcc:)",
        text,
        re.DOTALL,
    )
    if not accounts_block:
        raise ValueError("Could not find accounts.settings in settings.yml")

    blocks = re.split(r"\n\s*-\s*!<mono>\s*\n", accounts_block.group(1))
    for block in blocks:
        if not block.strip():
            continue
        account_id = re.search(r'accountId:\s*"([^"]+)"', block)
        token = re.search(r'token:\s*"([^"]+)"', block)
        alias = re.search(r'alias:\s*"([^"]+)"', block)
        if account_id and token and alias:
            accounts.append({
                "id": account_id.group(1),
                "token": token.group(1),
                "alias": alias.group(1),
            })

    return accounts


def fetch_statement(token: str, account_id: str, t_from: int, t_to: int) -> list[dict]:
    url = f"{MONO_API}/{account_id}/{t_from}/{t_to}"
    req = urllib.request.Request(url, headers={"X-Token": token})
    ctx = ssl.create_default_context()
    with urllib.request.urlopen(req, context=ctx, timeout=30) as resp:
        return json.load(resp)


def fetch_all_transactions(accounts: list[dict], months: int) -> tuple[list[dict], list[str]]:
    now = datetime.now(timezone.utc)
    date_from = now - timedelta(days=months * 30)
    t_from_base = int(date_from.timestamp())
    t_to_base = int(now.timestamp())

    chunks: list[tuple[int, int]] = []
    t = t_from_base
    while t < t_to_base:
        t_end = min(t + CHUNK_DAYS * 86400, t_to_base)
        chunks.append((t, t_end))
        t = t_end

    all_txns: list[dict] = []
    errors: list[str] = []

    for acc in accounts:
        for chunk_from, chunk_to in chunks:
            label = f"{acc['alias']} [{datetime.fromtimestamp(chunk_from).date()}]"
            for attempt in range(3):
                try:
                    txns = fetch_statement(acc["token"], acc["id"], chunk_from, chunk_to)
                    for tx in txns:
                        tx["_account"] = acc["alias"]
                        tx["_account_id"] = acc["id"]
                    all_txns.extend(txns)
                    print(f"  {label}: {len(txns)} txns")
                    time.sleep(1)
                    break
                except urllib.error.HTTPError as e:
                    if e.code == 429 and attempt < 2:
                        print(f"  {label}: rate limited, waiting {RATE_LIMIT_SEC}s...")
                        time.sleep(RATE_LIMIT_SEC)
                    else:
                        msg = f"{label}: HTTP {e.code}"
                        errors.append(msg)
                        print(f"  ERROR {msg}")
                        break
                except Exception as e:
                    msg = f"{label}: {e}"
                    errors.append(msg)
                    print(f"  ERROR {msg}")
                    break

    by_id = {t["id"]: t for t in all_txns}
    return list(by_id.values()), errors


def load_mcc_registry(path: Path) -> dict[int, dict]:
    registry: dict[int, dict] = {}
    with path.open() as f:
        for entry in json.load(f):
            registry[int(entry["mcc"])] = entry
    return registry


def load_mapping(path: Path) -> dict:
    with path.open() as f:
        return yaml.safe_load(f)


def pattern_matches(description: str, pattern: str) -> bool:
    return pattern.lower() in description.lower()


def classify_transaction(
    txn: dict,
    mapping: dict,
    mcc_registry: dict[int, dict],
) -> dict:
    desc = (txn.get("description") or "").strip()
    mcc = txn.get("mcc", 0)
    amount_uah = abs(txn.get("amount", 0)) / 100
    mcc_entry = mcc_registry.get(mcc, {})
    mcc_group_type = mcc_entry.get("group", {}).get("type", "")

    # Pass 1: payee pattern rules (ordered by group/category in YAML)
    for group_def in mapping.get("groups", []):
        group_name = group_def["name"]
        is_expense = group_def.get("is_expense", True)
        for cat_def in group_def.get("categories", []):
            cat_name = cat_def["name"]
            min_amount = cat_def.get("min_amount_uah")
            max_amount = cat_def.get("max_amount_uah")
            if min_amount is not None and amount_uah < min_amount:
                continue
            if max_amount is not None and amount_uah > max_amount:
                continue

            for pattern in cat_def.get("payee_patterns", []):
                if pattern_matches(desc, pattern):
                    return _result(
                        group_name, cat_name, "payee", pattern,
                        is_expense, cat_def, txn,
                    )

            cat_mccs = cat_def.get("mcc", [])
            if mcc in cat_mccs and cat_def.get("payee_patterns"):
                continue  # mcc-only match only when no payee patterns defined
            if mcc in cat_mccs and not cat_def.get("payee_patterns"):
                return _result(
                    group_name, cat_name, "mcc", str(mcc),
                    is_expense, cat_def, txn,
                )

    # Pass 2: MCC fallback
    mcc_fb = mapping.get("mcc_fallback", {}).get(mcc)
    if mcc_fb:
        group_def = next(
            (g for g in mapping["groups"] if g["name"] == mcc_fb["group"]), None,
        )
        is_expense = group_def.get("is_expense", True) if group_def else True
        return _result(
            mcc_fb["group"], mcc_fb["category"], "mcc_fallback", str(mcc),
            is_expense, {}, txn,
        )

    # Pass 3: MCC group type fallback
    type_fb = mapping.get("mcc_group_type_fallback", {}).get(mcc_group_type)
    if type_fb:
        group_def = next(
            (g for g in mapping["groups"] if g["name"] == type_fb["group"]), None,
        )
        is_expense = group_def.get("is_expense", True) if group_def else True
        return _result(
            type_fb["group"], type_fb["category"], "mcc_group_type", mcc_group_type,
            is_expense, {}, txn,
        )

    # Pass 4: unmatched MCC 4829 transfers
    if mcc == 4829:
        return _result(
            "Transfers", "Other P2P", "mcc", "4829",
            False, {}, txn,
        )

    return _result(
        "Other / Review", desc[:40] or "Unknown", "unmatched", "",
        True, {}, txn,
    )


def _result(
    group: str, category: str, matched_by: str, matched_value: str,
    is_expense: bool, cat_def: dict, txn: dict,
) -> dict:
    return {
        "group": group,
        "category": category,
        "matched_by": matched_by,
        "matched_value": matched_value,
        "is_expense": is_expense,
        "exclude_from_reports": cat_def.get("exclude_from_reports", False),
        "needs_manual_review": cat_def.get("needs_manual_review", False),
        "description": txn.get("description"),
        "mcc": txn.get("mcc"),
        "amount_uah": abs(txn.get("amount", 0)) / 100,
        "account": txn.get("_account"),
        "time": txn.get("time"),
        "id": txn.get("id"),
    }


def build_report(
    classified: list[dict],
    spending_count: int,
    errors: list[str],
    period: str,
) -> dict:
    by_group: dict = defaultdict(lambda: {"count": 0, "total": 0.0, "categories": Counter()})
    by_payee: dict = defaultdict(lambda: {
        "count": 0, "total": 0.0, "group": "", "category": "", "needs_review": False,
    })

    expense_total = 0.0
    expense_count = 0

    for c in classified:
        g = c["group"]
        cat = c["category"]
        by_group[g]["count"] += 1
        by_group[g]["total"] += c["amount_uah"]
        by_group[g]["categories"][cat] += 1

        if c["is_expense"] and not c["exclude_from_reports"]:
            expense_total += c["amount_uah"]
            expense_count += 1

        payee = c["description"] or "?"
        by_payee[payee]["count"] += 1
        by_payee[payee]["total"] += c["amount_uah"]
        by_payee[payee]["group"] = g
        by_payee[payee]["category"] = cat
        by_payee[payee]["needs_review"] = c["needs_manual_review"]

    payee_mapping = sorted(
        [
            {
                "payee": payee,
                "group": info["group"],
                "category": info["category"],
                "total_uah": round(info["total"], 2),
                "count": info["count"],
                "needs_manual_review": info["needs_review"],
            }
            for payee, info in by_payee.items()
        ],
        key=lambda x: -x["total_uah"],
    )

    group_summary = sorted(
        [
            {
                "group": g,
                "total_uah": round(info["total"], 2),
                "count": info["count"],
                "categories": [
                    {"name": cat, "count": cnt}
                    for cat, cnt in info["categories"].most_common()
                ],
            }
            for g, info in by_group.items()
        ],
        key=lambda x: -x["total_uah"],
    )

    unmatched = [c for c in classified if c["group"] == "Other / Review"]
    needs_review = [c for c in classified if c["needs_manual_review"]]

    return {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "period": period,
        "spending_transactions": spending_count,
        "classified_transactions": len(classified),
        "expense_total_uah": round(expense_total, 2),
        "expense_count": expense_count,
        "fetch_errors": errors,
        "group_summary": group_summary,
        "payee_mapping": payee_mapping,
        "unmatched": unmatched,
        "needs_manual_review": needs_review,
        "transactions": classified,
    }


def render_markdown(report: dict) -> str:
    lines = [
        "# Mono Category Mapping Report",
        "",
        f"Generated: {report['generated_at']}",
        f"Period: {report['period']}",
        f"Spending transactions: {report['spending_transactions']}",
        f"Expense total (excl. transfers/pots): **{report['expense_total_uah']:,.0f} UAH** ({report['expense_count']} txns)",
        "",
    ]
    if report["fetch_errors"]:
        lines += ["## Fetch errors", ""]
        for e in report["fetch_errors"]:
            lines.append(f"- {e}")
        lines.append("")

    lines += ["## Group summary", "", "| Group | Total UAH | Txns | Categories |", "|-------|-----------|------|--------------|"]
    for g in report["group_summary"]:
        cats = ", ".join(f"{c['name']} ({c['count']})" for c in g["categories"][:5])
        lines.append(f"| {g['group']} | {g['total_uah']:,.0f} | {g['count']} | {cats} |")
    lines.append("")

    lines += ["## Payee → category mapping", "", "| Payee | Group | Category | Total UAH | Txns | Review? |",
              "|-------|-------|----------|-----------|------|---------|"]
    for p in report["payee_mapping"][:60]:
        review = "yes" if p["needs_manual_review"] else ""
        payee = p["payee"][:40].replace("|", "/")
        lines.append(
            f"| {payee} | {p['group']} | {p['category']} | {p['total_uah']:,.0f} | {p['count']} | {review} |",
        )
    lines.append("")

    if report["unmatched"]:
        lines += ["## Unmatched (needs review)", ""]
        for c in sorted(report["unmatched"], key=lambda x: -x["amount_uah"])[:20]:
            lines.append(f"- {c['description']} — {c['amount_uah']:.0f} UAH (MCC {c['mcc']})")
        lines.append("")

    lines += [
        "## Manual next steps",
        "",
        "1. Create the 10 groups in your budget app using `mono-category-mapping.yml`",
        "2. Mark **Transfers** as non-budgeted",
        "3. Exclude **Internal (pots)** / `На альпаку` from expense reports",
        "4. Review **PayPal** transactions individually",
        "",
    ]
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description="Analyze Mono transactions and suggest categories")
    parser.add_argument("--months", type=int, default=3, help="Months of history to fetch")
    parser.add_argument("--skip-fetch", action="store_true", help="Use cached mono-txns-cache.json")
    args = parser.parse_args()

    mapping = load_mapping(MAPPING_PATH)
    mcc_registry = load_mcc_registry(MCC_PATH)
    accounts = load_accounts(SETTINGS_PATH)

    now = datetime.now(timezone.utc)
    period = f"{(now - timedelta(days=args.months * 30)).date()} → {now.date()}"

    if args.skip_fetch and CACHE_PATH.exists():
        print(f"Loading cached transactions from {CACHE_PATH}")
        with CACHE_PATH.open() as f:
            cache = json.load(f)
        txns = cache["transactions"]
        errors = cache.get("errors", [])
    else:
        print(f"Fetching transactions for {len(accounts)} accounts ({period})...")
        txns, errors = fetch_all_transactions(accounts, args.months)
        with CACHE_PATH.open("w") as f:
            json.dump({"transactions": txns, "errors": errors, "period": period}, f)
        print(f"Cached {len(txns)} transactions to {CACHE_PATH}")

    spending = [t for t in txns if t.get("amount", 0) < 0]
    print(f"Classifying {len(spending)} spending transactions...")

    classified = [classify_transaction(t, mapping, mcc_registry) for t in spending]
    report = build_report(classified, len(spending), errors, period)

    with REPORT_JSON.open("w") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)
    print(f"Wrote {REPORT_JSON}")

    md = render_markdown(report)
    REPORT_MD.write_text(md, encoding="utf-8")
    print(f"Wrote {REPORT_MD}")

    print(f"\nExpense total: {report['expense_total_uah']:,.0f} UAH across {report['expense_count']} txns")
    print(f"Groups: {len(report['group_summary'])}, Payees mapped: {len(report['payee_mapping'])}")
    if report["unmatched"]:
        print(f"Unmatched: {len(report['unmatched'])} txns")
    if report["needs_manual_review"]:
        print(f"Needs manual review: {len(report['needs_manual_review'])} txns")


if __name__ == "__main__":
    main()
