# Budget and pots

The budget of the new UI shares out the money of a period. At the top is the **frame** (shown as
"Spielraum" in the German UI), taken from the monthly balance: what comes in, what is fixed
(contracts and reserves), what is planned in pots and what is still free. Below it are the
**pots** ("Töpfe"), e.g. Täglicher Bedarf, Ausgehen or Geschenke.

The diagram [`budgets.puml`](budgets.puml) shows where the figures come from. Render it with the
PlantUML plugin of Android Studio or `java -jar plantuml.jar budgets.puml`.

The budgets of the classic screens (tables `budgets` and `budget_allocations`) are not touched.
The budget page offers once to take them over as pots (`LegacyBudgets`): one pot per category with
an amount of its own, or one pot for a budget without such amounts. Which accounts a budget was
for is not taken over, and budgets in other currencies are left out.

## Kinds of pots

| Kind | Example | What it holds |
| --- | --- | --- |
| `RUNNING` | Ausgehen 150 € per period | the same amount each period, what is left can be carried over |
| `YEARLY` | Geschenke 600 € per year | a twelfth is added each month, it is spent now and then |
| `GOAL` | Neues Spiel 70 € until December | saved through reserves or money moved to own accounts |
| `PROJECT` | Urlaub Italien 1500 € | a total for a group (tag), optionally between two days |
| `ITEM_GROUP` | Süßes at most 10 % | a share of the scanned purchases, it only watches |

A period runs from salary to salary like the monthly balance, else it is the calendar month
(`budgetPeriods`).

## Which pot a transaction is paid from

`PotMatcher` puts each transaction, or each part of a split transaction, into at most one pot,
so that nothing counts twice:

1. A pot for a purpose (goal, project) comes first: what is spent on a trip is paid from the trip.
2. Otherwise the closest match: contract, then payee, then group (tag), then the deepest category.
   On equal terms the pot listed first.
3. A payment of a contract only belongs to a pot that lists the contract, e.g. the gym in Fitness.
   Other contracts are fixed costs and in no pot.
4. Movements between own accounts are in no pot.

Reimbursed transactions count nowhere. Purchases marked as one-off do not count in running pots,
which are for everyday spending; the detail of the pot lists them apart. They count in all other
pots and always in the frame.

## Computing

`BudgetAnalyzer.analyse` is a pure function of a `BudgetInput` (ledger, pots, categories,
contracts, salaries, tag meanings, receipts, today, period). `BudgetViewModel` keeps one `Memo`
for the current period (overview, facts, details of a transaction) and one for the period picked
on the budget page. Receipts are only part of the input if there is a pot of item groups.

- The frame comes from `MonthlyBalance.compute`. Contracts of pots are fixed costs already, so a
  pot only plans its part without contracts (Fitness 50 € with a gym of 35 € plans 15 €).
- "Still free" is the money available after the monthly balance, less what running pots can still
  spend and the shares of yearly pots and goals not spent from yet in the period.
- The pace of a running pot is projected from what it spent without contracts so far, after
  five days of the period.

`PotSuggestions` suggests the amount of a new pot from what it would have held in the periods
before (median of the last three for running pots, the last twelve for yearly ones), and contracts
whose category it holds. `PotTemplate` finds the categories of a template in the categories of the
user by words in German and English.

## Who reads the budget

`BudgetFacts` (the pots of the current period with their earlier periods) are read by the saving
tips, which name pots overspent in three of their last four periods (`PotsOverspent`).

The overview shows the pots as rings with what is still free and the pot that will be overspent
most at this pace. The details of a transaction show the pot it is paid from (`PotSection`).

Creating a pot goes through the licence check of the budget feature (`ContribFeature.BUDGET`),
like a classic budget.

## Storage

`BudgetStore` keeps the pots as a JSON array in the DataStore (`next_budget_pots`), which is part of
the backup but not synced. Ids are UUIDs, so they stay the same when a pot is renamed.
