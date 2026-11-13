# Cash

Cash taken from the bank is no spending yet: it is spent later, in many small payments. In the
setup the user decides how to keep it (see `:next:data:setup`):

- as spending: cash withdrawals get a category, from then on by a rule for cash
  (`CategoryRule.isCash` in `:next:data:categories`)
- in a cash account: the user tells what it holds now, and from then on cash withdrawals are
  movements to it, while what is paid in cash is entered there. Withdrawals from before keep the
  category they got, so that no correction is booked.

`CashRepository` keeps the `CashAccount` with the day it was created (`CashStore`, in the data store,
which is part of the backup) and creates or deletes it through `CashWriting`, which the shell
implements with the provider of the app. Taking it back deletes the account only while nothing was
booked on it.
