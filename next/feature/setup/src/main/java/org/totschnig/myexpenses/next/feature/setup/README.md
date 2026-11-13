# Setup

The screens of the guided setup after the first start. The logic is in `:next:data:setup` (see its
README): which steps there are, which payees come first, how much of the money has a category,
time estimates, progress.

## Pages

`SetupFlow` is the single entry, with its own back stack (`SetupKey`):

| Page | What it shows |
| --- | --- |
| `Start` | what the setup brings, the steps with what they take (`GuideStepRow`), "Get started" or "Go on", "Skip setup" closes it for good |
| `Income` | the regular incomes to confirm and the choice of the salary the shell hands in (`incomes`), finish or skip; only if incomes were found |
| `Categories` | `GuideProgressHeader` with the share of the money with a category, a mark at 80 % and the time left; first the backlog question (`Banner`), then one payee after the other (`DecisionCard`), at the target "Next" or "Go on" |
| `Transactions` | the debits without category of one payee, through the `FixedTransactions` of the shell (`SetupTransactions`); it leaves once they all have a category |
| `Differs` | the debits without category of one payee with checkboxes ("Different"), to give those that belong together a category without a rule; it leaves once they all have one |
| `Automatic` | how many of the recent debits now get their category without asking |
| `Contracts` | the suggestions of contracts the shell hands in (`contracts`), finish or skip; `Income` and `Contracts` share `SetupSectionPage` |
| `Done` | back to the overview; the setup is closed if no step is left |

A step that is over leaves the back stack, so that back returns to the overview of the steps.

## A payee

The card shows the payee, the money spent there without category, since when, and its last three
debits with the purposes from the bank (`EntryPreview`), which tell e.g. the merchant behind a
direct debit. With a suggestion "Use" takes it and "Other category" opens the category picker,
else "Choose category" does. "Also for later transactions" adds a rule ("Immer so machen"), on at
first. "Show all 7 transactions" opens the debits without category with their details.

- "Don't know" marks the payee as unclear (`CategoryDecisions.unclear`): its money no longer counts
  and nothing asks about it again.
- "Later" puts the payee off until the others are done; "Show again" brings it back.
- "Change last decision" takes back the last decision of the visit (`SetupRepository.takeBack`)
  and shows its payee first again, with the category chosen before as the suggestion. Pressed
  again, it goes further back.

## Cash

Cash withdrawals are not shown with their payee but on a card of their own (`CashDecision`, `CashCard`),
where their money puts them among the payees: the money taken, about how much a month and since
when. The user keeps them in a new cash account, telling what it holds now, or counts them as
spending; the withdrawals so far get the category on the card either way, suggested if possible
("Bargeldabhebung"). Without a way to create a cash account, or with one already, only the category
is asked. "Later" and "Change last decision" work as for a payee (`CashActions`).

## State

`SetupViewModel` implements `SetupActions` and `CashActions`. `setupOverview` and `categoriesStep` are pure functions
of the repository's flows and the visit:

- Payees decided on in this visit are hidden at once and their money counted in, since the ledger
  knows their categories only a moment later.
- The visit (`SetupVisit`: decided, unclear and put off payees, going on after the target, the
  payee gone back to), the decisions to go back to, "always" and the payee waiting for the
  category picker are in the `SavedStateHandle`, so that the picker can return to a new process.
- Decisions are written and taken back one after the other (a `Mutex`), so that going back right
  after a decision finds it.
- The time of a decision runs from `cardShown` to the decision and goes to `SetupStore.recordDecision`
  (`Pace`); a payee gone back to does not count twice. `clock` is a parameter for tests.

## On the overview

`SetupCard` shows what `SetupViewModel.offer` says: until the setup was opened the big
`ActionCard` with "Skip setup", while the overview shows nothing else (`takesOverOverview`); then
an `InviteCard` with the steps left, the time and a ring of the steps done above budgets and
balance. Its close button puts it away (`SetupCardActions.dismissCard`) or, after the longest
pause, asks whether to stop reminding (`ConfirmDialog`). Opening the setup marks it as opened
(`SetupCardActions.opened`). The shell adds "Resume setup" and "Restart setup" to the insights
settings (`SetupSettings`).
