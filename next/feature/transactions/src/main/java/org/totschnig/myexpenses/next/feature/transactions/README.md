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

- on a screen of its own, `TransactionEditorFlow` in `TransactionEditorActivity` of the shell: for
  a new transaction from the plus button of the main screen, a receipt without a booking, the
  widget, the launcher shortcut and a scanned receipt of the old OCR; and for changing one from
  "Edit" in the menus of lists without the pencil and the old details dialog. :myExpenses does
  not know the shell and opens it through `TransactionEditorEntry` (`newTransactionIntent`,
  `editTransactionIntent`), like the main screen. "Save" closes it, for a new transaction "save
  and new" keeps account, sign, day and payment method for the next one.
- in the details of a transaction after a tap on the pencil, in `TransactionDetailSheet` of the
  shell. A transaction from the bank import starts with a warning; one with an amount in another
  currency keeps its amount.

Splits and transfers are only shown, with their parts or the other account, and can be deleted,
ungrouped or unlinked; `canBeEdited` of `TransactionEditorEntry` tells which transactions the form
takes. Clone is gone; manual transfers and splits are no longer offered.

Both use `TransactionEditViewModel`, which also saves the quick changes of the details (date,
payment method, note) and offers to undo them. What is typed is a `TransactionDraft` in the
`SavedStateHandle` (rule `vm-saved-state`), kept as typed so that an amount that cannot be read
yet survives. `toValues` in `:next:data:transactions` turns it into `EditValues`; these rules are
pure and tested there.

## Templates

A template holds what is entered often, e.g. the rent paid in cash. It is entered in the same
`TransactionForm`: the draft carries `TemplateFields` (title, "save without asking") and has no
day. `TemplatesFlow` in `TemplatesActivity` of the shell lists them, the ones used most first, and
holds their form on its own back stack. A tap saves a transaction dated today at once, if the
template says so, or opens `TransactionEditorActivity` filled with it; the other way is in the
actions of the row. The form of a new transaction offers the templates used most as chips; a chip
fills the draft but keeps its day, and saving counts as a use of the template.

- Templates of the classic form that are a split or a transfer are listed, but can only be saved
  at once (`instantiateTemplate` of the app) and deleted.
- "Create template" in the menu of a transaction opens the form of a new template with its
  values; a split or a transfer is saved as a template at once.
- The template widget and pinned shortcuts of :myExpenses open these screens through
  `TemplateScreens`, which `:app` hands in. Saving at once from them stays in :myExpenses.

The rules (`toDraft`, `withTemplate`, `toTemplateValues`, `forChips`) are pure and tested in
`:next:data:transactions`; `TemplateStore` is implemented by `RepositoryTemplateStore` of the shell.

## What comes from elsewhere

- `TransactionStore`, by `RepositoryTransactionStore` of the shell: loads and saves on the
  database of the app. A change writes only the values that differ, so that a category set from
  its chip meanwhile stays. A payee typed by name is found or created, like the classic form did
  (pinned in `TransactionSaveRulesTest` of :myExpenses).
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
