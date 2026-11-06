---
name: architektur-pruefen
description: Prüft Code der neuen Oberfläche (:ui_next, die Module unter next/ (:next:core:*, :next:data:*, :next:feature:*), :designsystem, :charts, :ocr-model, :mlkit-core, :fixturecollector und Hooks in :myExpenses) gegen die Regeln in ui_next/ARCHITECTURE.md und meldet Verstöße mit Regel-Id und datei:zeile. Für "prüfe die Architektur", "passt das zur Architektur", Review eines Branches, Pakets oder Moduls auf Architekturregeln. Ändert keinen Code.
---

# Architektur prüfen

## Ziel
Code gegen die Regeln in `ui_next/ARCHITECTURE.md` prüfen und einen Befund liefern: welche Regel
an welcher Stelle verletzt ist und was zu tun wäre. Grundlage sind **nur** die Regeln dieser Datei.
Keine eigenen Regeln erfinden, keinen Stil bewerten, keine Fehler suchen (dafür gibt es
`/code-review`).

Niemals im selben Durchgang Code ändern. Am Ende anbieten, die Befunde zu beheben.

## Phase 0: Umfang klären
Aus der Anfrage ableiten, sonst den Standard nehmen:

| Anfrage | Umfang |
| --- | --- |
| nichts genannt, "der Branch", "meine Änderungen" | Diff des Branches gegen `master` plus uncommittete Änderungen (Standard) |
| ein Paket, z.B. "budgets" | alle Dateien der Module `next/data/budgets` und `next/feature/budgets` (bzw. des Pakets in `ui_next`, wenn es in der Hülle liegt) und die Tests dazu |
| ein Modul, z.B. ":charts" | alle Quellen und `build.gradle` des Moduls |
| "alles" | alle Module aus `ARCHITECTURE.md`; dann Phase 3 per Subagent |

Nur nachfragen, wenn die Anfrage zwei Umfänge offen lässt.

## Phase 1: Regeln lesen
`ui_next/ARCHITECTURE.md` vollständig lesen, dazu `ui_next/modules.puml`. Die Regeln mit Id
(M1, P3, A2, …) sind die Prüfliste. Bei jedem Lauf neu lesen, nicht aus dem Gedächtnis prüfen:
die Datei ändert sich.

## Phase 2: Dateien bestimmen
Im Worktree einfache git-Befehle ohne Variablen verwenden:

```
git merge-base master HEAD
git diff --name-only <merge-base>
git status --porcelain
```

Nur Dateien der Module aus `ARCHITECTURE.md` behalten. Für Paket- und Modulumfang die Dateien mit
Glob auflisten. Merken, welche Pakete und Module (`ui_next`, `next/**`) berührt sind: deren README und `.puml` gehören
für P5/P6 dazu.

Dateien in `:myExpenses` aus dem Diff sind Kandidaten für M6 und M7, auch wenn sonst nichts im
Hauptmodul geprüft wird.

## Phase 3: Mechanische Vorprüfung
Zuerst die Konsist-Tests laufen lassen (Regeln mit Markierung `Konsist` in `ARCHITECTURE.md`):

```
./gradlew :architecture-test:test --console=plain -q
```

Jede gemeldete Stelle (`KoAssertionFailedException`, mit Regel-Id und `datei:zeile` in
`architecture-test/build/test-results/test/*.xml`) ist ein **Verstoß**, wenn sie im Umfang liegt.
Stellen außerhalb des Umfangs nur in der Zusammenfassung zählen. Diese Regeln nicht noch einmal
per Grep prüfen. Bricht der Build vor den Tests ab, das sagen und für diese Regeln auf die Suchen
unten zurückfallen (M2, M3, M4, A3, V1, V5, S4).

Für die übrigen Regeln mit Markierung `test` diese Suchen laufen lassen, auf den Umfang begrenzt.
Ein Treffer ist ein **Kandidat**, kein Befund: jeden an der Stelle lesen und gegen den Wortlaut der
Regel prüfen.

| Regel | Suche (Grep) | Befund, wenn |
| --- | --- | --- |
| M1 | Diff von `ui_next/modules.puml` und `project\(` in `build.gradle*` der Module | `./gradlew :ui_next:checkModuleGraph` fehlschlägt oder eine neue Kante ohne Grund dazukommt |
| M2, M3 | `import org.totschnig.myexpenses\.(?!charts\|designsystem)` in `:charts` bzw. `:designsystem`; `Money\|CurrencyUnit` in `:charts` | ein Typ der App importiert wird |
| M4 | `import android` in `ocr-model` | immer |
| M5 | `project\(':(myExpenses\|ui_next)'\)` in `fixturecollector/build.gradle` | immer |
| M7 | `DATABASE_VERSION` im Diff | der Branch die Version mehr als einmal erhöht |
| M10 | `import org.totschnig.myexpenses\.(provider\|db2\|activity\|viewmodel)` in `ui_next` | außerhalb der Adapter (`Provider…`, `Repository…`, `App…`, `…Reading`, `…Picker`) in Paketen, die es auch als Feature-Modul gibt |
| M11 | `^package ` in `next/**` | das Paket nicht mit dem Namespace aus `build.gradle` des Moduls beginnt (Kotlin-JVM-Module: `org.totschnig.myexpenses.next.core.<name>`) |
| M13 | `import org.totschnig.myexpenses.next.(feature\|data)\.` in `ui_next` | außerhalb des Pakets des Bereichs (`next.<bereich>`), `NextFeatures.kt` und `NextData.kt` (nur ohne Test) |
| P1 | `import org.totschnig.myexpenses.next.(data\|feature).<anderer Bereich>.*(Store\|Screen\|ViewModel)` in `next/feature/**` | Import aus einem fremden Feature (View Models prüft der Test zu V5) |
| V5 | `import org.totschnig.myexpenses.next.*ViewModel` | außerhalb `MyExpensesNext`, `NextSettings`, `…Feature` und `…Flow` (nur ohne Test) |
| A2 | `LocalDate.now\|LocalDateTime.now\|System.currentTimeMillis\|Clock\.` | es in einer Analyse, einem Input oder einer Facts-Ableitung steht (nicht: Nutzeraktion, Preview, Datumsauswahl im UI) |
| A3 | `class \w+(Input\|Facts)\b` ohne `data` | immer |
| A8 | `\w+ViewModel\.(facts\|rows\|readiness\|savingTips)` in `viewModelsOf { … }` der `…Feature`-Einträge der Shell | ein View Model Facts von einem anderen View Model statt aus `NextData` bekommt |
| V1 | `@Inject\|by lazy\|fun start\(\)\|injector.inject` in `ui_next` und `next/**` | bei einem View Model der neuen UI (nicht bei View Models des Hauptmoduls) |
| V3 | `dataStore\|DataStore` in `*ViewModel.kt` | immer |
| S4 | `AlertDialog` in `ui_next` und `next/**` | immer |
| S7 | `DateTimeFormatter.of\|NumberFormat.get` in `ui_next` und `next/**` außerhalb `Formats.kt` | ein Format ad hoc gebaut wird, das `Formats.kt` schon hat |
| S8 | `BackHandler` in `next/**` | in einem Feature, das schon einen Back-Stack (`…Key`) hat, für einen Screen statt für einen Dialog oder Bearbeitungsmodus |
| S9 | `ModalBottomSheet` in `ui_next` und `next/**`; `MultiChoiceDialog` überall | immer |

Ausnahmen: Steht an der Stelle ein Kommentar `// Exception to <Id>: …`, die Stelle nicht melden,
nur in der Zusammenfassung zählen.

## Phase 4: Prüfung mit Urteil
Die übrigen Regeln (Markierung `review`) an den Dateien des Umfangs prüfen. Dafür die Dateien
lesen, bei einem Diff zuerst den Diff (`git diff <merge-base> -- <datei>`), bei Bedarf die ganze
Datei.

Bei großem Umfang (mehr als etwa 25 Dateien oder "alles") die Exploration nicht selbst machen,
sondern genau einen Subagenten `code-explorer` starten und um einen Fundbericht bitten, ohne
Bewertung: je Datei Paket, Imports aus `org.totschnig.myexpenses`, Klassen mit Art (data class,
ViewModel, Composable, Store), Konstruktorparameter der View Models, Composables mit mehr als
etwa 15 Zeilen Logik außerhalb von UI-Aufrufen, Aufrufe von Datenbank, DataStore oder Netzwerk.
Vorher den Explorer-Cache fragen (`~/.claude/scripts/explorer-cache list`), wie im Skill
`anforderungen-aus-code` beschrieben.

Worauf je Gruppe achten:

- **M6**: Jede geänderte Datei in `:myExpenses` einzeln ansehen. Ein Hook ist additiv und klein
  (Parameter mit Default, `public`, Callback). Neue Logik der neuen UI im Hauptmodul ist ein
  Befund, ebenso Code in `:ui_next`, der sichtbar eine Funktion des Hauptmoduls nachbaut.
- **P3, P4**: Wird etwas berechnet, das es schon gibt? Bei Verdacht mit Grep nach dem Namen oder
  der Schwelle im Rest von `next` suchen, bevor gemeldet wird.
- **P5, P6**: README und `.puml` der berührten Pakete lesen. Klassennamen, die dort stehen, müssen
  existieren; neue View Models und Facts müssen in der Tabelle von `ledger/README.md` und in
  `ledger/architecture.puml` stehen.
- **A1, A4, A5, A7**: Hat die neue Analyse eine reine Funktion, einen Input als data class, einen
  `Memo` und einen Test, der Neuberechnung prüft? Tests unter `ui_next/src/test` und `next/**/src/test` suchen.
- **S1, S2, S5**: Neue Screens: Einstieg über `…Flow`? Aktionen als `…Actions`? Rechnet ein
  Composable selbst?
- **S3**: Neue Composables in `ui_next` oder `next/**`, die eine Karte, Zeile, Überschrift, Leerzustand oder einen
  Dialog von Hand bauen (`Card(`, `Surface(`, `Row(` mit eigener Gestaltung), mit den Komponenten in
  `designsystem/src/main/java/org/totschnig/myexpenses/designsystem` vergleichen. Ein Befund, wenn
  es die Komponente gibt oder dasselbe Muster ein zweites Mal entsteht.
- **S6**: Neue Strings in `values` und `values-de`, Präfix `next_`, Begriffe "Gruppe" und "Bereich".
- **D1 bis D4**: Nur wenn der Code Daten anlegt, speichert, sichert oder sendet.

Nur melden, was eine Regel trifft. Was auffällt, aber keine Regel trifft, höchstens drei Punkte
unter "Ohne Regel" am Ende, mit der Frage, ob es eine Regel werden soll.

## Phase 5: Befund
Im Chat ausgeben, keine Datei schreiben (außer der Nutzer bittet darum). Gliederung:

```
## Architekturprüfung: <Umfang>

Geprüft: <n> Dateien, Regeln <Ids>; ausgelassen: <Ids> (betrifft den Umfang nicht)

### Verstöße
- **<Id>** `datei:zeile` – was gegen die Regel verstößt. Vorschlag: was zu tun wäre.

### Zu prüfen
- **<Id>** `datei:zeile` – warum es ein Verstoß sein könnte und was dagegen spricht.

### Ohne Regel
- …

<k> Ausnahmen per Kommentar. Soll ich die Verstöße beheben?
```

- **Verstoß**: der Wortlaut der Regel ist eindeutig verletzt.
- **Zu prüfen**: die Regel trifft vielleicht, die Entscheidung braucht den Nutzer (z.B. ob ein
  Import aus `transactions` ein geteiltes Bauteil oder eine fremde Abhängigkeit ist).
- Je Befund ein Satz, keine Code-Blöcke, Pfade repo-relativ.
- Leere Abschnitte weglassen. Ohne Befund: ein Satz, welche Regeln geprüft wurden.
- Passt eine Regel nicht mehr zum Code, weil der Code besser ist, das sagen und eine Änderung von
  `ARCHITECTURE.md` vorschlagen statt den Code zu beanstanden.
