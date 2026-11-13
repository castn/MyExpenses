# Setup

After the first start and the import from the bank, most transactions have no category, and the
analyses cannot say much yet. The guided setup leads the user through a few decisions that make
them useful: first the regular incomes and the salary, then categories, by payee and the most money
first, then the contracts detected. It
is started from a card on the overview, never on its own, and every step and the whole setup can
be skipped.

This module has the logic; the screens are in `:next:feature:setup`. The diagram is the PlantUML
source [`setup.puml`](setup.puml) next to this file.

## Categories step

`categoryQueue(ledger, unclear)` is a pure function:

- It looks at the debits that can have a category over the whole ledger, in the home currency.
- `CategoryCoverage` says how much of the money spent has a category. Money of payees the user
  knows no category for ("Weiß ich nicht", `CategoryDecisions.unclear`) does not count.
- The payees whose debits have no category are sorted by the money spent there, so that rent and
  the supermarket come first. They are grouped by `Counterpart`, as the suggestions of categories
  are, so that PayPal purchases are decided on per merchant.
- `required` are the payees that bring the coverage to `TARGET_SHARE` (80 %), usually 20 to 30
  decisions. `optional` are the others, for users who want to come closer to all of the money.
- A `SetupPayee` tells since when it is paid and its debits without category (`transactions`);
  the last three (`recent`) have purposes that help to tell what the payee is, and `suggestionFor`
  gives the category most of its debits are suggested.
- Cash withdrawals (`isCashWithdrawal` of `:next:core:model`, by the booking text of the bank or
  the purpose) are taken out of their payee, e.g. the own bank, and decided on once for all of
  them (`SetupCash`). They count towards the target like a payee: `isCashRequired` says whether
  they are needed for it.
- The cash withdrawals are kept as spending, with a rule for cash for later ones, or in a cash
  account (`keepCash`, `:next:data:cash`): the user tells what it holds now, and the withdrawals so
  far keep the category they get, without a correction booking. `suggestionFor(cash, …)` offers the
  category most of them are suggested, else one named for cash, e.g. "Bargeldabhebung".
  With a cash account, withdrawals from the day it was created are left out (`cashSince`): they
  become transfers to it, and deciding on older ones sets no rule for cash.
- Debits without counterpart, e.g. Amazon through a payment service, count, but cannot be decided
  on by payee.

Before the payees the step asks once about the backlog (`SetupRepository.backlog`, `answerBacklog`):
the transactions from before the suggestions started that can be categorized for sure.

A decision goes to `CategoryRepository.categorize`: the debits of the payee without category get
it, and with "Immer so machen" a rule gives it to later ones. "Weiß ich nicht" goes to
`CategoryRepository.markUnclear`. When the debits of a payee are for different things ("Unterschiedlich"),
`assign` gives only the chosen ones a category, without a rule. `SetupRepository` returns each as a `SetupDecision`, which `takeBack`
undoes: the debits lose the category again, if they still have it, the rule the payee had before
comes back (`Categorization`), and an unclear payee is asked about again.

`automaticShare(ledger, rules, categories, today)` tells what the step brings: of the debits of
the last three months, how many `CategorySuggester` would categorize without asking if they came
again.

## Income and contracts steps

The contracts area may be left out (`feat-optional-area`). The shell hands in `SetupContracts`:
`Available` with the number of contracts detected and not confirmed, of regular incomes detected
and not confirmed (`incomeSuggestions`) and of those confirmed (`incomes`), or `None`, and then
there is neither step (`SetupRepository.steps`). The income step comes first, and only if there are
incomes (`hasIncomes`): one decision per income to confirm and one for the salary
(`incomeDecisions`), since the monthly balance runs from one salary to the next. The screens of
both steps come from the contracts area as well.

## Time and progress

A step shows how long it takes: decisions × seconds per decision (`Pace`). Until the user made
`Pace.MIN_DECISIONS` decisions, `Pace.DEFAULT_SECONDS` stands for one, a placeholder until it is
measured by hand; then the measured pace of the user counts, each decision at most a minute.

`SetupStore` keeps the finished and skipped steps, whether the whole setup is closed, and the pace,
in the data store, which is part of the backup. `SetupProgress.next` is the step to go on with: the
first one not finished and not skipped, else a skipped one.

`SetupRepository` computes queue and share with a `Memo` each, from the ledger and the decisions
about categories, and runs once for the process (`NextData.setup`).
