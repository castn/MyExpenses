# Money flow

The analysis tab shows where the money of the last twelve months came from and where it went:
earned, spent and left, and a card that opens the money flow as Sankey diagram. There the user
picks twelve months, a month or a year, and the flow runs from left to right:

incomes → accounts → categories (and what is left) → payees

The diagram [`moneyflow.puml`](moneyflow.puml) shows the way from the ledger to the screen.
Render it with the PlantUML plugin of Android Studio or `java -jar plantuml.jar moneyflow.puml`.

## What counts

The money flow counts like the spending by category, with the same function `countedParts`:
movements between own accounts and transactions excluded from spending (e.g. reimbursed ones,
see `TagFacts`) do not count, split transactions count with their parts, amounts are in the home
currency. Nothing is imported or cleaned up for it, all it needs is in the ledger already:

- The **category** is the main category. Each main category is a group, the transactions without
  category are two, their debits and their credits.
- The **side** of a group is its net amount. A category for expenses with more returns than
  purchases is an income, a category for income with more debits than credits an expense. So
  each side is complete and earned minus spent is what is left.
- The **actor** of a transaction is the merchant if the payee is a known one, e.g. "McDonald's"
  for "MCDONALDS 1234 DARMSTADT", see `Merchants`, else the payee.
- At most `MAX_INCOMES` incomes and `MAX_EXPENSES` expenses, the smaller ones are summed up into
  "Others"; per category at most `MAX_ACTORS` actors with "n more". Actors with more returns than
  payments go into the rest, so the actors never add up to more than their category.
- If more was spent than earned, the difference comes "from savings" before the accounts,
  otherwise what is "left" is a node next to the categories.

## Colors

Incomes are green. A category keeps its color in all periods: the categories with the most
spending over the whole ledger take the colors of `ChartPalette.withoutGreen` in this order,
others in a period take a free color, the rest is neutral. The payees of a category share its
color. The palette is checked for color vision deficiencies; three of its light colors are below
3:1 against the surface, which is why every node large enough is named directly.

## The diagram

`SankeyChart` in `:charts` only knows nodes, flows and labels. `SankeyLayout` places the nodes by
column, each as high as what flows through it, with one scale for all columns, and finds what a
tap hits. `Viewport` keeps the zoom between 1 and 6 and the chart inside its view.

- Two fingers zoom, one finger moves the zoomed chart, a double tap shows it whole. Unzoomed, one
  finger scrolls the page.
- Nodes keep their width and labels their size when zoomed. A node is named once it is high
  enough for its label, so zooming in names the smaller payees.
- A tap on a node or band highlights it with what it connects and shows above the diagram what it
  stands for, e.g. "Eating out → McDonald's", 20 transactions, 186 €, with its transactions.
- Screen readers read each node with its value and can select it.
