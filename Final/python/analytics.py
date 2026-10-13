import sqlite3
import os
import datetime
from collections import defaultdict

"""
analytics.py
Queries database/expense_tracker.db for a given user_id and performs financial calculations.
"""

DB_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "database", "expense_tracker.db")

class FinancialAnalytics:
    def __init__(self, user_id, db_path=DB_PATH):
        self.user_id = user_id
        self.db_path = db_path

    def get_connection(self):
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def calculate_all(self):
        if not os.path.exists(self.db_path):
            return {
                "status": "error",
                "message": f"Database file not found at {self.db_path}"
            }

        try:
            with self.get_connection() as conn:
                cursor = conn.cursor()

                # 1. Total Income & Income List
                cursor.execute("SELECT amount, source, category, date FROM income WHERE user_id = ?", (self.user_id,))
                incomes = cursor.fetchall()
                total_income = sum(row["amount"] for row in incomes)

                # 2. Total Expenses & Expenses List
                cursor.execute("SELECT amount, description, category, payment_method, date FROM expenses WHERE user_id = ?", (self.user_id,))
                expenses = cursor.fetchall()
                total_expenses = sum(row["amount"] for row in expenses)

                net_savings = total_income - total_expenses

                # 3. Highest Expense
                highest_expense = max((row["amount"] for row in expenses), default=0.0)

                # 4. Category Breakdown
                category_totals = defaultdict(float)
                daily_totals = defaultdict(float)
                monthly_totals = defaultdict(float)

                for row in expenses:
                    cat = row["category"] or "Other"
                    amount = row["amount"]
                    date_str = row["date"]

                    category_totals[cat] += amount
                    if date_str:
                        daily_totals[date_str] += amount
                        month_key = date_str[:7] # YYYY-MM
                        monthly_totals[month_key] += amount

                # 5. Highest Spending Category
                highest_category = "None"
                highest_category_amount = 0.0
                for cat, total in category_totals.items():
                    if total > highest_category_amount:
                        highest_category_amount = total
                        highest_category = cat

                # 6. Average Daily Spending
                days_count = len(daily_totals)
                avg_daily_spending = total_expenses / days_count if days_count > 0 else 0.0

                # 7. Budget Usage overview
                cursor.execute("SELECT category, amount, month, year FROM budgets WHERE user_id = ?", (self.user_id,))
                budgets = cursor.fetchall()

                budget_summary = []
                current_month_str = datetime.date.today().strftime("%Y-%m")

                for b in budgets:
                    b_cat = b["category"]
                    b_amount = b["amount"]
                    b_month = b["month"]
                    b_year = b["year"]
                    month_key = f"{b_year:04d}-{b_month:02d}"

                    # Calculate spent in this month
                    spent = sum(row["amount"] for row in expenses if row["category"] == b_cat and row["date"].startswith(month_key))
                    pct = (spent / b_amount * 100.0) if b_amount > 0 else 0.0

                    budget_summary.append({
                        "category": b_cat,
                        "budget": b_amount,
                        "spent": spent,
                        "remaining": b_amount - spent,
                        "percentageUsed": round(pct, 1),
                        "month": b_month,
                        "year": b_year
                    })

                return {
                    "status": "success",
                    "userId": self.user_id,
                    "totalIncome": round(total_income, 2),
                    "totalExpenses": round(total_expenses, 2),
                    "netSavings": round(net_savings, 2),
                    "highestExpense": round(highest_expense, 2),
                    "highestCategory": highest_category,
                    "highestCategoryAmount": round(highest_category_amount, 2),
                    "averageDailySpending": round(avg_daily_spending, 2),
                    "categoryTotals": {k: round(v, 2) for k, v in category_totals.items()},
                    "monthlyTotals": {k: round(v, 2) for k, v in sorted(monthly_totals.items())},
                    "budgetSummary": budget_summary
                }

        except Exception as e:
            return {
                "status": "error",
                "message": f"Analytics calculation error: {str(e)}"
            }
