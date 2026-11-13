# Setup

The screens of the guided setup after the first start. The logic is in `:next:data:setup` (see its
README): which payees come first, how much of the money has a category, time estimates, progress.

## Pages

`SetupFlow` is the single entry, with its own back stack (`SetupKey`):

| Page | What it shows |
| --- | --- |
| `Start` | what the setup brings, the steps with what they take (`GuideStepRow`), "Get started" or "Go on", "Skip setup" closes it for good |
| `Categories` | `GuideProgressHeader` with the share of the money with a category, a mark at 80 % and the time left; first the backlog question (`Banner`), then one payee after the other (`DecisionCard`), at the target "Next" or "Go on" |
| `Automatic` | how many of the recent debits now get their category without asking |
| `Contracts` | the suggestions of contracts the shell hands in (`contracts`), finish or skip |
| `Done` | back to the overview; the setup is closed if no step is left |

A step that is over leaves the back stack, so that back returns to the overview of the steps.

## A payee

The card shows the payee, the money spent there without category, since when, and the last
purpose from the bank (`QuotedText`), which tells e.g. the merchant behind a direct debit. With a
suggestion "Use" takes it and "Other category" opens the category picker, else "Choose category"
does. "Also for later transactions" adds a rule ("Immer so machen"), on at first.

- "Don't know" marks the payee as unclear (`CategoryDecisions.unclear`): its money no longer counts
  and nothing asks about it again.
- "Later" puts the payee off until the others are done; "Show again" brings it back.
- "Change last decision" takes back the last decision of the visit (`SetupRepository.takeBack`)
  and shows its payee first again, with the category chosen before as the suggestion. Pressed
  again, it goes further back.

## State

`SetupViewModel` implements `SetupActions`. `setupOverview` and `categoriesStep` are pure functions
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
