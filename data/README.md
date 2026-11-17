# Demo data

Fictional used-vehicle data for the moby-cars demo. **None of it is real.**

`onedrive/` stands in for the analyst's OneDrive when the backend runs with `harness.documents.mode=fake` (the default).
Each sub-folder is one folder of the demo library (`DemoData.java` lists the folders and files; the folder's directory is
its name in lower case with dashes). The build copies `onedrive/` onto the classpath (`copy-demo-data` in
`moby-bank-quarkus/pom.xml`), and attaching a file in the app sends its real contents to the agent's sandbox, so a real
agent can build a genuine Q3 report with no OneDrive.

| Folder | Files | What is in them |
|---|---|---|
| `used-vehicle-sales/` | `Used_Vehicle_Sales_Q2_2026.csv`, `…_Q3_2026.csv` | Units sold and average retail price, mileage and age by month, region and segment |
| `wholesale-pricing/` | `Wholesale_Price_Index_Q2_2026.csv`, `…_Q3_2026.csv`, `Auction_Volumes_Q3_2026.csv` | Weekly wholesale index (base Jan 2020 = 100) with its year-ago value and the average auction price; weekly auction volumes and conversion. Each index file starts with the prior quarter's closing week as the baseline |
| `inventory-and-days-to-turn/` | `Days_To_Turn_By_Segment_Q2_2026.csv`, `…_Q3_2026.csv` | Days to turn, units in stock and the share of stock older than 60 days, by segment and region |
| `loan-performance/` | `Auto_Loan_Performance_Q2_2026.csv`, `…_Q3_2026.csv` | Active loans, balance, loan-to-value and 30+/60+ day delinquency by origination cohort and credit tier |
| `quarterly-reports/` | `Used_Car_Quarterly_Report_Q2_2026.md` | The finished Q2 report (the seeded conversation in the app is its history) |
| `analyst-templates/` | `Quarterly_Report_Template.md` | The report outline and method notes |

The Q2 and Q3 files are consistent with each other (the Q3 wholesale baseline is the Q2 close), so a Q3 report can be
compared with the Q2 one.

`scripts/generate_demo_data.py` regenerates every CSV deterministically (`python3 scripts/generate_demo_data.py onedrive`
from this folder), and `scripts/analyze_demo_data.py onedrive` prints the figures the reports and the seeded conversations
quote. If you change the data, rerun the analysis and update `DemoData.java`, `FakeSandboxAgent.java` and the Q2 report.
