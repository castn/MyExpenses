# Guidance

The analyses of the new UI are only as good as the transactions: an expense without category is
missing from the spending by category, a purchase without scanned receipt tells nothing about what
was bought. This package tells the user what is missing and what it would bring, in the places
where it matters, instead of a tutorial at the first start, when there is nothing to show yet.

The diagram is the PlantUML source [`guidance.puml`](guidance.puml) next to this file.

## How well the transactions are described

`dataReadiness(ledger, receipts, today)` counts, as a pure function:

- the debits of the last 3 months that can have a category, those without one and those in a
  catch-all category like "Sonstiges" (`DataReadiness.CATCH_ALL`)
- the purchases at supermarket chains and those without scanned receipt
- the purchases with a scanned receipt in the windows of `ItemGroupRising`, and within the last year
- the debits of the last year with a tag

`DataReadiness.gaps` turns this into `DataGap`s, the most useful first. A gap needs at least
`MIN_DEBITS` debits, and categories count as missing only from `MIN_COUNT` debits and a share of
`MAX_SHARE`, so that a few exceptions do not nag. The thresholds of the saving tips are not copied:
the gaps use `ItemGroupRising.isRecent`, `isUsual`, `hasEnough` and `supermarketOf` of the tips, so
that what the gaps say cannot drift from what the tips need.

`DataReadinessViewModel` computes it with a `Memo` from the ledger and the `ReceiptFacts`.

## Where the user sees it

| Place | What | Closed |
| --- | --- | --- |
| Analysis tab | `ReadinessBox` (an `InfoBox`): one paragraph per gap, with a button that lists its transactions; their details offer category, suggestions and receipts | never, it goes away once nothing is missing |
| Saving tips | `PendingTipsSection`: tips that cannot say anything yet, without amounts, see `pendingTips()` | never, a tip that is there is not pending |
| Overview | `OverviewHint` (an `InviteCard`): the first gap, opens the analysis tab; not while the category backlog asks | for its topic |
| Details of a supermarket purchase without receipt | `ReceiptHint` (a `HintCard`), next to the chip that scans one | for its topic |
| After saving a transaction | snackbar about a missing category or receipt | for its topic, or after `HintTopic.MAX_AFTER_SAVE` times |
| Empty states | the empty transaction list, saving tips and receipt pots say what makes them useful | — |
| First start | the last page says that precise categories and receipts make the app useful | — |

## Hints

A hint has a `HintTopic`: categories or receipts. The user closes a topic, not a single hint, so
that a hint closed in the overview does not come back after saving. `HintStore` keeps the closed
topics and how often a hint was shown after saving in the data store, which is part of the backup.
"Show hints again" in the settings of the analysis resets both.

After saving, the editor returns the id of the transaction (`BaseActivity.onTransactionSaved`, handed
to `GuidanceFeature.onTransactionSaved` in the shell).
`HintsViewModel.afterSave` waits for the ledger that knows the change, at most a few seconds, since
the ledger shown before may still have the transaction as it was before editing. An unchanged ledger
is the same instance (see `Memo`), so a new instance is a new reading.

## Adding a gap or a hint

1. Count what is needed in `dataReadiness` and add a `DataGap` with a `key`, test it in
   `DataReadinessTest`. Reuse the thresholds of the feature that needs the data.
2. Add its text and action to `ReadinessBox` and its title to `gapTitle`.
3. If a saving tip waits for it, add a `PendingTip`.
4. If it deserves a hint, give it a `HintTopic` in `DataGap.topic`.
