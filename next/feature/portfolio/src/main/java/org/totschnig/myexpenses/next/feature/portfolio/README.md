# Trades of portfolios

A portfolio is an account with an account of its own for each asset it holds and one for its cash.
A trade buys or sells an asset, deposits or withdraws cash, or moves an asset to or from another
portfolio. The page of a portfolio lists its trades; a trade is entered and changed in a form of its
own. Prices are not entered here: a buy or sell stores its price, the latest one fills the form.

The diagram [`trades.puml`](trades.puml) shows the parts. Render it with the PlantUML plugin of
Android Studio or `java -jar plantuml.jar trades.puml`.

## The list

`PortfolioTradesFlow` shows the trades of one portfolio on its page of the main screen, the latest
first, without paging and without the filter of the classic page: a portfolio has few trades.
`PortfolioTradesViewModel` keeps one state per portfolio, as the pages of the pager share it. A
tap changes a trade, its actions change or delete it; deleting is asked for by the app
(`MainActivity.delete`), which also removes the other side of a transfer. A closed portfolio only
shows its trades.

## The form

`TradeForm` holds all fields: the kind, day and time, the asset with quantity and price, or the
amount of a deposit or withdrawal, the costs with their categories, where the money comes from or
goes to, and a note. It is shown by `TradeEditorFlow` in `TradeEditorActivity` of the shell, for a
new trade from the plus button of a portfolio and for changing one from its page or from the details
of its transaction (`editTrade` of `BaseMyExpenses`). "Save" closes it, for a new trade "save and
new" keeps kind, day, asset and funding for the next one.

- A trade that is changed keeps its kind of trade: a buy can become a sell, a deposit a withdrawal,
  a transfer stays one with its other portfolio.
- The value is quantity times price, rounded to the currency of the portfolio with the rounding mode
  kept per portfolio; it is offered only when the value has more decimals than the currency.
- What can be sold or withdrawn is shown below the quantity; while a trade is changed, what it moved
  out counts again.
- The costs start with the categories of the costs of the trade saved last.
- Funded from an account of the app, the transactions of that account with the same amount on the
  same day are offered to be linked as the other side, e.g. the debit from the bank import; the only
  one is linked at once (rule `data-bank-import`).
- A security, crypto currency or commodity the app does not know yet is added from the choice of the
  asset (`AssetDialog`).

`TradeEditViewModel` keeps what is typed as a `TradeDraft` in the `SavedStateHandle` (rule
`vm-saved-state`), as typed so that a number that cannot be read yet survives. The rules (`figures`,
`withKind`, `withAsset`, `toValues`, `matchQuery`, `linkAfter`) are pure and tested in
`:next:data:portfolio`.

## What comes from elsewhere

- `TradeStore`, by `RepositoryTradeStore` of the shell: the portfolios with their balances and latest
  prices as the main screen computes them, the trades read with `loadTrades`, and saving with
  `TradeSaver` of :myExpenses, whose rules are pinned in its trade tests.
- The category picker of the app, `LocalCategoryPicker` of `:next:core:ui`, for the costs.

Setting up a portfolio, importing trades and the history of prices are still the classic dialogs and
screen of :myExpenses.
