# Debts

Money lent to or borrowed from a person, and the transactions that pay it back. The rules are in
`:next:data:debts` (`Debts.kt`: balance, conversion of other currencies, what a payment does;
`DebtBook.kt`: overview, history, form), the app is reached through `DebtDatabase` (reading and
linking) and `DebtWriting` (entering, closing, deleting), implemented by the shell in
`next/debts/RepositoryDebtDatabase`.

## In the details of a transaction

`DebtsViewModel` with `DebtSection` and `DebtChip`, see `debtChoice`: a linked transaction shows
its debt with the balance before and after it and can be unlinked unless the debt is closed; a
transaction of a payee with open debts in the currency of its account or the home currency offers
them. Splits, their parts and transfers are left out.

## Pages

`DebtsFlow`, opened by the shell from the card on the overview (`DebtOverviewCard`) and from the
menu item "Debts", with its own back stack (`DebtKey`):

- the overview, sorted by person (grouped), name or amount, open debts only unless all are shown
  (`DebtOverviewStore`), with the total of the open debts in the home currency
- a debt with its history: the debt itself, then each payment with what is left after it; edit,
  close or reopen, share as text, delete (its transactions stay)
- the form: with whom (an unknown name creates the payee), name, who owes whom, amount, currency
  (only for a new debt), its value in the home currency for another currency, date, note; the
  draft lives in the `SavedStateHandle`

`DebtBookViewModel` holds the pages. Unlike the classic screens, voided transactions do not count
and payments in a third currency are converted at the rate of the debt.
