# Entering and changing transactions

The new UI assumes that transactions come from the bank import (rule `data-bank-import`). By hand
the user enters an expense or income, e.g. paid in cash, and corrects a transaction in its details.
Transfers and splits are not entered here: transfers between own accounts are recognized from the
import, a category answers one "Wofür?" and the item groups of a receipt cover the breakdown.

The diagram [`editing.puml`](editing.puml) shows the parts. Render it with the PlantUML plugin of
Android Studio or `java -jar plantuml.jar editing.puml`.

## One form, two places

`TransactionForm` holds all fields: account, expense or income, amount, payee, category, tags,
date, payment method and note. It is shown

- on a screen of its own for a new transaction, `NewTransactionFlow` in `NewTransactionActivity`
  of the shell, opened by the plus button of the main screen and for a receipt without a booking.
  "Save" closes it, "save and new" keeps account, sign, day and payment method for the next one.
- in the details of a transaction after a tap on the pencil, in `TransactionDetailSheet` of the
  shell. A transaction from the bank import starts with a warning; one with an amount in another
  currency keeps its amount.

Both use `TransactionEditViewModel`, which also saves the quick changes of the details (date,
payment method, note) and offers to undo them. What is typed is a `TransactionDraft` in the
`SavedStateHandle` (rule `vm-saved-state`), kept as typed so that an amount that cannot be read
yet survives. `toValues` in `:next:data:transactions` turns it into `EditValues`; these rules are
pure and tested there.

## What comes from elsewhere

- `TransactionStore`, by `RepositoryTransactionStore` of the shell: loads and saves on the
  database of the app. A change writes only the values that differ, so that a category set from
  its chip meanwhile stays. A payee typed by name is found or created, like in the classic form.
- `CategorySuggestions`, by `AnalysisCategorySuggestions` through `NextData.categorySuggestions`:
  the suggestion of `CategorySuggester` for the payee and note typed, instead of the payee autofill
  of the classic form. A certain suggestion, e.g. from a rule of the user, is taken and follows
  the payee until the user picks a category; another one is offered as a chip.
- The tags as a `TagField` slot, from `TagsFeature` of the shell (rule `mod-feature-isolated`).
- The category picker of the app, `LocalCategoryPicker` of `:next:core:ui`.

A new transaction starts in the account shown, or the open account used last, with the tags that
account gives its transactions; it follows them when the account is changed until the user changes
the tags. Closed accounts are not offered, and the database rejects a transaction in an account
closed meanwhile.
