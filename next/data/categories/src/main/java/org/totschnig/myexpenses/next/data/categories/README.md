# Categories

A category answers "Wofür?" (`data-category-tag`). This module suggests categories for
transactions without one and keeps what the user decided about them. The screens are in
`:next:feature:categories` (suggestions in the details, the backlog banner) and in the setup
(`:next:feature:setup`), the writing to the database in the shell (`ProviderCategoryWriting`).

## Who a transaction is with

`Counterpart` is the payee, or for a payment service like PayPal or Klarna the merchant named in
the purpose (`PaymentService`). Transactions with the same counterpart are categorized alike. A
payment service without merchant, e.g. Amazon, has no counterpart: its purchases are too different
to learn from.

## Suggestions

`CategorySuggester` learns from the transactions that have a category, credits and debits apart:

| Source | From | Certain |
| --- | --- | --- |
| `RULE` | a rule of the user ("Immer so machen") | always |
| `PAYEE` | earlier transactions of the counterpart, more than half with one category | from 5 with 90 % |
| `WORDS` | words of payee and purpose that led to a category before | never |
| `MERCHANT` | a well-known chain matched to a category of the user's tree by name | never |

`CategoryDecisions.plan` sorts the suggestions: certain ones for new transactions are applied
without asking (`CategoryRepository.appliedAutomatically`, while the app is shown and after an
import), certain ones for transactions from before the first analysis are the backlog, asked once,
the others are shown in the details.

## Decisions of the user

`CategoryDecisions`, kept by `CategoryStore` in the data store, which is part of the backup:

- `rules`: counterparts that always get a category; a counterpart is in one rule at most. One rule
  may be for cash withdrawals instead (`CategoryRule.isCash`, `withCashRule`): they follow it rather
  than the rule of their counterpart, e.g. the bank, which they never follow.
- `rejected`: a category the user took back from a transaction, which is not suggested again
- `automatic`: categories applied without asking, so that the details can say so
- `unclear`: counterparts the user knows no category for ("Weiß ich nicht"). Their transactions stay
  without category, get no suggestion, are not applied or asked in the backlog, and do not count as
  missing in the readiness of the data, the hints and the setup (`unclearIds`,
  `CategoryRepository.unclear`). A rule for the counterpart ends it, and the other way round.

`CategoryRepository.categorize` gives the transactions of a counterpart a category and, if asked,
a rule, and returns what it changed (`Categorization`) for `takeBack`, which removes the category
again where it is still set and restores the rule the counterpart had; `markUnclear` and
`unmarkUnclear` change the unclear counterparts. `categorizeCash` does the same for cash withdrawals
(`CashCategorization`).
