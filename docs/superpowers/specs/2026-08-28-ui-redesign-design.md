# QuestLog UI Redesign — Design Spec

**Direction:** *Nightfall × Monument* — Monument's structural bones (hairline
rules, carved hierarchy, a numbered quest ledger) wearing Nightfall's dusk
atmosphere (desaturated indigo, serif numerals, one luminous accent that
appears only on earned states).

**Status:** approved in brainstorming (visual companion, 2026-08-28).
**Scope:** the `app` module only — theme, components, screen structure,
navigation between two screens. **No change to `shared`, the ViewModel
contract, use cases, repositories, or the database.**

---

## 1. Goals & non-goals

**Goals**

- A distinctive, restrained visual identity — not generic Material 3, not
  fantasy clip-art. Reads as a calm "focus companion".
- Replace the single long scroll with a focused **Today** screen; move the
  build grid to its own **Realm** screen one tap away.
- Real typographic hierarchy from a bundled display face; a proper semantic
  colour system; considered spacing driven by rules, not ad-hoc margins.
- Full **light ("dawn")** and **dark ("nightfall")** themes following the
  system setting.
- Calm, purposeful motion that honours reduced-motion.
- Remove emoji used as UI chrome; keep the interface legible at a glance.

**Non-goals**

- No new app features, no bottom navigation, no settings/history screens.
- No changes to reward maths, quests, streaks, billing, or persistence.
- No in-app theme toggle (system setting only).
- No new time-series data — the ambient progress element uses data the app
  already has (today's saved time vs the 90-minute budget).

---

## 2. Global constraints

Carried from the codebase; every task inherits these.

- App package `com.example.questlog`; `applicationId` stays `com.questlog.app`.
- `minSdk 26`, `compileSdk 36`, JDK 21, Kotlin 2.3.21, Compose BOM
  `2026.03.01` (pinned in `gradle/libs.versions.toml`), Material 3.
- Koin DI: `DashboardViewModel` is resolved via `koinViewModel()`; its
  constructor and `appModule` wiring do not change.
- **No component reads a raw `Quest*` colour constant.** Every colour comes
  from the theme — `MaterialTheme.colorScheme` for M3 surfaces, or
  `QuestLogTheme.colors` (the custom token holder) for semantic tokens.
- The theme entry point keeps its current name, `QuestLogTheme` — it is
  reimplemented, not renamed.
- No emoji in persistent UI chrome (headers, labels, buttons, tiles, stats).
  A drawn `ImageVector` or a typographic mark instead. Transient snackbar
  copy may keep a single emoji.
- Bundled fonts are OFL/SIL-licensed; the licence file is committed under
  `app/src/main/res/font/OFL.txt`.
- `./gradlew :app:assembleDebug` and `:app:testDebugUnitTest` pass;
  `:app:assembleRelease` stays R8-clean; `:shared:desktopTest` untouched
  and green.
- New pure formatting/geometry logic lives in testable non-`@Composable`
  functions with JVM unit tests in `app/src/test`.

---

## 3. Design tokens

### 3.1 Colour — semantic, not literal

Two token sets. The **role names** are identical across themes; only the
values differ. Components reference roles, never hexes.

| Role | Dark ("nightfall") | Light ("dawn") | Used for |
|---|---|---|---|
| `groundTop` | `#141B2E` | `#FBFAF6` | top of the screen's radial gradient |
| `ground` | `#0B0E17` | `#F1F2F7` | page background (gradient base) |
| `surface` | `#161C2B` | `#FFFFFF` | tiles, dialog |
| `surfaceRaised` | `#1E2536` | `#ECEDF3` | device-frame edge, pressed states |
| `rule` | `rgba(150,158,192,0.16)` | `rgba(120,128,160,0.20)` | hairline dividers, tile borders |
| `inkPrimary` | `#E5E8F2` | `#1F2333` | headings, values |
| `inkSecondary` | `#A9B0C7` | `#4B5168` | body, quest titles |
| `inkMuted` | `#6E7691` | `#8A90A8` | labels, captions, quest numbers |
| `earned` | `#6EE7D4` | `#0E8F7A` | **only** completed quests, built tiles, the reclaimed-value unit, the progress ring, the "Pro" pill, the "Shield ready" cue |
| `currency` | `#E0A458` | `#B4771E` | gold values only |
| `locked` | `#A78BE6` | `#6B4FC7` | Pro-locked tiles and the paywall's identity |
| `scrim` | `rgba(6,8,14,0.62)` | `rgba(20,22,40,0.30)` | dialog scrim |

**Accent discipline:** `earned` is the one bold colour and appears sparingly.
`currency` and `locked` are functional, never decorative. Everything else is
ink on ground. Do not tint surfaces with the accent.

**Implementation:** an immutable `data class QuestColors(...)` holding every
role as a `Color`, exposed through `val LocalQuestColors = staticCompositionLocalOf { … }`.
`QuestLogTheme.colors` is a convenience accessor
(`object QuestLogTheme { val colors: QuestColors @Composable get() = LocalQuestColors.current }`).
The M3 `ColorScheme` is also populated (so `Snackbar`, `Dialog`, ripples,
text selection inherit correct colours): `background/surface` → `ground/surface`,
`onBackground/onSurface` → `inkPrimary`, `primary` → `earned`,
`onPrimary` → `ground`, `outline` → `rule`, `scrim` → `scrim`. `darkColorScheme()`
/ `lightColorScheme()` as the base. `dynamicColor` is **off**.

### 3.2 Typography

**Bundled:** *Instrument Serif* — Regular (`400`) and Italic. OFL. ~40 KB
each. Files: `app/src/main/res/font/instrument_serif_regular.ttf`,
`instrument_serif_italic.ttf`, plus `OFL.txt`. Declared via an
`androidx.compose.ui.text.font.FontFamily`.

Body stays the platform default (`FontFamily.Default` → Roboto). No second
bundled face.

`QuestType` object exposes the scale (all `TextStyle`s). It also feeds an M3
`Typography` where the mapping is natural (see right column).

| Token | Family / weight | Size / line-height / tracking | Role | ~M3 slot |
|---|---|---|---|---|
| `display` | Serif 400 | 44 / 46 / -0.01em | the "reclaimed today" value, big numerals | `displaySmall` |
| `wordmark` | Serif 400 | 22 / 26 / 0 | app name in the header | — |
| `screenTitle` | Serif 400 | 20 / 24 / 0 | "Your Realm", paywall title | `titleLarge` |
| `serifNumeral` | Serif 400 | 15 / 18 / 0 | quest numbers `01–03`, ring centre, section counts | `labelLarge` |
| `bodyLarge` | Default 400 | 15 / 22 / 0 | quest titles, perk names | `bodyLarge` |
| `bodySmall` | Default 400 | 12 / 17 / 0 | quest subtitles, perk descriptions | `bodySmall` |
| `label` | Default 600 | 10 / 12 / 0.20em, UPPERCASE | section labels ("Today's quests") | `labelMedium` |
| `caption` | Default 500 | 9 / 12 / 0.12em, UPPERCASE | pills, tile status | `labelSmall` |

Italic of the serif is reserved for the emphasised unit in the hero value
(`1h` in `*1h* 30m`) and nowhere else.

### 3.3 Spacing, shape, elevation

- **Spacing scale** (`object QuestSpacing`): `xs 4`, `sm 8`, `md 12`,
  `lg 16`, `xl 24`, `xxl 32` dp. Screen horizontal padding `lg`. Vertical
  rhythm between rule-separated sections `lg`. Inside a section `md`.
- **Shape** (`object QuestShapes` → M3 `Shapes`): `small 10`, `medium 14`,
  `large 20` dp corners. Tiles `medium`. Dialog `large`. Pills fully
  rounded. The dashboard itself has **no card container** — sections sit
  directly on the ground, divided by hairlines.
- **Elevation:** flat. Depth comes from the ground gradient and hairlines,
  not shadows. The paywall dialog is the only elevated surface (M3 default
  tonal elevation, plus the scrim).

### 3.4 The gradient ground

Every screen paints a `Brush.radialGradient` — `groundTop` at
`Offset(centreX, 0)` fading to `ground` by ~55% of height, then flat
`ground`. Implemented once in `QuestScaffold`. Edge-to-edge; content insets
via `WindowInsets`.

---

## 4. Information architecture & navigation

Two screens plus one dialog.

```
QuestLogRoot
├── Today        (start)
├── Realm        (push; system back / header back returns to Today)
└── Paywall      (Dialog over whichever screen; not a route)
```

**No navigation library.** `QuestLogRoot` holds
`var screen by rememberSaveable { mutableStateOf(Screen.Today) }` (`Screen`
is a simple `enum`). Transition is an `AnimatedContent` with a horizontal
slide + fade (Today→Realm slides in from the right; back reverses).
`BackHandler(enabled = screen == Screen.Realm) { screen = Screen.Today }`.

Both screens observe the **same** `DashboardViewModel` (already the app's
single VM, holding all state). `DashboardViewModel` is unchanged; its name
stays.

`showPaywall` in `DashboardUiState` continues to drive the dialog, rendered
by `QuestLogRoot` above both screens. The snackbar host also lives in
`QuestLogRoot` so confirmations survive a screen switch.

---

## 5. Screens

### 5.1 Today

Vertical scroll (`Column` + `verticalScroll`), `lg` horizontal padding,
sections separated by `Hairline()`.

1. **Header row** — `wordmark` "Questlog" left; right: a `Pill` that reads
   "Get Pro" (`locked` outline, opens paywall) or "Pro" (`earned` fill) when
   premium, then a **refresh** icon button (`QuestIcons.Refresh`, shows a
   3 dp `CircularProgressIndicator` in `earned` while `isLoading`).
2. `Hairline`
3. **Hero** (`TodayHero`)
   - `label` "Reclaimed today"
   - a `Row`: `ProgressRing` (56 dp) + the `display` value from
     `formatReclaimed(todaySavedMs)` — `hours` (when non-null) italic in
     `earned`, a hair-space, then `minutes` in `inkPrimary`.
   - `ProgressRing`: a thin (3 dp) arc, `earned` on a `rule`-coloured track,
     swept to `ringFraction(todaySavedMs, DetoxBudget.DEFAULT_DAILY_BUDGET_MS)`.
     Centre text
     (two lines, `serifNumeral`): `"${consecutiveDetoxDays}d"` over
     `formatMultiplier(streakMultiplier)` (e.g. `"2.0×"`). When
     `consecutiveDetoxDays == 0`, centre shows a single `"—"`.
   - Arc animates from 0 to target once on first composition
     (`Animatable`, 700 ms, `FastOutSlowInEasing`); skipped under reduced
     motion (renders at target).
4. `Hairline`
5. **Level** (`LevelBar`) — a `Row`: `bodySmall` `"Level $level · $levelTitle"`
   left, `serifNumeral` `"${(progress*100).toInt()}%"` right, both in
   `inkMuted` (the old bright-cyan percentage is dropped). Below: a 3 dp
   progress line, `inkSecondary` fill on
   `rule` track, `animateFloatAsState` to `TimeConversion.xpProgress(xp)`.
6. `Hairline`
7. **Quests** (`QuestLedger`)
   - section head: `label` "Today's quests" left, `serifNumeral`
     `"$done / ${quests.size}"` right.
   - one `QuestLedgerRow` per quest: `serifNumeral` index `01/02/03` in
     `inkMuted`; a 16 dp checkbox (`rule` border → `earned` fill with a
     drawn tick when `isCompleted`, cross-faded via `animateColorAsState`);
     `bodyLarge` title + `bodySmall` description (`inkMuted`); right-aligned
     reward `"+${xpReward}"` (`bodySmall`; `earned` when complete else
     `inkSecondary`) over `"+${goldReward} g"` (`caption`, `currency`).
   - the per-quest emoji from `DailyQuest.icon` is **not shown**.
8. `Hairline`
9. **Realm strip** (`RealmStrip`)
   - section head: `label` "Your realm" / `serifNumeral` `"$built / $total"`.
   - a `Row` of one 6-dp-tall rounded bar per tile: `earned` if owned,
     `rule` if buyable, a `locked`-outlined hollow bar if Pro-locked.
   - trailing `caption` "View" + `QuestIcons.ArrowRight`. The whole row is
     one click target → `screen = Screen.Realm`.
10. bottom spacer `xxl`.

Perk chips (the old `⚡ 2× XP` / `🛡️ SHIELD READY`) are **removed from
Today**. When premium: the header pill becomes "Pro" and, if
`streakFreezeReady`, a single `caption` line under the hero reads
"Shield ready" in `earned`; when spent it reads "Shield recharging" in
`inkMuted`. The 2× state is implied by the "Pro" pill — no separate chip.

### 5.2 Realm

1. **Header row** — a back `IconButton` (`QuestIcons.Back`) + `screenTitle`
   "Your Realm" left; `serifNumeral` `"$gold g"` in `currency` right.
2. `Hairline`
3. `label` `"$built of $total built"`.
4. **Grid** — the header row, hairline and label are fixed at the top; the
   `LazyVerticalGrid(GridCells.Fixed(2))` takes the remaining height and is
   the screen's only scroller (no nested scrolling). `md` gaps, one
   `RealmTile` per `CityTile`:
   - `surface` fill, 1 dp border: `earned`(α .3) if owned, `locked`(α .45,
     **dashed** — a `drawBehind` dashed `RoundedCornerShape` stroke) if
     Pro-locked, `rule` otherwise.
   - `bodyLarge` `displayName`; the tier as a small `serifNumeral`
     `"I / II / III"` in `inkMuted` top-right.
   - status `caption` bottom-left: `"Built"` (`earned`), `"$goldCost g"`
     (`currency`), `"Free"` (`currency`) or `"Pro"` (`locked`).
   - no building emoji.
   - `clickable` → `DashboardIntent.Purchase(tile)` (unchanged intent;
     `PremiumRequired` still flips `showPaywall`, `InsufficientFunds` still
     shows the snackbar).
5. footer `caption`, centered: `"Tap a lit tile to build it"`.

### 5.3 Paywall (`PaywallDialog`, renamed from `PaywallModal`)

`Dialog`. Content on `surface`, `large` shape, `xl` padding, centered.

- a 42 dp ring outline in `locked`, containing `QuestIcons.Crown`.
- `screenTitle` "Questlog Pro"; `caption` in `locked` "Architect of the High Realm".
- `Hairline`.
- three `PerkRow`s (icon in `earned` serif mark, `bodyLarge` name,
  `bodySmall` `inkMuted` description):
  - `×2` — **Double rewards** — "Every minute of focus time pays twice the XP and gold"
  - `◇` — **Streak Freeze** — "Protects your streak through one missed day a week"
  - `▢` — **Two realm buildings** — "Crystal Castle and Aurora Fountain"
- primary `Button` (`earned` container, `ground` label): "Unlock — $4.99 / month" → `onUnlockPro`.
- text button `caption` `inkMuted`: "Maybe later" → `onDismiss`.
- the RevenueCat footnote line is kept, `caption`, `inkMuted`.

---

## 6. Components & file layout

New tree under `app/src/main/java/com/example/questlog/`:

```
theme/
  QuestColors.kt      NEW  data class + LocalQuestColors + light/dark instances
  Color.kt            REPLACE  raw palette → private consts feeding QuestColors
  Theme.kt            REPLACE  QuestLogTheme(darkTheme): M3 scheme + typography + shapes + provides QuestColors; + object QuestLogTheme.colors accessor
  Type.kt             REPLACE  Instrument Serif family + QuestType scale + M3 Typography
  Shape.kt            NEW  QuestShapes → M3 Shapes
  Spacing.kt          NEW  QuestSpacing object
  QuestIcons.kt       NEW  ImageVector defs: Back, Refresh, Check, ArrowRight, Crown, Lock
ui/
  QuestLogRoot.kt     NEW  screen host, AnimatedContent, paywall + snackbar
  common/
    QuestScaffold.kt  NEW  gradient background + status-bar insets + header slot
    Hairline.kt       NEW
    SectionHeader.kt  NEW  label + right-aligned serif count
    Pill.kt           NEW
  today/
    TodayScreen.kt    NEW
    TodayHero.kt      NEW
    ProgressRing.kt   NEW  Canvas arc, animated sweep
    LevelBar.kt       NEW
    QuestLedger.kt    NEW  + QuestLedgerRow
    RealmStrip.kt     NEW
  realm/
    RealmScreen.kt    NEW
    RealmTile.kt      NEW
  paywall/
    PaywallDialog.kt  NEW  (replaces components/PaywallModal.kt)
  format/
    Formatting.kt     NEW  pure: formatReclaimed, formatMultiplier, levelTitle, ringFraction
DELETE:
  ui/components/StatsCard.kt
  ui/components/DailyQuestBanner.kt
  ui/components/CityGrid.kt
  ui/components/PaywallModal.kt
  ui/dashboard/DashboardScreen.kt
KEEP:
  ui/dashboard/DashboardViewModel.kt   (unchanged)
```

`MainActivity` changes only its content:
`QuestLogTheme { QuestLogRoot(viewModel = koinViewModel()) }` (drops the bare
`Surface` — `QuestScaffold` owns the background).

Each composable file has one public composable + its private children, kept
small enough to hold in context. Every new public composable gets a
`@Preview` (light + dark) using a hand-built fake `PlayerStats` /
`List<CityTile>` / `List<DailyQuest>`.

### 6.1 `QuestIcons`

Six `ImageVector`s built with `ImageVector.Builder` (no dependency).
Simple 24 dp line glyphs, `strokeWidth` 2, `currentColor` via tint at call
site. `Check` and the ring are drawn; `Crown` is a minimal 5-point outline.

---

## 7. Pure logic to extract (and test)

Move all formatting/derivation out of composables into
`ui/format/Formatting.kt`:

| Function | Signature | Notes / cases |
|---|---|---|
| `formatReclaimed` | `(ms: Long) -> ReclaimedText` | `data class ReclaimedText(val hours: String?, val minutes: String)` so the hero can italicise the hours in `earned`. `0 → (null,"0m")`, `45min → (null,"45m")`, `90min → ("1h","30m")`, `125min → ("2h","5m")`. Minutes = `(totalMin % 60)`, hours present only when `totalMin >= 60`. Mirrors the current `StatsCard` "${h}h ${m}m" / "${m}m" split. |
| `formatMultiplier` | `(Float) -> String` | `2.0f → "2.0×"`, one decimal, `×` (U+00D7). |
| `levelTitle` | `(Int) -> String` | the existing 1→"Novice of Will" … 4→"Knight of Discipline", else "Grandmaster of Focus", verbatim. |
| `ringFraction` | `(savedMs: Long, budgetMs: Long) -> Float` | `budgetMs <= 0 → 0f`; else `(savedMs.toFloat() / budgetMs).coerceIn(0f, 1f)`. |

`DEFAULT_DAILY_BUDGET_MS` is read from `com.questlog.util.DetoxBudget`
(already in `shared`).

Tests: `app/src/test/java/com/example/questlog/ui/format/FormattingTest.kt`
— one case per row above, boundaries included. Runs in
`:app:testDebugUnitTest` (already a CI task).

---

## 8. Motion

| Element | Animation | Reduced-motion |
|---|---|---|
| Screen change | `AnimatedContent`, 250 ms slide+fade | cross-fade only, 120 ms |
| Hero value | count-up on change (`animateIntAsState` over the minute count, formatted each frame), 600 ms | jump to value |
| Progress ring | sweep 0→target once, 700 ms | render at target |
| XP bar | `animateFloatAsState`, 800 ms (unchanged) | (M3 respects scale) |
| Quest check | `animateColorAsState` border/fill, 200 ms | instant |
| Tile press | M3 ripple (themed) | n/a |

Reduced motion via a `@Composable fun reducedMotion(): Boolean` reading
`LocalContext`'s `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`. Explicit
`Animatable` sequences check it; `animate*AsState` already scales with the
system setting.

---

## 9. Accessibility

- Every `IconButton` gets a `contentDescription` ("Refresh", "Back",
  "Upgrade to Pro").
- The ProgressRing has `contentDescription = "Today: 1h 30m of the 90 minute
  budget reclaimed, 6 day streak"`.
- The realm strip's single click target gets
  `contentDescription = "Your realm, 4 of 6 built. Open."` and
  `Role.Button`.
- Contrast: `inkSecondary` and `inkMuted` on `ground` must clear 4.5:1 in
  both themes (the §3.1 values are chosen for this; the implementer confirms
  with a contrast check and nudges the token if any pair falls short).
- Touch targets ≥ 48 dp (checkbox rows, tiles, pills padded to meet it).
- Text scales with the system font-scale (all sizes in `sp`; no fixed
  heights on text containers).

---

## 10. Testing strategy

Compose UI is not exercised by `ci.yml` (it runs `:app:testDebugUnitTest`,
not instrumented tests) — the same stance CLAUDE.md documents for shared
Android tests.

- **JVM unit (`app/src/test`, runs in CI):** `FormattingTest` (§7). Verify
  `DashboardViewModelTest` and `PremiumStatusProviderSeamTest` stay green
  (no VM change expected).
- **Instrumented (`app/src/androidTest`, present deps, runnable locally,
  not in CI):** `TodayScreenTest`, `RealmScreenTest` with
  `createComposeRule()` covering: quest count renders; tapping the realm
  strip navigates to Realm; back returns to Today; tapping a buyable tile
  emits `Purchase`; tapping a Pro tile opens the paywall; the "Get Pro"
  pill opens the paywall. These are written but **flagged as a follow-up to
  wire into CI** (needs an emulator job in `ci.yml`).
- **Previews:** every component has light+dark `@Preview`s; the reviewer
  checks them render.
- **Manual gate:** `:app:assembleDebug`, `:app:assembleRelease` (R8),
  install on the Pixel 8a emulator, walk Today → Realm → buy → paywall in
  both light and dark system settings.

---

## 11. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Instrument Serif has no bold — headings may feel light | Scale + tracking carry hierarchy; the serif is only ever large. Verified in the mockup. |
| Dusk gradient + `earned` mint is close to the "near-black + lone acid-green" AI cluster | Accent is used *only* on earned states and paired with lavender-grey inks, not pure grey; the serif numerals and hairline structure pull it away from that cluster. |
| Removing the sparkline weakens the hero | The budget-progress ring replaces it with honest data; revisit a real sparkline only if hourly saved-time history is ever stored. |
| No nav library → manual back-stack bugs | Only one push depth; `rememberSaveable` + `BackHandler` is sufficient and covered by an instrumented test. |
| `LazyVerticalGrid` inside a scrollable column (Realm) | Realm's grid is the screen's only scroller — it fills height, no nesting. |
| Light theme neglected | Every token has both values; every `@Preview` is dual; the manual gate walks both. |
| Big diff, hard to review | File layout in §6 keeps each unit small; the plan sequences theme → primitives → components → screens → wiring → delete, each independently reviewable. |

---

## 12. Out of scope / follow-ups

- Wiring an instrumented-test job into `ci.yml`.
- A real day-progress sparkline (needs a saved-time time-series in `shared`).
- An in-app theme toggle.
- Animated building illustrations on Realm tiles.
- Haptics.
