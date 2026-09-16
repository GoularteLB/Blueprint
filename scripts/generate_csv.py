#!/usr/bin/env python3
"""Gera CSV de transacoes em stream.

Uso: python generate_csv.py --rows 1000000 --out transactions.csv --error-rate 0.005
"""
import argparse
import csv
import random
from datetime import datetime, timedelta, timezone

CATEGORIES = ["FOOD", "FUEL", "HEALTH", "TRANSPORT", "LEISURE",
              "EDUCATION", "HOUSING", "UTILITIES", "SHOPPING", "OTHER"]
SOURCES = ["WEB", "APP", "POS", "API"]
DESCRIPTIONS = ["Lunch", "Gas station", "Pharmacy", "Uber", "Cinema",
                "Course", "Rent", "Electricity", "Store, downtown", "Misc"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--rows", type=int, required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--error-rate", type=float, default=0.005)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--months", type=int, default=12)
    a = ap.parse_args()

    rnd = random.Random(a.seed)
    start = datetime(2025, 9, 1, tzinfo=timezone.utc)
    span = a.months * 30 * 24 * 3600

    with open(a.out, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["external_id", "occurred_at", "category", "description", "amount", "source"])
        for i in range(a.rows):
            ts = start + timedelta(seconds=rnd.randrange(span))
            cat = rnd.choice(CATEGORIES)
            amt = round(rnd.lognormvariate(3.0, 1.0), 2)
            desc = rnd.choice(DESCRIPTIONS)
            src = rnd.choice(SOURCES)
            if rnd.random() < a.error_rate:
                kind = rnd.choice(["bad_amount", "bad_date", "empty_category"])
                if kind == "bad_amount":
                    amt = "abc"
                elif kind == "bad_date":
                    ts = "not-a-date"
                else:
                    cat = ""
            ts_str = ts if isinstance(ts, str) else ts.isoformat(timespec="seconds").replace("+00:00", "Z")
            w.writerow([f"TX{i:09d}", ts_str, cat, desc, amt, src])


if __name__ == "__main__":
    main()
