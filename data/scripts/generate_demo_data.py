"""Generates the moby-cars demo OneDrive data (fictional). Deterministic."""
import csv, os, random, sys, datetime as dt

OUT = sys.argv[1]
rnd = random.Random(20261116)
SEG = ["SUV", "Truck", "Sedan", "Electric"]
REG = ["Northeast", "Southeast", "Midwest", "Southwest", "West"]
REG_W = {"Northeast": .19, "Southeast": .24, "Midwest": .20, "Southwest": .17, "West": .20}
SEG_REG = {  # regional tilt per segment
    "SUV": {"Northeast": 1.05, "Southeast": 1.0, "Midwest": 1.05, "Southwest": .95, "West": .95},
    "Truck": {"Northeast": .8, "Southeast": 1.05, "Midwest": 1.15, "Southwest": 1.25, "West": .8},
    "Sedan": {"Northeast": 1.15, "Southeast": 1.05, "Midwest": .95, "Southwest": .9, "West": .95},
    "Electric": {"Northeast": .9, "Southeast": .75, "Midwest": .6, "Southwest": .9, "West": 1.85},
}
Q = {
    "Q2": dict(months=["2026-04", "2026-05", "2026-06"], mw=[.32, .33, .35],
               units={"SUV": 231000, "Truck": 168000, "Sedan": 142000, "Electric": 71000},
               price={"SUV": 30420, "Truck": 34150, "Sedan": 19980, "Electric": 29700},
               days={"SUV": 39, "Truck": 37, "Sedan": 52, "Electric": 58},
               over60={"SUV": 9, "Truck": 7, "Sedan": 15, "Electric": 19},
               wq={"SUV": -0.8, "Truck": -0.4, "Sedan": -1.9, "Electric": -5.2},
               yoy={"SUV": -3.1, "Truck": -1.8, "Sedan": -6.0, "Electric": -14.8},
               weeks_start=dt.date(2026, 4, 4)),
    "Q3": dict(months=["2026-07", "2026-08", "2026-09"], mw=[.34, .35, .31],
               units={"SUV": 237000, "Truck": 171000, "Sedan": 144000, "Electric": 73000},
               price={"SUV": 30310, "Truck": 34090, "Sedan": 19820, "Electric": 29150},
               days={"SUV": 38, "Truck": 36, "Sedan": 51, "Electric": 56},
               over60={"SUV": 8, "Truck": 6, "Sedan": 14, "Electric": 16},
               wq={"SUV": -0.5, "Truck": -0.2, "Sedan": -1.4, "Electric": -3.1},
               yoy={"SUV": -2.6, "Truck": -1.2, "Sedan": -5.1, "Electric": -11.9},
               weeks_start=dt.date(2026, 7, 4)),
}
MILES = {"SUV": 47000, "Truck": 58000, "Sedan": 52000, "Electric": 31000}
AGE = {"SUV": 4.6, "Truck": 5.4, "Sedan": 5.1, "Electric": 2.9}
PRICE_REG = {"Northeast": 1.02, "Southeast": .98, "Midwest": .97, "Southwest": 1.0, "West": 1.04}

def split(total, weights):
    s = sum(weights); raw = [total * w / s for w in weights]
    out = [int(round(x / 10) * 10) for x in raw]
    out[-1] += total - sum(out)
    return out

def write(path, header, rows):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="") as f:
        w = csv.writer(f, lineterminator="\n"); w.writerow(header); w.writerows(rows)

regional_units = {}
for q, c in Q.items():
    rows = []; regional_units[q] = {}
    for seg in SEG:
        cells = [(m, r) for m in range(3) for r in REG]
        weights = [c["mw"][m] * REG_W[r] * SEG_REG[seg][r] * rnd.uniform(.96, 1.04) for m, r in cells]
        units = split(c["units"][seg], weights)
        prices = [c["price"][seg] * PRICE_REG[r] * rnd.uniform(.985, 1.015) for m, r in cells]
        wavg = sum(u * p for u, p in zip(units, prices)) / sum(units)
        prices = [round(p * c["price"][seg] / wavg) for p in prices]
        for (m, r), u, p in zip(cells, units, prices):
            regional_units[q][(seg, r)] = regional_units[q].get((seg, r), 0) + u
            rows.append([c["months"][m], r, seg, u, p, int(round(MILES[seg] * rnd.uniform(.93, 1.07), -2)),
                         round(AGE[seg] * rnd.uniform(.94, 1.06), 1)])
    rows.sort(key=lambda x: (x[0], REG.index(x[1]), SEG.index(x[2])))
    write(f"{OUT}/used-vehicle-sales/Used_Vehicle_Sales_{q}_2026.csv",
          ["month", "region", "segment", "units_sold", "avg_retail_price_usd", "avg_mileage", "avg_vehicle_age_years"], rows)

# wholesale index: weekly, plus the prior quarter's closing week as the baseline row
start_index = {"SUV": 212.4, "Truck": 229.8, "Sedan": 181.6, "Electric": 156.2}
AUCTION_BASE = {"SUV": 115.0, "Truck": 128.0, "Sedan": 85.0, "Electric": 152.0}  # usd per index point
close = {}
for q, c in Q.items():
    rows = []
    base_week = c["weeks_start"] - dt.timedelta(days=7)
    for seg in SEG:
        b = start_index[seg] if q == "Q2" else close[seg]
        end = round(b * (1 + c["wq"][seg] / 100), 1)
        b_yoy_end = c["yoy"][seg]
        prev_yoy = Q["Q2"]["yoy"][seg] + (rnd.uniform(.5, 1.5) if q == "Q2" else 0)
        if q == "Q3": prev_yoy = Q["Q2"]["yoy"][seg]
        path = [b]
        for k in range(1, 13):
            frac = k / 13
            path.append(round(b + (end - b) * frac + rnd.uniform(-.6, .6) * (1 - abs(2 * frac - 1)), 1))
        path.append(end)
        for k, idx in enumerate(path):
            week = base_week + dt.timedelta(days=7 * k)
            yoy = prev_yoy + (b_yoy_end - prev_yoy) * (k / 13)
            ya = round(idx / (1 + yoy / 100), 1)
            rows.append([week.isoformat(), seg, idx, ya, int(round(idx * AUCTION_BASE[seg], -1))])
        close[seg] = end
    rows.sort(key=lambda x: (x[0], SEG.index(x[1])))
    write(f"{OUT}/wholesale-pricing/Wholesale_Price_Index_{q}_2026.csv",
          ["week_ending", "segment", "wholesale_index", "wholesale_index_year_ago", "avg_auction_price_usd"], rows)

# auction volumes, Q3 only
conv = {"SUV": 61, "Truck": 64, "Sedan": 55, "Electric": 47}
offered = {"SUV": 21000, "Truck": 15500, "Sedan": 14800, "Electric": 7900}
rows = []
for k in range(13):
    week = Q["Q3"]["weeks_start"] + dt.timedelta(days=7 * k)
    for seg in SEG:
        o = int(offered[seg] * rnd.uniform(.92, 1.08))
        cv = conv[seg] + rnd.uniform(-2.5, 2.5) - (k / 12) * (2.0 if seg == "Electric" else 0)
        s = int(o * cv / 100)
        rows.append([week.isoformat(), seg, o, s, round(100 * s / o, 1)])
write(f"{OUT}/wholesale-pricing/Auction_Volumes_Q3_2026.csv",
      ["week_ending", "segment", "units_offered", "units_sold", "conversion_pct"], rows)

# days to turn by segment and region, weighted by units sold
for q, c in Q.items():
    rows = []
    for seg in SEG:
        ru = {r: regional_units[q][(seg, r)] for r in REG}
        days = {r: c["days"][seg] + rnd.uniform(-2.5, 2.5) for r in REG}
        if seg == "Sedan": days["Midwest"] = c["days"][seg] + 9  # the slowest cell
        other = [r for r in REG if not (seg == "Sedan" and r == "Midwest")]
        wavg = sum(ru[r] * days[r] for r in REG) / sum(ru.values())
        shift = (c["days"][seg] - wavg) * sum(ru.values()) / sum(ru[r] for r in other)
        for r in other: days[r] += shift
        over = {r: c["over60"][seg] + rnd.uniform(-2, 2) + (days[r] - c["days"][seg]) * .6 for r in REG}
        stock = {r: int(round(ru[r] / 90 * days[r] * rnd.uniform(.97, 1.03), -1)) for r in REG}
        wo = sum(stock[r] * over[r] for r in REG) / sum(stock.values())
        for r in REG: over[r] += c["over60"][seg] - wo
        for r in REG:
            rows.append([seg, r, ru[r], round(days[r], 1), stock[r], round(over[r], 1)])
    write(f"{OUT}/inventory-and-days-to-turn/Days_To_Turn_By_Segment_{q}_2026.csv",
          ["segment", "region", "units_sold", "avg_days_to_turn", "units_in_stock", "pct_stock_over_60_days"], rows)

# auto-loan performance by origination cohort and credit tier
TIERS = {"prime": (.58, 1.1, .35), "near_prime": (.27, 3.6, 1.4), "subprime": (.15, 9.8, 4.3)}
def cohorts(end):
    y, qq = 2024, 3; out = []
    while (y, qq) <= end:
        out.append((y, qq)); qq += 1
        if qq == 5: y, qq = y + 1, 1
    return out
LTV0 = {(2024, 3): 96, (2024, 4): 98, (2025, 1): 101, (2025, 2): 104, (2025, 3): 102, (2025, 4): 99,
        (2026, 1): 97, (2026, 2): 96, (2026, 3): 95}
for q, end, target30, target60 in [("Q2", (2026, 2), 3.9, 1.6), ("Q3", (2026, 3), 4.1, 1.7)]:
    rows = []; cs = cohorts(end)
    for (y, cq) in cs:
        age = (end[0] - y) * 4 + end[1] - cq
        seasoning = min(1.0, .25 + age * .18)
        for tier, (share, d30, d60) in TIERS.items():
            n = int(round(42000 * share * rnd.uniform(.93, 1.07) * (1 - age * .035), -1))
            bal = int(round(n * 21500 * (1 - age * .07) * rnd.uniform(.97, 1.03), -3))
            peak = 1.35 if (y, cq) in [(2025, 1), (2025, 2), (2025, 3)] else 1.0
            ltv0 = LTV0[(y, cq)] + {"prime": -6, "near_prime": 0, "subprime": 5}[tier]
            cur = ltv0 - age * 2.2 + (4.5 if peak > 1 else 0)
            rows.append([f"{y}-Q{cq}", tier, n, bal, round(ltv0 + rnd.uniform(-1, 1), 1),
                         round(cur + rnd.uniform(-1, 1), 1), d30 * seasoning * peak, d60 * seasoning * peak])
    for col, target in [(6, target30), (7, target60)]:
        w = sum(r[2] * r[col] for r in rows) / sum(r[2] for r in rows)
        for r in rows: r[col] = round(r[col] * target / w, 2)
    write(f"{OUT}/loan-performance/Auto_Loan_Performance_{q}_2026.csv",
          ["origination_cohort", "credit_tier", "active_loans", "balance_outstanding_usd",
           "ltv_at_origination_pct", "current_ltv_pct", "dpd_30_plus_pct", "dpd_60_plus_pct"], rows)
print("ok")
