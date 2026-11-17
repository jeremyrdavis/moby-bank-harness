import csv, sys, collections as C
D = sys.argv[1]
def rd(p): return list(csv.DictReader(open(f"{D}/{p}")))
SEG = ["SUV", "Truck", "Sedan", "Electric"]
for q in ["Q2", "Q3"]:
    print(f"===== {q}")
    s = rd(f"used-vehicle-sales/Used_Vehicle_Sales_{q}_2026.csv")
    tot = sum(int(r["units_sold"]) for r in s)
    avgp = sum(int(r["units_sold"]) * int(r["avg_retail_price_usd"]) for r in s) / tot
    print("units", tot, "avg price %.0f" % avgp)
    m = C.Counter(); 
    for r in s: m[r["month"]] += int(r["units_sold"])
    print("by month", dict(m))
    segu = {}
    for g in SEG:
        rs = [r for r in s if r["segment"] == g]; u = sum(int(r["units_sold"]) for r in rs)
        segu[g] = u
        print(" ", g, u, "%.0f" % (sum(int(r["units_sold"]) * int(r["avg_retail_price_usd"]) for r in rs) / u))
    reg = C.Counter()
    for r in s: reg[r["region"]] += int(r["units_sold"])
    print("by region", dict(reg))
    w = rd(f"wholesale-pricing/Wholesale_Price_Index_{q}_2026.csv")
    weeks = sorted({r["week_ending"] for r in w}); first, last = weeks[0], weeks[-1]
    comp0 = comp1 = 0
    for g in SEG:
        a = float(next(r for r in w if r["week_ending"] == first and r["segment"] == g)["wholesale_index"])
        b_row = next(r for r in w if r["week_ending"] == last and r["segment"] == g)
        b = float(b_row["wholesale_index"]); ya = float(b_row["wholesale_index_year_ago"])
        comp0 += a * segu[g]; comp1 += b * segu[g]
        print("  wholesale", g, a, "->", b, "QoQ %.1f%%" % (100 * (b / a - 1)), "YoY %.1f%%" % (100 * (b / ya - 1)), "auction $", b_row["avg_auction_price_usd"])
    print("  wholesale composite QoQ %.1f%%" % (100 * (comp1 / comp0 - 1)), "baseline", first, "close", last)
    d = rd(f"inventory-and-days-to-turn/Days_To_Turn_By_Segment_{q}_2026.csv")
    tu = sum(int(r["units_sold"]) for r in d)
    print("  days overall %.1f" % (sum(int(r["units_sold"]) * float(r["avg_days_to_turn"]) for r in d) / tu))
    for g in SEG:
        rs = [r for r in d if r["segment"] == g]; u = sum(int(r["units_sold"]) for r in rs); st = sum(int(r["units_in_stock"]) for r in rs)
        print("  days", g, "%.1f" % (sum(int(r["units_sold"]) * float(r["avg_days_to_turn"]) for r in rs) / u),
              "over60 %.1f%%" % (sum(int(r["units_in_stock"]) * float(r["pct_stock_over_60_days"]) for r in rs) / st), "stock", st)
    slow = sorted(d, key=lambda r: -float(r["avg_days_to_turn"]))[:3]
    print("  slowest", [(r["segment"], r["region"], r["avg_days_to_turn"]) for r in slow])
    allst = sum(int(r["units_in_stock"]) for r in d)
    print("  over60 overall %.1f%%" % (sum(int(r["units_in_stock"]) * float(r["pct_stock_over_60_days"]) for r in d) / allst))
    l = rd(f"loan-performance/Auto_Loan_Performance_{q}_2026.csv")
    n = sum(int(r["active_loans"]) for r in l)
    f = lambda col, rs: sum(int(r["active_loans"]) * float(r[col]) for r in rs) / sum(int(r["active_loans"]) for r in rs)
    print("  loans", n, "bal %.2fB" % (sum(int(r["balance_outstanding_usd"]) for r in l) / 1e9), "30+ %.2f 60+ %.2f" % (f("dpd_30_plus_pct", l), f("dpd_60_plus_pct", l)))
    for t in ["prime", "near_prime", "subprime"]:
        rs = [r for r in l if r["credit_tier"] == t]; print("   ", t, "30+ %.2f 60+ %.2f" % (f("dpd_30_plus_pct", rs), f("dpd_60_plus_pct", rs)))
    for co in sorted({r["origination_cohort"] for r in l}):
        rs = [r for r in l if r["origination_cohort"] == co]
        print("   ", co, "30+ %.2f" % f("dpd_30_plus_pct", rs), "curLTV %.1f" % f("current_ltv_pct", rs), "share %.1f%%" % (100*sum(int(r["active_loans"]) for r in rs)/n))
    uw = [r for r in l if float(r["current_ltv_pct"]) > 100]
    print("   current LTV>100 rows:", [(r["origination_cohort"], r["credit_tier"], r["current_ltv_pct"]) for r in uw])
a = rd("wholesale-pricing/Auction_Volumes_Q3_2026.csv")
for g in SEG:
    rs = [r for r in a if r["segment"] == g]
    print("auction", g, "conv %.1f%%" % (100 * sum(int(r["units_sold"]) for r in rs) / sum(int(r["units_offered"]) for r in rs)),
          "first4 %.1f last4 %.1f" % tuple(100 * sum(int(r["units_sold"]) for r in x) / sum(int(r["units_offered"]) for r in x) for x in (rs[:4], rs[-4:])))
