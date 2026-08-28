# QuestLog UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the QuestLog `app` UI with the *Nightfall × Monument* design — a two-screen (Today / Realm) interface with a semantic colour token system, a bundled serif display face, light + dark themes, and emoji removed from chrome.

**Architecture:** All work is in `app/`. A new `theme/` layer supplies a `QuestColors` token holder (custom `CompositionLocal`) plus M3 `ColorScheme`/`Typography`/`Shapes`, wired through the existing `QuestLogTheme` composable. New composables live under `ui/today/`, `ui/realm/`, `ui/paywall/`, `ui/common/`. A dependency-free `QuestLogRoot` hosts a two-state screen enum with an `AnimatedContent` transition and a `BackHandler`. `DashboardViewModel` and everything in `shared/` are untouched. Old components stay compiling until a final cleanup task deletes them.

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose (BOM `2026.03.01`), Material 3, Koin, JUnit4. minSdk 26 / compileSdk 36 / JDK 21.

**Spec:** `docs/superpowers/specs/2026-08-28-ui-redesign-design.md` — read it alongside this plan.

## Global Constraints

- App package `com.example.questlog`; `R` is `com.example.questlog.R`; `applicationId` stays `com.questlog.app`. Do not change `namespace`, `applicationId`, or `versionName`.
- minSdk 26, compileSdk 36, JDK 21, Kotlin 2.3.21, Compose BOM `2026.03.01` (pinned in `gradle/libs.versions.toml`), Material 3.
- `DashboardViewModel` — its constructor, `appModule` wiring, and name do **not** change. `shared/` is not modified.
- **No component reads a raw `Quest*` colour constant.** New code takes colour from `MaterialTheme.colorScheme` (M3 surfaces) or `QuestLogTheme.colors` (semantic tokens). The old `Quest*` constants remain in `theme/Color.kt` only until Task 15 deletes them.
- The theme entry point keeps its name `QuestLogTheme` — reimplemented, not renamed.
- No emoji in persistent UI chrome (headers, labels, buttons, tiles, stats, perk rows). Use a drawn `ImageVector` (`QuestIcons`) or a typographic mark. Transient snackbar copy set by `DashboardViewModel` is out of scope and unchanged.
- Bundled fonts are OFL/SIL-licensed; the licence text is committed at `app/src/main/res/font/OFL.txt`.
- Every new public composable gets `@Preview` functions for **both** light and dark (`@Preview(uiMode = UI_MODE_NIGHT_YES)` and default), using hand-built fakes — never real repositories.
- Gate for every task: `./gradlew :app:assembleDebug --no-daemon` prints `BUILD SUCCESSFUL`. Tasks that add pure logic also run `./gradlew :app:testDebugUnitTest --no-daemon`. Use `--rerun-tasks` when re-running tests.
- Compose UI is **not** unit-tested in this plan (see spec §10). Only Task 1 is TDD. Composable tasks are gated by compilation + a rendered `@Preview`. Instrumented tests are written in Task 14 and are **not** run by CI.
- Commit at the end of every task. Commit messages end with the trailer:
  ```

  Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01VbKdFn1KxazkQd2SkrLTWx
  ```
- Colour token values, type scale, spacing scale, and copy strings are in spec §3 and §5 — use them verbatim.

---

## File Structure

```
app/src/main/java/com/example/questlog/
  theme/
    Color.kt          MODIFY (T2)  add private token consts; DELETE old Quest* consts in T15
    QuestColors.kt     CREATE (T2)  QuestColors data class, LocalQuestColors, light/dark instances, QuestLogTheme accessor object
    Spacing.kt         CREATE (T2)  QuestSpacing
    QuestShapes.kt     CREATE (T2)  QuestShapes -> M3 Shapes
    Type.kt            MODIFY (T2)  QuestType scale + M3 Typography (serif = FontFamily.Serif placeholder)
    QuestFonts.kt      CREATE (T3)  InstrumentSerif FontFamily
    Type.kt            MODIFY (T3)  point serif tokens at InstrumentSerif
    Theme.kt           MODIFY (T2)  QuestLogTheme(darkTheme=isSystemInDarkTheme()) — M3 + tokens
    QuestIcons.kt      CREATE (T4)  Back, Refresh, Check, ArrowRight, Crown, Lock ImageVectors
  ui/
    common/
      Motion.kt        CREATE (T6)  reducedMotion() helper
      Hairline.kt      CREATE (T5)
      Pill.kt          CREATE (T5)
      SectionHeader.kt CREATE (T5)
      QuestScaffold.kt CREATE (T5)  gradient background + insets + header slot
    today/
      ProgressRing.kt  CREATE (T6)
      TodayHero.kt     CREATE (T7)
      LevelBar.kt      CREATE (T7)
      QuestLedger.kt   CREATE (T8)  QuestLedger + QuestLedgerRow
      RealmStrip.kt    CREATE (T9)
      TodayScreen.kt   CREATE (T10)
    realm/
      RealmScreen.kt   CREATE (T11)
      RealmTile.kt     CREATE (T11)
    paywall/
      PaywallDialog.kt CREATE (T12)
    format/
      Formatting.kt    CREATE (T1)
    QuestLogRoot.kt    CREATE (T13)
  MainActivity.kt      MODIFY (T13)

  ui/components/StatsCard.kt        DELETE (T15)
  ui/components/DailyQuestBanner.kt DELETE (T15)
  ui/components/CityGrid.kt         DELETE (T15)
  ui/components/PaywallModal.kt     DELETE (T12)
  ui/dashboard/DashboardScreen.kt   DELETE (T13)
  ui/dashboard/DashboardViewModel.kt  KEEP — unchanged

app/src/main/res/font/
  instrument_serif_regular.ttf  ADD (T3)
  instrument_serif_italic.ttf   ADD (T3)
  OFL.txt                        ADD (T3)

app/src/test/java/com/example/questlog/ui/format/FormattingTest.kt  CREATE (T1)
app/src/androidTest/java/com/example/questlog/ui/TodayScreenTest.kt CREATE (T14)
app/src/androidTest/java/com/example/questlog/ui/RealmScreenTest.kt CREATE (T14)

README.md, CHANGELOG.md, CLAUDE.md  MODIFY (T15)
```

---

## Task 1: Formatting helpers (pure, TDD)

**Files:**
- Create: `app/src/main/java/com/example/questlog/ui/format/Formatting.kt`
- Test: `app/src/test/java/com/example/questlog/ui/format/FormattingTest.kt`

**Interfaces:**
- Consumes: `com.questlog.util.TimeConversion` (already on the classpath via `:shared`), `com.questlog.util.DetoxBudget.DEFAULT_DAILY_BUDGET_MS: Long`.
- Produces:
  - `data class ReclaimedText(val hours: String?, val minutes: String)`
  - `fun formatReclaimed(ms: Long): ReclaimedText`
  - `fun formatMultiplier(multiplier: Float): String`
  - `fun levelTitle(level: Int): String`
  - `fun ringFraction(savedMs: Long, budgetMs: Long): Float`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/example/questlog/ui/format/FormattingTest.kt`:

```kotlin
package com.example.questlog.ui.format

import org.junit.Test
import kotlin.test.assertEquals

class FormattingTest {

    @Test
    fun `formatReclaimed splits hours and minutes`() {
        assertEquals(ReclaimedText(null, "0m"), formatReclaimed(0L))
        assertEquals(ReclaimedText(null, "45m"), formatReclaimed(45 * 60_000L))
        assertEquals(ReclaimedText("1h", "30m"), formatReclaimed(90 * 60_000L))
        assertEquals(ReclaimedText("2h", "5m"), formatReclaimed(125 * 60_000L))
        assertEquals(ReclaimedText("1h", "0m"), formatReclaimed(60 * 60_000L))
    }

    @Test
    fun `formatReclaimed floors partial minutes`() {
        assertEquals(ReclaimedText(null, "1m"), formatReclaimed(119_000L))
    }

    @Test
    fun `formatMultiplier is one decimal with the times glyph`() {
        assertEquals("2.0×", formatMultiplier(2.0f))
        assertEquals("1.0×", formatMultiplier(1.0f))
        assertEquals("3.0×", formatMultiplier(3.0f))
        assertEquals("1.5×", formatMultiplier(1.5f))
    }

    @Test
    fun `levelTitle maps 1 to 4 then falls through`() {
        assertEquals("Novice of Will", levelTitle(1))
        assertEquals("Seeker of Focus", levelTitle(2))
        assertEquals("Guardian of Time", levelTitle(3))
        assertEquals("Knight of Discipline", levelTitle(4))
        assertEquals("Grandmaster of Focus", levelTitle(5))
        assertEquals("Grandmaster of Focus", levelTitle(9))
    }

    @Test
    fun `ringFraction clamps to 0 and 1`() {
        assertEquals(0f, ringFraction(0L, 90 * 60_000L))
        assertEquals(0.5f, ringFraction(45 * 60_000L, 90 * 60_000L))
        assertEquals(1f, ringFraction(200 * 60_000L, 90 * 60_000L))
        assertEquals(0f, ringFraction(10L, 0L))
    }
}
```

- [ ] **Step 2: Run the test — verify it fails**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --rerun-tasks --tests 'com.example.questlog.ui.format.FormattingTest'`
Expected: FAIL — `Unresolved reference: formatReclaimed` (compilation error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/example/questlog/ui/format/Formatting.kt`:

```kotlin
package com.example.questlog.ui.format

/** The reclaimed-time value split so the hero can style the hours separately. */
data class ReclaimedText(val hours: String?, val minutes: String)

/** e.g. 90 min -> ("1h", "30m"); 45 min -> (null, "45m"); 0 -> (null, "0m"). */
fun formatReclaimed(ms: Long): ReclaimedText {
    val totalMinutes = ms / 60_000L
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return ReclaimedText(
        hours = if (h > 0) "${h}h" else null,
        minutes = "${m}m",
    )
}

/** e.g. 2.0f -> "2.0×". One decimal place, U+00D7. */
fun formatMultiplier(multiplier: Float): String {
    val rounded = (multiplier * 10).toInt() / 10.0
    return "$rounded×"
}

/** The five level titles; anything past 4 is "Grandmaster of Focus". */
fun levelTitle(level: Int): String = when (level) {
    1 -> "Novice of Will"
    2 -> "Seeker of Focus"
    3 -> "Guardian of Time"
    4 -> "Knight of Discipline"
    else -> "Grandmaster of Focus"
}

/** Today's saved time as a 0..1 fraction of the daily budget. */
fun ringFraction(savedMs: Long, budgetMs: Long): Float {
    if (budgetMs <= 0L) return 0f
    return (savedMs.toFloat() / budgetMs).coerceIn(0f, 1f)
}
```

- [ ] **Step 4: Run the test — verify it passes**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --rerun-tasks --tests 'com.example.questlog.ui.format.FormattingTest'`
Expected: PASS, 5 tests. Then run the full module: `./gradlew :app:testDebugUnitTest --no-daemon --rerun-tasks` — all green (existing `DashboardViewModelTest`, `PremiumStatusProviderSeamTest` unaffected).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/questlog/ui/format/Formatting.kt app/src/test/java/com/example/questlog/ui/format/FormattingTest.kt
git commit -m "Add UI formatting helpers"
```

---

## Task 2: Colour tokens, spacing, shapes, type scale, theme

**Files:**
- Create: `app/src/main/java/com/example/questlog/theme/QuestColors.kt`
- Create: `app/src/main/java/com/example/questlog/theme/Spacing.kt`
- Create: `app/src/main/java/com/example/questlog/theme/QuestShapes.kt`
- Modify: `app/src/main/java/com/example/questlog/theme/Color.kt` (add consts; keep the old ones)
- Modify: `app/src/main/java/com/example/questlog/theme/Type.kt` (replace)
- Modify: `app/src/main/java/com/example/questlog/theme/Theme.kt` (replace)

**Interfaces:**
- Produces:
  - `QuestColors` data class with: `groundTop, ground, surface, surfaceRaised, rule, inkPrimary, inkSecondary, inkMuted, earned, currency, locked, scrim` — all `Color`.
  - `val LocalQuestColors: ProvidableCompositionLocal<QuestColors>`
  - `object QuestLogTheme { val colors: QuestColors @Composable @ReadOnlyComposable get() }`
  - `object QuestSpacing { val xs=4.dp; sm=8; md=12; lg=16; xl=24; xxl=32 }`
  - `object QuestType` with `TextStyle`s: `display, wordmark, screenTitle, serifNumeral, bodyLarge, bodySmall, label, caption`
  - `@Composable fun QuestLogTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`

- [ ] **Step 1: Create `theme/QuestColors.kt`**

```kotlin
package com.example.questlog.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic colour roles. Identical names across themes; values differ. */
data class QuestColors(
    val groundTop: Color,
    val ground: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val rule: Color,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val inkMuted: Color,
    val earned: Color,
    val currency: Color,
    val locked: Color,
    val scrim: Color,
)

val questDarkColors = QuestColors(
    groundTop = Color(0xFF141B2E),
    ground = Color(0xFF0B0E17),
    surface = Color(0xFF161C2B),
    surfaceRaised = Color(0xFF1E2536),
    rule = Color(0x29969EC0),           // rgba(150,158,192,0.16)
    inkPrimary = Color(0xFFE5E8F2),
    inkSecondary = Color(0xFFA9B0C7),
    inkMuted = Color(0xFF6E7691),
    earned = Color(0xFF6EE7D4),
    currency = Color(0xFFE0A458),
    locked = Color(0xFFA78BE6),
    scrim = Color(0x9E06080E),          // rgba(6,8,14,0.62)
)

val questLightColors = QuestColors(
    groundTop = Color(0xFFFBFAF6),
    ground = Color(0xFFF1F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFECEDF3),
    rule = Color(0x337880A0),           // rgba(120,128,160,0.20)
    inkPrimary = Color(0xFF1F2333),
    inkSecondary = Color(0xFF4B5168),
    inkMuted = Color(0xFF8A90A8),
    earned = Color(0xFF0E8F7A),
    currency = Color(0xFFB4771E),
    locked = Color(0xFF6B4FC7),
    scrim = Color(0x4D141628),          // rgba(20,22,40,0.30)
)

val LocalQuestColors = staticCompositionLocalOf { questDarkColors }

/** Accessor for the semantic tokens: `QuestLogTheme.colors.earned`. */
object QuestLogTheme {
    val colors: QuestColors
        @Composable
        @ReadOnlyComposable
        get() = LocalQuestColors.current
}
```

- [ ] **Step 2: Create `theme/Spacing.kt`**

```kotlin
package com.example.questlog.theme

import androidx.compose.ui.unit.dp

object QuestSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}
```

- [ ] **Step 3: Create `theme/QuestShapes.kt`**

```kotlin
package com.example.questlog.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val QuestShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)
```

- [ ] **Step 4: Add private token consts to `theme/Color.kt`**

Leave every existing `val QuestGold`, `val QuestSlateDark`, … in place (old components still import them). Append nothing else — `QuestColors.kt` holds the new values inline. This step is a no-op checkpoint: confirm `Color.kt` is unchanged and still compiles.

- [ ] **Step 5: Replace `theme/Type.kt`**

```kotlin
package com.example.questlog.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Task 3 swaps this for the bundled Instrument Serif family.
private val Serif = FontFamily.Serif
private val Body = FontFamily.Default

object QuestType {
    val display = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-0.01).sp)
    val displayItalic = display.copy(fontStyle = FontStyle.Italic)
    val wordmark = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 26.sp)
    val screenTitle = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 24.sp)
    val serifNumeral = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 18.sp)
    val bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp)
    val label = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 2.0.sp)
    val caption = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = 1.1.sp)
}

val QuestTypography = Typography(
    displaySmall = QuestType.display,
    titleLarge = QuestType.screenTitle,
    labelLarge = QuestType.serifNumeral,
    bodyLarge = QuestType.bodyLarge,
    bodySmall = QuestType.bodySmall,
    labelMedium = QuestType.label,
    labelSmall = QuestType.caption,
)
```

- [ ] **Step 6: Replace `theme/Theme.kt`**

```kotlin
package com.example.questlog.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private fun schemeFor(c: QuestColors, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = c.earned, onPrimary = c.ground,
        background = c.ground, onBackground = c.inkPrimary,
        surface = c.surface, onSurface = c.inkPrimary,
        surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.inkSecondary,
        outline = c.rule, scrim = c.scrim,
    )
} else {
    lightColorScheme(
        primary = c.earned, onPrimary = c.ground,
        background = c.ground, onBackground = c.inkPrimary,
        surface = c.surface, onSurface = c.inkPrimary,
        surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.inkSecondary,
        outline = c.rule, scrim = c.scrim,
    )
}

@Composable
fun QuestLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) questDarkColors else questLightColors
    CompositionLocalProvider(LocalQuestColors provides colors) {
        MaterialTheme(
            colorScheme = schemeFor(colors, darkTheme),
            typography = QuestTypography,
            shapes = QuestShapes,
            content = content,
        )
    }
}
```

- [ ] **Step 7: Add a preview swatch sheet**

Append to `theme/Theme.kt` (or a new `theme/ThemePreview.kt`):

```kotlin
@androidx.compose.ui.tooling.preview.Preview(name = "Tokens light")
@androidx.compose.ui.tooling.preview.Preview(name = "Tokens dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TokenSwatches() {
    QuestLogTheme {
        val c = QuestLogTheme.colors
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier
                .background(c.ground)
                .padding(16.dp),
        ) {
            listOf(
                "ground" to c.ground, "surface" to c.surface, "rule" to c.rule,
                "inkPrimary" to c.inkPrimary, "inkSecondary" to c.inkSecondary, "inkMuted" to c.inkMuted,
                "earned" to c.earned, "currency" to c.currency, "locked" to c.locked,
            ).forEach { (name, col) ->
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = androidx.compose.ui.Modifier.padding(vertical = 3.dp),
                ) {
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.size(22.dp).background(col),
                    )
                    androidx.compose.material3.Text(
                        "  $name", style = QuestType.bodySmall, color = c.inkPrimary,
                    )
                }
            }
        }
    }
}
```

Add the needed imports (`background`, `padding`, `size`, `Column`, `Row`, `Box`, `dp`).

- [ ] **Step 8: Build and check**

Run: `./gradlew :app:assembleDebug --no-daemon`
Expected: `BUILD SUCCESSFUL`. The existing `DashboardScreen` still compiles (it uses the untouched `Quest*` consts and the old `QuestLogTheme` signature — note `QuestLogTheme` now takes `darkTheme` defaulted, so `MainActivity`'s `QuestLogTheme { … }` call still compiles).
Open `TokenSwatches` preview in Android Studio; confirm both light and dark render with distinct palettes.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/questlog/theme/
git commit -m "Add semantic colour tokens, type scale, spacing, shapes"
```

---

## Task 3: Bundle the Instrument Serif display font

**Files:**
- Add: `app/src/main/res/font/instrument_serif_regular.ttf`
- Add: `app/src/main/res/font/instrument_serif_italic.ttf`
- Add: `app/src/main/res/font/OFL.txt`
- Create: `app/src/main/java/com/example/questlog/theme/QuestFonts.kt`
- Modify: `app/src/main/java/com/example/questlog/theme/Type.kt` (swap `Serif`)

**Interfaces:**
- Produces: `val InstrumentSerif: FontFamily`

- [ ] **Step 1: Download the font files**

*Instrument Serif* is OFL-1.1 licensed. Primary source — the Google Fonts
API static host, which serves each face as a stable `.ttf`:

```bash
mkdir -p app/src/main/res/font && cd app/src/main/res/font

# Regular
curl -sL "https://raw.githubusercontent.com/google/fonts/main/ofl/instrumentserif/InstrumentSerif-Regular.ttf" -o instrument_serif_regular.ttf
# Italic
curl -sL "https://raw.githubusercontent.com/google/fonts/main/ofl/instrumentserif/InstrumentSerif-Italic.ttf" -o instrument_serif_italic.ttf
# Licence
curl -sL "https://raw.githubusercontent.com/google/fonts/main/ofl/instrumentserif/OFL.txt" -o OFL.txt

file instrument_serif_regular.ttf instrument_serif_italic.ttf   # expect "TrueType Font data" / "OpenType font"
```

If that host is unreachable: download the family zip from
`https://fonts.google.com/specimen/Instrument+Serif` in a browser, and copy
`InstrumentSerif-Regular.ttf` → `instrument_serif_regular.ttf`,
`InstrumentSerif-Italic.ttf` → `instrument_serif_italic.ttf`, plus the
`OFL.txt`. Android resource filenames must be lowercase `[a-z0-9_]` — the
target names above already comply. Verify each file is > 20 KB and passes
`file`; a stray HTML error page will otherwise slip in and the build will
fail with a font-parse error.

- [ ] **Step 2: Create `theme/QuestFonts.kt`**

```kotlin
package com.example.questlog.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.questlog.R

/** Bundled display face (OFL-1.1). Regular + Italic only — never bold. */
val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)
```

- [ ] **Step 3: Point the serif tokens at the bundled family**

In `theme/Type.kt` change the one line:

```kotlin
private val Serif = InstrumentSerif
```

(remove `import androidx.compose.ui.text.font.FontFamily` if it is now unused; keep `FontStyle`.)

- [ ] **Step 4: Add a serif preview**

Append to `QuestFonts.kt`:

```kotlin
@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun SerifPreview() {
    QuestLogTheme {
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier
                .background(QuestLogTheme.colors.ground)
                .padding(20.dp),
        ) {
            androidx.compose.material3.Text("Questlog", style = QuestType.wordmark, color = QuestLogTheme.colors.inkPrimary)
            androidx.compose.material3.Text("1h 30m", style = QuestType.display, color = QuestLogTheme.colors.inkPrimary)
            androidx.compose.material3.Text("2h", style = QuestType.displayItalic, color = QuestLogTheme.colors.earned)
        }
    }
}
```

Add imports (`background`, `padding`, `dp`, `Column`).

- [ ] **Step 5: Build and check size**

```bash
./gradlew :app:assembleDebug --no-daemon
./gradlew :app:assembleRelease --no-daemon
ls -la app/build/outputs/apk/release/*.apk
```

Expected: both `BUILD SUCCESSFUL`. The release APK grows by roughly the two `.ttf` sizes (~80–150 KB total). Open `SerifPreview` — the wordmark and numerals render in the serif, "2h" is italic.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/font/ app/src/main/java/com/example/questlog/theme/QuestFonts.kt app/src/main/java/com/example/questlog/theme/Type.kt
git commit -m "Bundle Instrument Serif display font"
```

---

## Task 4: `QuestIcons`

**Files:**
- Create: `app/src/main/java/com/example/questlog/theme/QuestIcons.kt`

**Interfaces:**
- Produces: `object QuestIcons { val Back, Refresh, Check, ArrowRight, Crown, Lock: ImageVector }`

- [ ] **Step 1: Create `theme/QuestIcons.kt`**

```kotlin
package com.example.questlog.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Minimal 24dp line glyphs. Tint at the call site via Icon(tint = …). */
object QuestIcons {

    private fun line(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    val Back: ImageVector = line("Back") { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }

    val ArrowRight: ImageVector = line("ArrowRight") {
        moveTo(5f, 12f); lineTo(19f, 12f); moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f)
    }

    val Check: ImageVector = line("Check") { moveTo(5f, 13f); lineTo(10f, 18f); lineTo(19f, 6f) }

    val Refresh: ImageVector = line("Refresh") {
        moveTo(20f, 11f)
        arcTo(8f, 8f, 0f, true, false, 20.5f, 14f)
        moveTo(20f, 5f); lineTo(20f, 11f); lineTo(14f, 11f)
    }

    val Crown: ImageVector = line("Crown") {
        moveTo(4f, 18f); lineTo(20f, 18f)
        moveTo(4f, 18f); lineTo(4f, 8f); lineTo(9f, 12f); lineTo(12f, 6f); lineTo(15f, 12f); lineTo(20f, 8f); lineTo(20f, 18f)
    }

    val Lock: ImageVector = line("Lock") {
        moveTo(6f, 11f); lineTo(18f, 11f); lineTo(18f, 20f); lineTo(6f, 20f); close()
        moveTo(8f, 11f); lineTo(8f, 8f)
        arcTo(4f, 4f, 0f, true, true, 16f, 8f)
        lineTo(16f, 11f)
    }
}
```

- [ ] **Step 2: Preview grid**

Append:

```kotlin
@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun IconsPreview() {
    QuestLogTheme {
        androidx.compose.foundation.layout.Row(
            modifier = androidx.compose.ui.Modifier
                .background(QuestLogTheme.colors.ground)
                .padding(16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            listOf(QuestIcons.Back, QuestIcons.ArrowRight, QuestIcons.Check, QuestIcons.Refresh, QuestIcons.Crown, QuestIcons.Lock).forEach {
                androidx.compose.material3.Icon(it, contentDescription = null, tint = QuestLogTheme.colors.inkPrimary)
            }
        }
    }
}
```

Add imports.

- [ ] **Step 3: Build and check**

Run: `./gradlew :app:assembleDebug --no-daemon` → `BUILD SUCCESSFUL`. Open `IconsPreview`; six recognisable glyphs, tinted `inkPrimary`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/questlog/theme/QuestIcons.kt
git commit -m "Add QuestIcons vector glyph set"
```

---

## Task 5: Common primitives — Hairline, Pill, SectionHeader, QuestScaffold

**Files:**
- Create: `app/src/main/java/com/example/questlog/ui/common/Hairline.kt`
- Create: `app/src/main/java/com/example/questlog/ui/common/Pill.kt`
- Create: `app/src/main/java/com/example/questlog/ui/common/SectionHeader.kt`
- Create: `app/src/main/java/com/example/questlog/ui/common/QuestScaffold.kt`

**Interfaces:**
- Consumes: `QuestLogTheme.colors`, `QuestType`, `QuestSpacing`.
- Produces:
  - `@Composable fun Hairline(modifier: Modifier = Modifier)`
  - `@Composable fun Pill(text: String, filled: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier)`
  - `@Composable fun SectionHeader(label: String, count: String, modifier: Modifier = Modifier)`
  - `@Composable fun QuestScaffold(header: @Composable () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`

- [ ] **Step 1: `ui/common/Hairline.kt`**

```kotlin
package com.example.questlog.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestLogTheme

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(QuestLogTheme.colors.rule),
    )
}
```

- [ ] **Step 2: `ui/common/Pill.kt`**

```kotlin
package com.example.questlog.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestType

/** Small rounded status/action chip. `filled` = earned fill; else a locked outline. */
@Composable
fun Pill(
    text: String,
    filled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = QuestLogTheme.colors
    val shape = RoundedCornerShape(999.dp)
    var m = modifier.clip(shape)
    m = if (filled) m.background(c.earned) else m.border(BorderStroke(1.dp, c.locked), shape)
    if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick)
    m = m.padding(horizontal = 10.dp, vertical = 5.dp)
    Text(
        text = text.uppercase(),
        style = QuestType.caption,
        color = if (filled) c.ground else c.locked,
        modifier = m,
    )
}
```

- [ ] **Step 3: `ui/common/SectionHeader.kt`**

```kotlin
package com.example.questlog.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestType

/** An uppercase section label with a right-aligned serif count, e.g. "TODAY'S QUESTS   2 / 3". */
@Composable
fun SectionHeader(label: String, count: String, modifier: Modifier = Modifier) {
    val c = QuestLogTheme.colors
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(label.uppercase(), style = QuestType.label, color = c.inkMuted)
        Text(count, style = QuestType.serifNumeral, color = c.inkMuted)
    }
}
```

- [ ] **Step 4: `ui/common/QuestScaffold.kt`**

```kotlin
package com.example.questlog.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestSpacing

/**
 * Every screen's frame: the dusk radial-gradient ground, system-bar insets, a fixed
 * [header] slot, then scroll-free [content] laid out in a Column with lg horizontal padding.
 */
@Composable
fun QuestScaffold(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = QuestLogTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(c.ground)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(c.groundTop, c.ground),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.height * 0.9f,
                    ),
                    size = Size(size.width, size.height * 0.6f),
                )
            }
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = QuestSpacing.lg),
    ) {
        header()
        content()
    }
}
```

- [ ] **Step 5: Previews**

Add to `QuestScaffold.kt`:

```kotlin
@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.ui.tooling.preview.Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PrimitivesPreview() {
    QuestLogTheme {
        QuestScaffold(header = {
            androidx.compose.material3.Text("Questlog", style = com.example.questlog.theme.QuestType.wordmark, color = QuestLogTheme.colors.inkPrimary)
        }) {
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(QuestSpacing.md))
            Hairline()
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(QuestSpacing.md))
            SectionHeader("Today's quests", "2 / 3")
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(QuestSpacing.md))
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(QuestSpacing.sm)) {
                Pill("Get Pro", filled = false, onClick = {})
                Pill("Pro", filled = true, onClick = null)
            }
        }
    }
}
```

Add `height` import.

- [ ] **Step 6: Build and check**

Run: `./gradlew :app:assembleDebug --no-daemon` → `BUILD SUCCESSFUL`. `PrimitivesPreview` shows the gradient ground, a hairline, the section header, and both pill styles, in light and dark.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/questlog/ui/common/
git commit -m "Add common UI primitives: Hairline, Pill, SectionHeader, QuestScaffold"
```

---

## Task 6: `ProgressRing` + reduced-motion helper

**Files:**
- Create: `app/src/main/java/com/example/questlog/ui/common/Motion.kt`
- Create: `app/src/main/java/com/example/questlog/ui/today/ProgressRing.kt`

**Interfaces:**
- Consumes: `QuestLogTheme.colors`.
- Produces:
  - `@Composable fun reducedMotion(): Boolean`
  - `@Composable fun ProgressRing(fraction: Float, modifier: Modifier = Modifier, strokeWidth: Dp = 3.dp, content: @Composable BoxScope.() -> Unit)`

- [ ] **Step 1: `ui/common/Motion.kt`**

```kotlin
package com.example.questlog.ui.common

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** True when the OS "remove animations" setting is on. */
@Composable
fun reducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
```

- [ ] **Step 2: `ui/today/ProgressRing.kt`**

```kotlin
package com.example.questlog.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.ui.common.reducedMotion

/**
 * A thin arc that sweeps clockwise from 12 o'clock to [fraction] of a full turn, on a
 * faint full-circle track. Animates once on first composition unless reduced motion is on.
 * [content] is centred inside the ring.
 */
@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 3.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val target = fraction.coerceIn(0f, 1f)
    val reduce = reducedMotion()
    val swept = remember { Animatable(if (reduce) target else 0f) }
    LaunchedEffect(target, reduce) {
        if (reduce) swept.snapTo(target)
        else swept.animateTo(target, tween(700, easing = FastOutSlowInEasing))
    }
    val c = QuestLogTheme.colors
    Box(
        modifier
            .size(56.dp)
            .drawBehind {
                val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                val inset = strokeWidth.toPx() / 2f
                val arcSize = Size(size.width - strokeWidth.toPx(), size.height - strokeWidth.toPx())
                drawArc(
                    color = c.rule, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = stroke,
                )
                drawArc(
                    color = c.earned, startAngle = -90f, sweepAngle = 360f * swept.value, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = stroke,
                )
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}
```

- [ ] **Step 3: Preview**

Append:

```kotlin
@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun RingPreview() {
    QuestLogTheme {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .androidx.compose.foundation.background(QuestLogTheme.colors.ground)
                .padding(16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            listOf(0f, 0.5f, 0.85f, 1f).forEach { f ->
                ProgressRing(fraction = f) {
                    androidx.compose.material3.Text(
                        "6d", style = com.example.questlog.theme.QuestType.serifNumeral,
                        color = QuestLogTheme.colors.earned,
                    )
                }
            }
        }
    }
}
```

> The fully-qualified `Modifier` chain above is ugly on purpose to keep the
> snippet import-free; when writing the file, add
> `import androidx.compose.foundation.background` /
> `import androidx.compose.foundation.layout.padding` etc. and write
> `Modifier.background(QuestLogTheme.colors.ground).padding(16.dp)` normally.

- [ ] **Step 4: Build and check**

Run: `./gradlew :app:assembleDebug --no-daemon` → `BUILD SUCCESSFUL`. `RingPreview` shows four rings at increasing sweep with a faint track; the mint arc starts at 12 o'clock.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/questlog/ui/common/Motion.kt app/src/main/java/com/example/questlog/ui/today/ProgressRing.kt
git commit -m "Add ProgressRing and reduced-motion helper"
```

---

## Task 7: `TodayHero` and `LevelBar`

**Files:**
- Create: `app/src/main/java/com/example/questlog/ui/today/TodayHero.kt`
- Create: `app/src/main/java/com/example/questlog/ui/today/LevelBar.kt`

**Interfaces:**
- Consumes: `ProgressRing`, `formatReclaimed`, `formatMultiplier`, `ringFraction`, `levelTitle`, `com.questlog.util.DetoxBudget.DEFAULT_DAILY_BUDGET_MS`, `com.questlog.util.TimeConversion.xpProgress`, `com.questlog.domain.model.PlayerStats`.
- Produces:
  - `@Composable fun TodayHero(stats: PlayerStats, modifier: Modifier = Modifier)`
  - `@Composable fun LevelBar(stats: PlayerStats, modifier: Modifier = Modifier)`

- [ ] **Step 1: `ui/today/TodayHero.kt`**

```kotlin
package com.example.questlog.ui.today

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.common.reducedMotion
import com.example.questlog.ui.format.formatMultiplier
import com.example.questlog.ui.format.formatReclaimed
import com.example.questlog.ui.format.ringFraction
import com.questlog.domain.model.PlayerStats
import com.questlog.util.DetoxBudget

@Composable
fun TodayHero(stats: PlayerStats, modifier: Modifier = Modifier) {
    val c = QuestLogTheme.colors
    val reduce = reducedMotion()

    val targetMinutes = (stats.todaySavedMs / 60_000L).toInt()
    val animatedMinutes by animateIntAsState(
        targetValue = targetMinutes,
        animationSpec = tween(600),
        label = "reclaimedMinutes",
    )
    val shownMinutes = if (reduce) targetMinutes else animatedMinutes
    val reclaimed = formatReclaimed(shownMinutes * 60_000L)

    Column(modifier) {
        Text("Reclaimed today".uppercase(), style = QuestType.label, color = c.inkMuted)
        Spacer(Modifier.height(QuestSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                fraction = ringFraction(stats.todaySavedMs, DetoxBudget.DEFAULT_DAILY_BUDGET_MS),
            ) {
                if (stats.consecutiveDetoxDays == 0) {
                    Text("—", style = QuestType.serifNumeral, color = c.inkMuted)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${stats.consecutiveDetoxDays}d", style = QuestType.serifNumeral, color = c.earned)
                        Text(formatMultiplier(stats.streakMultiplier), style = QuestType.caption, color = c.earned)
                    }
                }
            }
            Spacer(Modifier.width(QuestSpacing.lg))
            Text(
                buildAnnotatedString {
                    reclaimed.hours?.let {
                        withStyle(QuestType.displayItalic.toSpanStyle().copy(color = c.earned)) { append(it) }
                        append(" ")
                    }
                    withStyle(QuestType.display.toSpanStyle().copy(color = c.inkPrimary)) { append(reclaimed.minutes) }
                },
            )
        }
    }
}
```

> Remove the unused `appendInlineContent` import if the linter flags it.

- [ ] **Step 2: `ui/today/LevelBar.kt`**

```kotlin
package com.example.questlog.ui.today

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.format.levelTitle
import com.questlog.domain.model.PlayerStats
import com.questlog.util.TimeConversion

@Composable
fun LevelBar(stats: PlayerStats, modifier: Modifier = Modifier) {
    val c = QuestLogTheme.colors
    val progress by animateFloatAsState(
        targetValue = TimeConversion.xpProgress(stats.xp),
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "xp",
    )
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Level ${stats.level} · ${levelTitle(stats.level)}", style = QuestType.bodySmall, color = c.inkMuted)
            Text("${(progress * 100).toInt()}%", style = QuestType.serifNumeral, color = c.inkMuted)
        }
        Spacer(Modifier.height(QuestSpacing.sm))
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(c.rule),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(c.inkSecondary),
            )
        }
    }
}
```

- [ ] **Step 3: Previews**

Append to `TodayHero.kt` a `fakeStats()` and a dual `@Preview` that stacks `TodayHero` + `LevelBar` on `QuestLogTheme.colors.ground`:

```kotlin
internal fun fakeStats(
    level: Int = 6, xp: Long = 1950L, xpToNext: Long = 2100L, gold: Long = 250L,
    gems: Long = 0L, streakDays: Int = 6, mult: Float = 2.0f, savedMs: Long = 90 * 60_000L,
    shieldReady: Boolean = true,
) = PlayerStats(level, xp, xpToNext, gold, gems, streakDays, mult, savedMs, shieldReady)

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.ui.tooling.preview.Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HeroPreview() {
    QuestLogTheme {
        Column(
            Modifier
                .background(QuestLogTheme.colors.ground)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            TodayHero(fakeStats())
            Spacer(Modifier.height(16.dp))
            LevelBar(fakeStats())
        }
    }
}
```

Add imports (`background`, `fillMaxWidth`, `padding`, `dp`).

- [ ] **Step 4: Build and check**

Run: `./gradlew :app:assembleDebug --no-daemon` → `BUILD SUCCESSFUL`. `HeroPreview`: ring with "6d / 2.0×" centred, "**1h** 30m" with italic mint hours, then the level line + thin XP bar. Light and dark.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/questlog/ui/today/TodayHero.kt app/src/main/java/com/example/questlog/ui/today/LevelBar.kt
git commit -m "Add TodayHero and LevelBar"
```

---

## Task 8: `QuestLedger`

**Files:**
- Create: `app/src/main/java/com/example/questlog/ui/today/QuestLedger.kt`

**Interfaces:**
- Consumes: `SectionHeader`, `QuestIcons.Check`, `com.questlog.domain.model.DailyQuest`.
- Produces: `@Composable fun QuestLedger(quests: List<DailyQuest>, modifier: Modifier = Modifier)`

- [ ] **Step 1: `ui/today/QuestLedger.kt`**

```kotlin
package com.example.questlog.ui.today

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestIcons
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.common.SectionHeader
import com.questlog.domain.model.DailyQuest

@Composable
fun QuestLedger(quests: List<DailyQuest>, modifier: Modifier = Modifier) {
    val done = quests.count { it.isCompleted }
    Column(modifier.fillMaxWidth()) {
        SectionHeader("Today's quests", "$done / ${quests.size}")
        Spacer(Modifier.height(QuestSpacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(QuestSpacing.md)) {
            quests.forEachIndexed { i, q ->
                QuestLedgerRow(index = i + 1, quest = q)
            }
        }
    }
}

@Composable
private fun QuestLedgerRow(index: Int, quest: DailyQuest) {
    val c = QuestLogTheme.colors
    val boxColor by animateColorAsState(
        targetValue = if (quest.isCompleted) c.earned else Color.Transparent,
        animationSpec = tween(200),
        label = "questBox",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "%02d".format(index),
            style = QuestType.serifNumeral,
            color = c.inkMuted,
            modifier = Modifier.width(18.dp),
        )
        Spacer(Modifier.width(QuestSpacing.sm))
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(boxColor)
                .border(1.5.dp, if (quest.isCompleted) c.earned else c.rule, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (quest.isCompleted) {
                Icon(QuestIcons.Check, contentDescription = null, tint = c.ground, modifier = Modifier.size(11.dp))
            }
        }
        Spacer(Modifier.width(QuestSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(quest.title, style = QuestType.bodyLarge, color = c.inkSecondary)
            Text(quest.description, style = QuestType.bodySmall, color = c.inkMuted)
        }
        Spacer(Modifier.width(QuestSpacing.sm))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "+${quest.xpReward}",
                style = QuestType.bodySmall,
                color = if (quest.isCompleted) c.earned else c.inkSecondary,
                textAlign = TextAlign.End,
            )
            Text("+${quest.goldReward} g", style = QuestType.caption, color = c.currency)
        }
    }
}
```

- [ ] **Step 2: Preview**

```kotlin
@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.ui.tooling.preview.Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LedgerPreview() {
    QuestLogTheme {
        Column(
            Modifier.background(QuestLogTheme.colors.ground).padding(16.dp),
        ) {
            QuestLedger(
                listOf(
                    DailyQuest("a", "Budget Guardian", "Distraction apps under 30 min", 250, 60, true, ""),
                    DailyQuest("b", "Master Builder", "Build two in one day", 300, 70, true, ""),
                    DailyQuest("c", "Dawn Discipline", "Nothing before 9am", 150, 30, false, ""),
                ),
            )
        }
    }
}
```

- [ ] **Step 3: Build and check**

Run: `./gradlew :app:assembleDebug --no-daemon` → `BUILD SUCCESSFUL`. `LedgerPreview`: `01/02/03` serif numbers, filled mint checkboxes with a tick on the first two, reward columns; gold suffix in `currency`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/questlog/ui/today/QuestLedger.kt
git commit -m "Add QuestLedger"
```

---

## Task 9: `RealmStrip`

**Files:**
- Create: `app/src/main/java/com/example/questlog/ui/today/RealmStrip.kt`

**Interfaces:**
- Consumes: `SectionHeader`, `QuestIcons.ArrowRight`, `com.questlog.domain.model.CityTile`.
- Produces: `@Composable fun RealmStrip(tiles: List<CityTile>, onOpen: () -> Unit, modifier: Modifier = Modifier)`

- [ ] **Step 1: `ui/today/RealmStrip.kt`**

```kotlin
package com.example.questlog.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestIcons
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.common.SectionHeader
import com.questlog.domain.model.CityTile

@Composable
fun RealmStrip(tiles: List<CityTile>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = QuestLogTheme.colors
    val built = tiles.count { it.isOwned }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = "Your realm, $built of ${tiles.size} built. Open." }
            .padding(vertical = QuestSpacing.xs),
    ) {
        SectionHeader("Your realm", "$built / ${tiles.size}")
        Spacer(Modifier.height(QuestSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            tiles.forEach { tile ->
                val barMod = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                Box(
                    when {
                        tile.isOwned -> barMod.background(c.earned)
                        tile.isPremium -> barMod.border(1.dp, c.locked, RoundedCornerShape(3.dp))
                        else -> barMod.background(c.rule)
                    },
                )
                Spacer(Modifier.width(QuestSpacing.xs))
            }
            Spacer(Modifier.width(QuestSpacing.xs))
            Text("View".uppercase(), style = QuestType.caption, color = c.inkMuted)
            Spacer(Modifier.width(QuestSpacing.xs))
            Icon(QuestIcons.ArrowRight, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(14.dp))
        }
    }
}
```

- [ ] **Step 2: Preview**

```kotlin
@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.ui.tooling.preview.Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RealmStripPreview() {
    QuestLogTheme {
        Column(Modifier.background(QuestLogTheme.colors.ground).padding(16.dp)) {
            RealmStrip(fakeTiles(), onOpen = {})
        }
    }
}

internal fun fakeTiles() = listOf(
    CityTile("town_hall", "Town Hall", 1, false, true, 0),
    CityTile("market", "Market", 1, false, true, 50),
    CityTile("library", "Library", 2, false, true, 120),
    CityTile("garden", "Zen Garden", 2, false, false, 200),
    CityTile("castle", "Crystal Castle", 3, true, false, 0),
    CityTile("fountain", "Aurora Fountain", 3, true, false, 0),
)
```

- [ ] **Step 3: Build and check**

Run: `./gradlew :app:assembleDebug --no-daemon` → `BUILD SUCCESSFUL`. `RealmStripPreview`: four mint bars, two hollow violet-outlined bars, then "VIEW →".

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/questlog/ui/today/RealmStrip.kt
git commit -m "Add RealmStrip"
```

---

## Task 10: `TodayScreen`

**Files:**
- Create: `app/src/main/java/com/example/questlog/ui/today/TodayScreen.kt`

**Interfaces:**
- Consumes: `QuestScaffold`, `Hairline`, `Pill`, `QuestIcons.Refresh`, `TodayHero`, `LevelBar`, `QuestLedger`, `RealmStrip`, `fakeStats()`, `fakeTiles()`. `com.example.questlog.ui.dashboard.DashboardUiState`.
- Produces:
  ```kotlin
  @Composable fun TodayScreen(
      state: DashboardUiState,
      onRefresh: () -> Unit,
      onOpenPaywall: () -> Unit,
      onOpenRealm: () -> Unit,
      modifier: Modifier = Modifier,
  )
  ```

- [ ] **Step 1: `ui/today/TodayScreen.kt`**

```kotlin
package com.example.questlog.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestIcons
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.common.Hairline
import com.example.questlog.ui.common.Pill
import com.example.questlog.ui.common.QuestScaffold
import com.example.questlog.ui.dashboard.DashboardUiState

@Composable
fun TodayScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenRealm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = QuestLogTheme.colors
    QuestScaffold(
        modifier = modifier,
        header = {
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Questlog", style = QuestType.wordmark, color = c.inkPrimary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QuestSpacing.sm)) {
                    if (state.isPremium) {
                        Pill("Pro", filled = true, onClick = null)
                    } else {
                        Pill("Get Pro", filled = false, onClick = onOpenPaywall)
                    }
                    IconButton(onClick = onRefresh) {
                        if (state.isLoading) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = c.earned, strokeWidth = 2.dp)
                        } else {
                            Icon(QuestIcons.Refresh, contentDescription = "Refresh", tint = c.inkMuted)
                        }
                    }
                }
            }
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(QuestSpacing.lg),
        ) {
            Hairline()
            TodayHero(state.stats)
            if (state.isPremium) {
                Text(
                    if (state.stats.streakFreezeReady) "Shield ready" else "Shield recharging",
                    style = QuestType.caption,
                    color = if (state.stats.streakFreezeReady) c.earned else c.inkMuted,
                )
            }
            Hairline()
            LevelBar(state.stats)
            Hairline()
            QuestLedger(state.dailyQuests)
            Hairline()
            RealmStrip(state.cityTiles, onOpen = onOpenRealm)
            Spacer(Modifier.height(QuestSpacing.xxl))
        }
    }
}
```

> Note: `QuestScaffold`'s Column already applies `lg` horizontal padding; the inner scroll Column inherits it.

- [ ] **Step 2: Preview (light + dark, premium + free)**

```kotlin
@androidx.compose.ui.tooling.preview.Preview(name = "Today free")
@androidx.compose.ui.tooling.preview.Preview(name = "Today dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TodayPreview() {
    QuestLogTheme {
        TodayScreen(
            state = DashboardUiState(
                isLoading = false,
                stats = fakeStats(),
                cityTiles = fakeTiles(),
                dailyQuests = listOf(
                    com.questlog.domain.model.DailyQuest("a", "Budget Guardian", "Distraction apps under 30 min", 250, 60, true, ""),
                    com.questlog.domain.model.DailyQuest("b", "Master Builder", "Build two in one day", 300, 70, true, ""),
                    com.questlog.domain.model.DailyQuest("c", "Dawn Discipline", "Nothing before 9am", 150, 30, false, ""),
                ),
                isPremium = false,
            ),
            onRefresh = {}, onOpenPaywall = {}, onOpenRealm = {},
        )
    }
}
```

- [ ] **Step 3: Build and check**

Run: `./gradlew :app:assembleDebug --no-daemon` → `BUILD SUCCESSFUL`. `TodayPreview`: the full screen composes — header, hairline-separated hero / level / quests / realm strip — in both themes.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/questlog/ui/today/TodayScreen.kt
git commit -m "Add TodayScreen"
```

---

## Task 11: `RealmScreen` and `RealmTile`

**Files:**
- Create: `app/src/main/java/com/example/questlog/ui/realm/RealmTile.kt`
- Create: `app/src/main/java/com/example/questlog/ui/realm/RealmScreen.kt`

**Interfaces:**
- Consumes: `QuestScaffold`, `Hairline`, `QuestIcons.Back`, `com.questlog.domain.model.CityTile`, `com.example.questlog.ui.today.fakeTiles()`.
- Produces:
  - `@Composable fun RealmTile(tile: CityTile, onClick: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun RealmScreen(tiles: List<CityTile>, gold: Long, onBack: () -> Unit, onTileClick: (CityTile) -> Unit, modifier: Modifier = Modifier)`

- [ ] **Step 1: `ui/realm/RealmTile.kt`**

```kotlin
package com.example.questlog.ui.realm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.questlog.domain.model.CityTile

@Composable
fun RealmTile(tile: CityTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = QuestLogTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val tier = when (tile.tier) { 1 -> "I"; 2 -> "II"; else -> "III" }
    val (statusText, statusColor) = when {
        tile.isOwned -> "Built" to c.earned
        tile.isPremium -> "Pro" to c.locked
        tile.goldCost == 0L -> "Free" to c.currency
        else -> "${tile.goldCost} g" to c.currency
    }
    Column(
        modifier
            .clip(shape)
            .background(c.surface)
            .then(
                if (tile.isPremium && !tile.isOwned) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = c.locked,
                            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
                        )
                    }
                } else {
                    Modifier.border(1.dp, if (tile.isOwned) c.earned.copy(alpha = 0.3f) else c.rule, shape)
                },
            )
            .clickable(onClick = onClick)
            .padding(QuestSpacing.md)
            .fillMaxWidth()
            .height(84.dp),
    ) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
            Text(tile.displayName, style = QuestType.bodyLarge, color = c.inkPrimary, modifier = Modifier.weight(1f))
            Text(tier, style = QuestType.serifNumeral, color = c.inkMuted)
        }
        Spacer(Modifier.weight(1f))
        Text(statusText.uppercase(), style = QuestType.caption, color = statusColor)
    }
}
```

- [ ] **Step 2: `ui/realm/RealmScreen.kt`**

```kotlin
package com.example.questlog.ui.realm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestIcons
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.common.Hairline
import com.example.questlog.ui.common.QuestScaffold
import com.questlog.domain.model.CityTile

@Composable
fun RealmScreen(
    tiles: List<CityTile>,
    gold: Long,
    onBack: () -> Unit,
    onTileClick: (CityTile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = QuestLogTheme.colors
    val built = tiles.count { it.isOwned }
    QuestScaffold(
        modifier = modifier,
        header = {
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(QuestIcons.Back, contentDescription = "Back", tint = c.inkPrimary)
                    }
                    Text("Your Realm", style = QuestType.screenTitle, color = c.inkPrimary)
                }
                Text("$gold g", style = QuestType.serifNumeral, color = c.currency)
            }
        },
    ) {
        Hairline()
        Spacer(Modifier.height(QuestSpacing.md))
        Text("$built of ${tiles.size} built".uppercase(), style = QuestType.label, color = c.inkMuted)
        Spacer(Modifier.height(QuestSpacing.md))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(QuestSpacing.md),
            verticalArrangement = Arrangement.spacedBy(QuestSpacing.md),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = QuestSpacing.xxl),
        ) {
            items(tiles, key = { it.itemId }) { tile ->
                RealmTile(tile = tile, onClick = { onTileClick(tile) })
            }
        }
    }
}
```

- [ ] **Step 3: Previews**

```kotlin
@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.ui.tooling.preview.Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RealmPreview() {
    QuestLogTheme {
        RealmScreen(
            tiles = com.example.questlog.ui.today.fakeTiles(),
            gold = 250L,
            onBack = {}, onTileClick = {},
        )
    }
}
```

- [ ] **Step 4: Build and check**

Run: `./gradlew :app:assembleDebug --no-daemon` → `BUILD SUCCESSFUL`. `RealmPreview`: back arrow + "Your Realm" + "250 g"; a 2-col grid; owned tiles mint-bordered, Pro tiles dashed violet, buyable tiles show "200 G" in `currency`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/questlog/ui/realm/
git commit -m "Add RealmScreen and RealmTile"
```

---

## Task 12: `PaywallDialog` (replaces `PaywallModal`)

**Files:**
- Create: `app/src/main/java/com/example/questlog/ui/paywall/PaywallDialog.kt`
- Delete: `app/src/main/java/com/example/questlog/ui/components/PaywallModal.kt`

**Interfaces:**
- Consumes: `QuestIcons.Crown`, `Hairline`, `QuestLogTheme`.
- Produces: `@Composable fun PaywallDialog(onDismiss: () -> Unit, onUnlockPro: () -> Unit)`

- [ ] **Step 1: `ui/paywall/PaywallDialog.kt`**

```kotlin
package com.example.questlog.ui.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.questlog.theme.QuestIcons
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestShapes
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.common.Hairline

@Composable
fun PaywallDialog(onDismiss: () -> Unit, onUnlockPro: () -> Unit) {
    val c = QuestLogTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(QuestShapes.large)
                .background(c.surface)
                .padding(QuestSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QuestSpacing.md),
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(42.dp).clip(CircleShape).border(1.dp, c.locked, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(QuestIcons.Crown, contentDescription = null, tint = c.locked, modifier = Modifier.size(20.dp))
            }
            Text("Questlog Pro", style = QuestType.screenTitle, color = c.inkPrimary)
            Text("Architect of the High Realm".uppercase(), style = QuestType.caption, color = c.locked)
            Hairline()
            PerkRow(mark = "×2", title = "Double rewards", desc = "Every minute of focus time pays twice the XP and gold")
            PerkRow(mark = "◇", title = "Streak Freeze", desc = "Protects your streak through one missed day a week")
            PerkRow(mark = "▢", title = "Two realm buildings", desc = "Crystal Castle and Aurora Fountain")
            Spacer(Modifier.height(QuestSpacing.xs))
            Button(
                onClick = onUnlockPro,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = QuestShapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = c.earned, contentColor = c.ground),
            ) {
                Text("Unlock — \$4.99 / month", style = QuestType.bodyLarge)
            }
            TextButton(onClick = onDismiss) {
                Text("Maybe later".uppercase(), style = QuestType.caption, color = c.inkMuted)
            }
            Text(
                "Local receipt validation via RevenueCat SDK. Offline accessible.",
                style = QuestType.caption, color = c.inkMuted, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PerkRow(mark: String, title: String, desc: String) {
    val c = QuestLogTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(mark, style = QuestType.serifNumeral, color = c.earned, modifier = Modifier.width(24.dp))
        Spacer(Modifier.width(QuestSpacing.sm))
        Column {
            Text(title, style = QuestType.bodyLarge, color = c.inkPrimary)
            Text(desc, style = QuestType.bodySmall, color = c.inkMuted)
        }
    }
}
```

- [ ] **Step 2: Delete the old modal**

```bash
git rm app/src/main/java/com/example/questlog/ui/components/PaywallModal.kt
```

`DashboardScreen.kt` still references `PaywallModal` — it will not compile yet. That is expected; Task 13 replaces `DashboardScreen`. To keep this task's build green, **temporarily** update the one call site in `ui/dashboard/DashboardScreen.kt`:

```kotlin
// import com.example.questlog.ui.components.PaywallModal   -> remove
import com.example.questlog.ui.paywall.PaywallDialog
// PaywallModal(onDismiss = …, onUnlockPro = …)             -> PaywallDialog(onDismiss = …, onUnlockPro = …)
```

The parameter names match (`onDismiss`, `onUnlockPro`), so this is a rename at the call site only.

- [ ] **Step 3: Preview**

```kotlin
@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.ui.tooling.preview.Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PaywallPreview() {
    QuestLogTheme { PaywallDialog(onDismiss = {}, onUnlockPro = {}) }
}
```

- [ ] **Step 4: Build and check**

Run: `./gradlew :app:assembleDebug --no-daemon` → `BUILD SUCCESSFUL`. `PaywallPreview`: crown in a violet ring, serif "Questlog Pro", three `PerkRow`s with mint marks, mint unlock button.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/questlog/ui/paywall/PaywallDialog.kt app/src/main/java/com/example/questlog/ui/dashboard/DashboardScreen.kt
git commit -m "Add PaywallDialog, retire PaywallModal"
```

---

## Task 13: `QuestLogRoot` + wire `MainActivity`; retire `DashboardScreen`

**Files:**
- Create: `app/src/main/java/com/example/questlog/ui/QuestLogRoot.kt`
- Modify: `app/src/main/java/com/example/questlog/MainActivity.kt`
- Delete: `app/src/main/java/com/example/questlog/ui/dashboard/DashboardScreen.kt`

**Interfaces:**
- Consumes: `TodayScreen`, `RealmScreen`, `PaywallDialog`, `com.example.questlog.ui.dashboard.DashboardViewModel`, `DashboardIntent`.
- Produces: `@Composable fun QuestLogRoot(viewModel: DashboardViewModel)`

- [ ] **Step 1: `ui/QuestLogRoot.kt`**

```kotlin
package com.example.questlog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.questlog.ui.common.reducedMotion
import com.example.questlog.ui.dashboard.DashboardIntent
import com.example.questlog.ui.dashboard.DashboardViewModel
import com.example.questlog.ui.paywall.PaywallDialog
import com.example.questlog.ui.realm.RealmScreen
import com.example.questlog.ui.today.TodayScreen

private enum class Screen { Today, Realm }

@Composable
fun QuestLogRoot(viewModel: DashboardViewModel) {
    val state by viewModel.uiState.collectAsState()
    var screen by rememberSaveable { mutableStateOf(Screen.Today) }
    val snackbarHostState = remember { SnackbarHostState() }
    val reduce = reducedMotion()

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onIntent(DashboardIntent.DismissSnackbar)
        }
    }

    BackHandler(enabled = screen == Screen.Realm) { screen = Screen.Today }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (reduce) {
                    fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                } else if (targetState == Screen.Realm) {
                    (slideInHorizontally(tween(250)) { it } + fadeIn(tween(250))) togetherWith
                        (slideOutHorizontally(tween(250)) { -it / 4 } + fadeOut(tween(250)))
                } else {
                    (slideInHorizontally(tween(250)) { -it / 4 } + fadeIn(tween(250))) togetherWith
                        (slideOutHorizontally(tween(250)) { it } + fadeOut(tween(250)))
                }
            },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Today -> TodayScreen(
                    state = state,
                    onRefresh = { viewModel.onIntent(DashboardIntent.Refresh) },
                    onOpenPaywall = { viewModel.onIntent(DashboardIntent.OpenPaywall) },
                    onOpenRealm = { screen = Screen.Realm },
                )
                Screen.Realm -> RealmScreen(
                    tiles = state.cityTiles,
                    gold = state.stats.gold,
                    onBack = { screen = Screen.Today },
                    onTileClick = { viewModel.onIntent(DashboardIntent.Purchase(it)) },
                )
            }
        }

        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))

        if (state.showPaywall) {
            PaywallDialog(
                onDismiss = { viewModel.onIntent(DashboardIntent.DismissPaywall) },
                onUnlockPro = { viewModel.onIntent(DashboardIntent.UnlockProDemo) },
            )
        }
    }
}
```

- [ ] **Step 2: Update `MainActivity.kt`**

Replace the `setContent { … }` body:

```kotlin
setContent {
    QuestLogTheme {
        val viewModel: DashboardViewModel = koinViewModel()
        QuestLogRoot(viewModel = viewModel)
    }
}
```

Update imports: drop `androidx.compose.material3.MaterialTheme`, `androidx.compose.material3.Surface`, `androidx.compose.foundation.layout.fillMaxSize`, `com.example.questlog.ui.dashboard.DashboardScreen`; add `com.example.questlog.ui.QuestLogRoot`. Keep `enableEdgeToEdge()`.

- [ ] **Step 3: Delete `DashboardScreen.kt`**

```bash
git rm app/src/main/java/com/example/questlog/ui/dashboard/DashboardScreen.kt
```

`DashboardViewModel.kt` stays. Nothing else imports `DashboardScreen`.

- [ ] **Step 4: Build, install, walk it**

```bash
./gradlew :app:assembleDebug --no-daemon
```
Expected: `BUILD SUCCESSFUL`. Then on a running emulator:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.questlog.app/com.example.questlog.MainActivity
```
Verify: Today screen renders; tapping the realm strip slides to Realm; system back returns; tapping a buyable tile shows the purchase snackbar and the tile flips to Built; tapping a Pro tile opens the paywall; "Get Pro" opens the paywall; "Unlock" flips the header to "Pro" and shows the shield line. Toggle the emulator to light mode (Settings → Display) and re-open — the dawn palette applies.

- [ ] **Step 5: Run the JVM test suite**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --rerun-tasks` — `DashboardViewModelTest`, `PremiumStatusProviderSeamTest`, `FormattingTest` all green (no ViewModel change).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/questlog/ui/QuestLogRoot.kt app/src/main/java/com/example/questlog/MainActivity.kt
git commit -m "Wire QuestLogRoot: Today/Realm nav, paywall + snackbar host"
```

---

## Task 14: Instrumented Compose tests

**Files:**
- Create: `app/src/androidTest/java/com/example/questlog/ui/TodayScreenTest.kt`
- Create: `app/src/androidTest/java/com/example/questlog/ui/RealmScreenTest.kt`

**Interfaces:**
- Consumes: `TodayScreen`, `RealmScreen`, `QuestLogTheme`, `fakeStats()`, `fakeTiles()`, `DashboardUiState`.

These run on a device/emulator via `./gradlew :app:connectedDebugAndroidTest`. **They are not run by `ci.yml`** — flag this as a follow-up (spec §12).

- [ ] **Step 1: `TodayScreenTest.kt`**

```kotlin
package com.example.questlog.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.ui.dashboard.DashboardUiState
import com.example.questlog.ui.today.TodayScreen
import com.example.questlog.ui.today.fakeStats
import com.example.questlog.ui.today.fakeTiles
import com.questlog.domain.model.DailyQuest
import org.junit.Rule
import org.junit.Test

class TodayScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun state(isPremium: Boolean = false) = DashboardUiState(
        isLoading = false,
        stats = fakeStats(),
        cityTiles = fakeTiles(),
        dailyQuests = listOf(
            DailyQuest("a", "Budget Guardian", "x", 250, 60, true, ""),
            DailyQuest("b", "Master Builder", "y", 300, 70, false, ""),
            DailyQuest("c", "Dawn Discipline", "z", 150, 30, false, ""),
        ),
        isPremium = isPremium,
    )

    @Test
    fun quest_count_and_titles_render() {
        compose.setContent { QuestLogTheme { TodayScreen(state(), {}, {}, {}) } }
        compose.onNodeWithText("1 / 3").assertIsDisplayed()
        compose.onNodeWithText("Budget Guardian").assertIsDisplayed()
    }

    @Test
    fun tapping_realm_strip_invokes_onOpenRealm() {
        var opened = false
        compose.setContent { QuestLogTheme { TodayScreen(state(), {}, {}, { opened = true }) } }
        compose.onNodeWithContentDescription("Your realm, 3 of 6 built. Open.").performClick()
        assert(opened)
    }

    @Test
    fun get_pro_pill_invokes_onOpenPaywall() {
        var paywall = false
        compose.setContent { QuestLogTheme { TodayScreen(state(isPremium = false), {}, { paywall = true }, {}) } }
        compose.onNodeWithText("GET PRO").performClick()
        assert(paywall)
    }
}
```

- [ ] **Step 2: `RealmScreenTest.kt`**

```kotlin
package com.example.questlog.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.ui.realm.RealmScreen
import com.example.questlog.ui.today.fakeTiles
import com.questlog.domain.model.CityTile
import org.junit.Rule
import org.junit.Test

class RealmScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun tapping_a_buyable_tile_emits_its_CityTile() {
        var clicked: CityTile? = null
        compose.setContent {
            QuestLogTheme { RealmScreen(fakeTiles(), 250L, onBack = {}, onTileClick = { clicked = it }) }
        }
        compose.onNodeWithText("Zen Garden").performClick()
        assert(clicked?.itemId == "garden")
    }

    @Test
    fun back_button_invokes_onBack() {
        var back = false
        compose.setContent {
            QuestLogTheme { RealmScreen(fakeTiles(), 250L, onBack = { back = true }, onTileClick = {}) }
        }
        compose.onNodeWithContentDescription("Back").performClick()
        assert(back)
    }
}
```

- [ ] **Step 3: Build the androidTest variant**

Run: `./gradlew :app:assembleDebugAndroidTest --no-daemon`
Expected: `BUILD SUCCESSFUL` (compiles the tests; running them needs a device). If an emulator is attached, `./gradlew :app:connectedDebugAndroidTest --no-daemon` and confirm all five pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/
git commit -m "Add instrumented Compose tests for Today and Realm (not in CI)"
```

---

## Task 15: Cleanup + docs

**Files:**
- Delete: `app/src/main/java/com/example/questlog/ui/components/StatsCard.kt`
- Delete: `app/src/main/java/com/example/questlog/ui/components/DailyQuestBanner.kt`
- Delete: `app/src/main/java/com/example/questlog/ui/components/CityGrid.kt`
- Modify: `app/src/main/java/com/example/questlog/theme/Color.kt` (remove unused constants)
- Modify: `README.md`, `CHANGELOG.md`, `CLAUDE.md`

- [ ] **Step 1: Delete the retired components**

```bash
git rm app/src/main/java/com/example/questlog/ui/components/StatsCard.kt \
       app/src/main/java/com/example/questlog/ui/components/DailyQuestBanner.kt \
       app/src/main/java/com/example/questlog/ui/components/CityGrid.kt
```

The `ui/components/` directory is now empty — leave it or `rmdir` it, either is fine.

- [ ] **Step 2: Prune `theme/Color.kt`**

Open `theme/Color.kt`. Build once (`./gradlew :app:assembleDebug --no-daemon`) and note any `warning: … is never used`. Delete every `val Quest*` constant that is no longer referenced anywhere under `app/src/main`. Search to be sure:

```bash
for name in QuestSlateDark QuestSlateCard QuestSlateBorder QuestGold QuestGoldLight QuestGoldDark QuestEmerald QuestEmeraldLight QuestArcane QuestArcaneLight QuestAmber QuestCrimson QuestCyan QuestTextPrimary QuestTextSecondary QuestTextMuted; do
  echo -n "$name: "; grep -rl "$name" app/src/main --include='*.kt' | grep -v 'theme/Color.kt' | wc -l
done
```

Any name with count `0` is dead — remove its declaration. If all are dead, `Color.kt` may end up empty; if so `git rm` it. (The new palette lives entirely in `QuestColors.kt`.)

- [ ] **Step 3: Full verification**

```bash
./gradlew :shared:desktopTest :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --no-daemon --rerun-tasks
```
Expected: all `BUILD SUCCESSFUL`, no test failures, release R8-clean. Then `grep -rn "🔥\|⚔️\|👑\|🏰\|🛒\|📚\|🌿\|⛲\|🧘\|🛡️\|⚡\|🪙\|💎\|⏳" app/src/main --include='*.kt'` — expect **no matches** (emoji fully removed from UI chrome).

- [ ] **Step 4: Update `README.md`**

In the **`| `ui` |`** row of the module-layout table (search "Compose Material 3") and the app-facing description, replace any mention of a single dashboard with:

```
Two screens — **Today** (streak ring, level, quest ledger, realm summary) and
**Realm** (the build grid) — plus the Pro paywall dialog. A `QuestColors` token
system drives a `QuestLogTheme` with matched light ("dawn") and dark ("nightfall")
palettes; the *Instrument Serif* display face is bundled (`app/src/main/res/font/`).
```

Bump the test-count note if one is present (the JVM suite grew by `FormattingTest`; instrumented tests are not counted).

- [ ] **Step 5: Update `CHANGELOG.md`**

Under `## [Unreleased]` add:

```markdown
### Changed

- **Full UI redesign** (*Nightfall × Monument*) — the single scrolling dashboard
  becomes a focused **Today** screen with the build grid on its own **Realm**
  screen. New semantic colour token system (`QuestColors` / `QuestLogTheme`) with
  matched light and dark palettes, a bundled *Instrument Serif* display face, and
  emoji removed from all persistent UI. No change to rewards, quests, streaks,
  billing, or persistence.
```

- [ ] **Step 6: Update `CLAUDE.md`**

Under **## Conventions** add one line:

```markdown
- `app` UI: colour comes from `QuestLogTheme.colors` (semantic tokens in `theme/QuestColors.kt`) or `MaterialTheme.colorScheme`, never a raw `Color(...)`. Two screens (`ui/today`, `ui/realm`) hosted by `ui/QuestLogRoot.kt`; no nav library. Display face *Instrument Serif* is bundled in `res/font/`.
```

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "Retire old dashboard components; docs for the UI redesign"
```

---

## Self-Review (completed during planning)

**1. Spec coverage**

| Spec section | Task(s) |
|---|---|
| §3.1 colour tokens (dark + light) | T2 |
| §3.2 typography + bundled Instrument Serif | T2 (scale), T3 (font) |
| §3.3 spacing / shape / flat elevation | T2 |
| §3.4 gradient ground | T5 (`QuestScaffold`) |
| §4 two-screen IA, no nav lib, `AnimatedContent`, `BackHandler` | T13 |
| §5.1 Today: header, hero, ring, level, ledger, realm strip, shield line | T7–T10 |
| §5.2 Realm: header, gold, grid, dashed Pro tiles | T11 |
| §5.3 Paywall restyle + rename | T12 |
| §6 file layout, deletions, `MainActivity` | T12, T13, T15 |
| §6.1 `QuestIcons` | T4 |
| §7 pure formatting helpers + tests | T1 |
| §8 motion + reduced-motion | T6 (helper), applied in T7/T8/T13 |
| §9 accessibility (contentDescription, ring label, strip role, contrast) | T7–T13 (per-component); contrast check in T2 |
| §10 testing strategy | T1 (JVM), T14 (instrumented), previews throughout |
| §11 risks | addressed structurally (accent discipline in tokens, ring replaces sparkline, single back depth) |
| §12 follow-ups | noted in T14 (CI wiring) and T15 docs |

No gaps.

**2. Placeholder scan:** every code step contains complete code. The two
"remove the unused import if flagged" notes are lint hygiene, not missing
content. T15 Step 2 is deliberately search-driven (which constants are dead
depends on the exact final import set) but gives the exact command and the
decision rule.

**3. Type consistency:** `QuestColors` field names, `QuestType` token names,
`QuestSpacing` names, `QuestIcons` members, `formatReclaimed`/`ReclaimedText`,
`ringFraction`, `fakeStats()`/`fakeTiles()` signatures, and the
`TodayScreen(state, onRefresh, onOpenPaywall, onOpenRealm)` /
`RealmScreen(tiles, gold, onBack, onTileClick)` / `QuestLogRoot(viewModel)`
signatures are used identically wherever they appear across tasks. `DailyQuest`
and `CityTile` constructor argument order matches the models in `shared`
(`DailyQuest(id, title, description, xpReward, goldReward, isCompleted, icon)`;
`CityTile(itemId, displayName, tier, isPremium, isOwned, goldCost)`).
