# Setup

After the first start and the import from the bank, most transactions have no category, and the
analyses cannot say much yet. The guided setup leads the user through a few decisions that make
them useful: first categories, by payee and the most money first, then the contracts detected. It
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
- Debits without counterpart, e.g. Amazon through a payment service, count, but cannot be decided
  on by payee.

A decision goes to `CategoryRepository.categorize`: the debits of the payee without category get
it, and with "Immer so machen" a rule gives it to later ones. "Weiß ich nicht" goes to
`CategoryRepository.markUnclear`.

`automaticShare(ledger, rules, categories, today)` tells what the step brings: of the debits of
the last three months, how many `CategorySuggester` would categorize without asking if they came
again.

## Contracts step

The contracts area may be left out (`feat-optional-area`). The shell hands in `SetupContracts`:
`Available` with the number of contracts detected and not confirmed, or `None`, and then there is
no contracts step (`SetupRepository.steps`).

## Time and progress

A step shows how long it takes: decisions × seconds per decision (`Pace`). Until the user made
`Pace.MIN_DECISIONS` decisions, `Pace.DEFAULT_SECONDS` stands for one, a placeholder until it is
measured by hand; then the measured pace of the user counts, each decision at most a minute.

`SetupStore` keeps the finished and skipped steps, whether the whole setup is closed, and the pace,
in the data store, which is part of the backup. `SetupProgress.next` is the step to go on with: the
first one not finished and not skipped, else a skipped one.

`SetupRepository` computes queue and share with a `Memo` each, from the ledger and the decisions
about categories, and runs once for the process (`NextData.setup`).
