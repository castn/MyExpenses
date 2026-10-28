# Illustrations

Scenes with people at the top of pages that introduce something, e.g. the first start. Each scene
fits the content of its page; `HeroIllustration` of the design system shows them in the colors of
the theme, so they follow dynamic colors and the dark theme.

| Scene | Page of the first start | What it shows |
| --- | --- | --- |
| `welcome` | Welcome | a person holding a phone between a chart and a receipt |
| `privacy` | Privacy | a person sitting beside a shield with a lock |
| `start` | How to start | a person at a signpost with a sign for each way |
| `account` | First account | a person dropping coins into a wallet |
| `done` | Done | a person stepping towards a check among confetti |

## How a scene is made

The people are [Humaaans](https://www.humaaans.com/) by Pablo Stanley, published under
[CC0](https://creativecommons.org/publicdomain/zero/1.0/): free to use and change, no credit
required. Only the pieces in use are copied to `humaaans/`, unchanged.

A scene in `scenes/` is an SVG that places them and adds own props in the same flat style:

```xml
<use href="humaaans/standing-3.svg" top="primary" bottom="secondary"/>
<rect x="20" y="24" width="100" height="10" rx="5" fill="slot:paper"/>
```

- `top` and `bottom` give the clothes of a person a color of the theme: the lightest color of the
  group `Body/*` or `Bottom/*` becomes the slot, the others its shade. Seats (`Objects/*`) take
  `seat` or `neutral`. Skin, hair and shoes keep their colors.
- Props name a slot: `primary`, `secondary`, `tertiary` (each also as `…Shade`), `neutral`,
  `neutralShade`, `paper` (cards, receipts, screens), `line` (text on paper) and `ink`.

`build_illustrations.py` turns each scene into `src/main/res/drawable/illustration_<scene>.xml`, a
vector drawable that paints each slot with a placeholder color. `illustration()` in
`Illustration.kt` replaces them by the colors of the `ColorScheme`; `IllustrationSlot` must list
the slots in the same order as `SLOTS` in the script.

```sh
python3 build_illustrations.py                      # all scenes
python3 build_illustrations.py --preview /tmp/p scenes/done.svg
```

`--preview` also renders each scene in light and dark with the baseline colors of Material 3
(needs Inkscape), from the same tree as the drawable, so it shows what Android draws. The
drawables are generated: change the scene and run the script again.
