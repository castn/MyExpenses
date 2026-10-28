# Reading till receipts

`ReceiptParser.parse()` reads merchant, items, total, number of articles, date and payment from
the text ML Kit recognized on the photo of a till receipt. It works in four layers, each in its own file,
see the table below. The diagrams are PlantUML sources next to this file:

- [`layers.puml`](layers.puml): the layers and what each of them uses
- [`parse.puml`](parse.puml): what `parse()` does, from the recognized lines to the items and the total

Render them with the PlantUML plugin of Android Studio or `java -jar plantuml.jar *.puml`.

| Layer | File | Knows about language | Knows about recognition errors |
| --- | --- | --- | --- |
| Rows from the positions of the lines, tilt, price columns | `ReceiptLayout.kt` | no | no |
| Splitting a row into tokens | `ReceiptTokens.kt` | units and currency, from the vocabulary | yes, all of them |
| Kinds of rows | `RowKinds.kt` | no | no |
| Items from rows | `ReceiptAssembler.kt` | subtotal words, from the vocabulary | no |
| Total, discounts after it, number of articles, payment | `ReceiptParser.kt` | total, payment, card and cash words, from the vocabulary | no |
| Date of the purchase | `ReceiptDates.kt` | names of the months, from the vocabulary | spaces around separators |
| Words of one language | `ReceiptVocabulary.kt` | yes | misread keywords |

Regular expressions are only used in `ReceiptTokens` to recognize single pieces, never whole rows.

## Tokens

`ReceiptTokens.tokens("0, 750 kg x 1,79 EUR/kg")` gives
`Number(0.750) Unit(KG) Times Number(1.79) Currency PerUnit(KG)`.

| Token | Examples as recognized |
| --- | --- |
| `Number` | `2`, `1,79`, `0, 750` (space added by recognition), `1.640`, `-0,30`, `0,30-` |
| `Currency` | `€`, `EUR`, `e` after an amount, `E` before one (`E6,12`) |
| `Times` | `x`, `X`, `×`, `*`, also doubled, `x x` |
| `Unit` | `kg`, `Stk`, `St`, `Stück`, and misread, `k9`, `Gtúck`, `5tuck` |
| `PerUnit` | `/kg`, `€/kg`, `EUR / kg` |
| `Word` | everything else: words of names, tax classes, `200g`, `0,75L` |

Recognition errors the lexer evens out: numbers split at a space (`2, 22`), signs glued to numbers
(`€0,99`, `3,79A`, `6x`, `x2`, `17.90.A`), stray marks (`9,99.`, `i3,12`, `9,99'`).
A token keeps its position in the row, so that names are cut from the row as recognized.

## Kinds of rows

| Kind | Function in `RowKinds` | Examples as recognized |
| --- | --- | --- |
| Price at the end of a row | `price` | `KARTOFFELN 2, 22 B`, `Burek Käse 4,50 €`, `309172 1 3,79 3,79A`, `Mivolis Wundspray 3,15 1 1` (dm), `GEIG.PRISECCO0,75L 10,998` |
| Quantity row | `quantity` | `3 Stk x 0,59`, `0, 750 kg x 1,79 EUR/kg`, `2 Stk x x 1, 19`, `4 X 0.99`, without sign only with unit and price per unit, `0,764 kg 1,29 EUR/kg` |
| Count and unit price after the name | `inlineQuantity` | `MEGGLE BAGUET 1,32 € x X 3`, `G&G HASELNUSS 2,99 € x2`, `BAGUETTE 2 Stk x 1,19` |
| Second row of an item, before its price | `detail` | `1,00 Stück x 3,65`, `309172 1 3,79`, `1.640 14.90`, `2 x €0,99` |
| Article number only, name below | `isArticleNumberOnly` | `2945139` |
| Number of articles | `itemCount` | `7 Artikel`, `Posten: 9`, `Artikel 8`, `5 Pos`, `SUMME [5]` |
| Amount only | `isAmountOnly` | `EUR 12, 15` |

## Rules for items

`ReceiptAssembler` gives each row to the first rule that takes it:

1. **Name below an article number**: rows without price after `2945139 48,89 b` are its name
   (electronics stores).
2. **Subtotal**: `Zw.Summe 43,10` is no item.
3. **Quantity row**: `3 Stk x 0,59` goes to the item above if count × unit price is its amount
   (REWE), otherwise to the item below (Aldi, Netto).
4. **Second row**: `1,00 Stück x 3,65 3,65 B` with the name in the row above (bakery, DIY store,
   butcher).
5. **Item row**: `KARTOFFELN 2, 22 B`, also with count and unit price after the name.
6. **Name only**: a name without price waits for its second row (rule 4).

`ReceiptParser` finds the total before: the total row (`SUMME`, also misread as `SUNME` or `Sunne`),
otherwise the payment (`Geg. EC-Cash`). Discounts printed after the total are items, too.

After the total it reads how the receipt was paid: a card row (`Geg. EC-Cash 23,83`,
`Kartenzahlung` with the amount below) gives the amount the bank books, which differs from the
total by cash back or coupons; a cash row (`BAR`, `Rückgeld`) marks a cash payment. The date is
the first valid one anywhere on the receipt (`11.10.26`, `11.10.2026`, `03 Aug. 2026`).

Rows among the items the assembler took nothing from are `leftoverRows` of the result, from the
first item on, without the number of articles. The check of a receipt lists them, so that a missed
item is taken by a tap; `ReceiptParser.itemOf()` reads the item from one or several such rows.

## Changing the parser

The tests in `next/data/receipts/src/test/java/org/totschnig/myexpenses/next/data/receipts` guard every
change. Those on real receipts need their fixtures, which are kept outside of the repository and
given with `-PreceiptFixtures=<directory>` (see `ReceiptFixtures`); without, they are skipped.

| Test | Checks |
| --- | --- |
| `ReceiptFixturesTest` | every receipt with `<name>.expected.json` reads as expected |
| `ReceiptParserSnapshotTest` | the output for all receipts is unchanged, positions included |
| `ReceiptParserTest` | single receipts and constructed rows |
| `ReceiptTokensTest`, `RowKindsTest`, `ReceiptVocabularyTest`, `ReceiptDatesTest` | the layers with the examples above |

### A receipt is read wrongly

1. Keep the recognized text as `<name>.json` and write down what the receipt shows in
   `<name>.expected.json`, anonymized with `ReceiptAnonymizer`.
2. Put both into the directory of the fixtures.
   Names and merchant stay as ML Kit reads them, misreads included; items in the order of the
   receipt.
3. `ReceiptFixturesTest` with `-PreceiptFixtures` shows what is read wrongly.
4. Fix it in the layer it belongs to: a misread piece in `ReceiptTokens`, a new word in
   `ReceiptVocabulary`, a new kind of row in `RowKinds`, a new way rows belong together in
   `ReceiptAssembler`. Add the row as example to the test of that layer.
5. If other receipts change, check the diff of the snapshot, then write a new one with
   `./gradlew :next:data:receipts:testDebugUnitTest -PreceiptFixtures=<directory> -PupdateReceiptSnapshot`.

### Another language

Add a `ReceiptVocabulary` with the words of its receipts: total, subtotal, payment, number of
articles, units and how recognition misreads them, currency. Receipts of that language as fixtures
show what else differs, e.g. the order of count and unit price.

## Receipts scanned on their own

A receipt can be scanned from the overview before its transaction is chosen or even booked:

1. `ReceiptViewModel.recognizeUnassigned()` reads it and asks `ReceiptMatcher` for the expenses it
   may belong to: from the day before its date up to a week after it, by amount (the total or the
   amount paid by card) and shop. One with both is preselected, the user always confirms.
2. `ReceiptAssignmentDialog` shows the candidates with their reasons, other expenses by search, and
   lets the receipt wait or creates a transaction for it, e.g. for a cash payment.
3. A chosen transaction leads to the usual check in `ReceiptReviewDialog`. A waiting receipt is
   saved without transaction (`receipts.transaction_id` is null, the photo in `receipts.uri`).
4. `WaitingReceiptsBanner` on the overview tells when the transaction of a waiting receipt has
   been booked; assigning it makes its photo an attachment of the transaction.

## What comes from the app

The module never sees `:myExpenses`. The shell `:ui_next` implements what it needs:

- `ReceiptDatabase`: the stored receipts, the expenses they may belong to and keeping a new photo,
  by `ProviderReceiptDatabase`, which maps `Receipt` to the receipt the app stores.
- `TextRecognition`: the text recognition the user configured, by `OcrTextRecognition`.
- `LocalNewTransactionEditor`: the screen for a new expense filled from the receipt, by
  `ExpenseEditForReceipt`.

`productKey()` is in `:ocr-model`, since the app stores it with the items and the classifier looks
it up.
