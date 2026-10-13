import sys
import json
import argparse
from analytics import FinancialAnalytics

"""
reports.py
CLI entry point invoked by Java ReportManager process runner.
Prints calculated financial analytics JSON to standard output.
"""

def main():
    parser = argparse.ArgumentParser(description="Generate Financial Analysis Reports JSON")
    parser.add_argument("--user-id", type=int, required=True, help="User ID to generate reports for")

    args = parser.parse_args()

    analytics = FinancialAnalytics(user_id=args.user_id)
    report_data = analytics.calculate_all()

    # Print JSON output to stdout
    print(json.dumps(report_data))

if __name__ == "__main__":
    main()
