# QuestLog — Shipaton 2026 submission

Copy each section into the matching field on the submission form. Check the official
form for field names and length limits; trim from the bottom of each section if needed.

---

## Name

QuestLog

## Tagline (one line)

Time off your phone is the game — earn XP, gold and a city for every minute you don't scroll.

## Links

- Repo: https://github.com/udshah31/QuestLog
- Demo video: [add link]
- Play Store / testing link: [add link once the Play account is verified]

---

## What it is

QuestLog is an Android digital-detox app that flips the usual screen-time tracker around:
instead of scolding you for time spent, it rewards the time you *didn't* spend. Pick your
distracting apps, and every minute you stay off them earns XP and gold. Gold builds a small
city, three daily quests rotate each day, and a streak multiplies what you earn. The less you
scroll, the more your skyline grows — and you can share it.

## The problem

Screen-time dashboards show you a number and a guilty feeling. Hard blockers work until the
first time you genuinely need the app — then you turn them off for good. People need
something in between: a reason to stay away that feels good, and a way back in that doesn't
break the habit.

## What makes it different

**Mindful Unlocks — AI that decides, not an AI chatbot.** When you do need a blocked app,
you open it through QuestLog and type why. A small TypeSafe judgment model (behind our own
Cloudflare Worker) returns a typed verdict: *is this purposeful?* and *what kind of task?*
If it's a real task — replying to a message, looking something up, work — you get five
minutes that don't count against you. If it's "just bored", QuestLog says so honestly and
still lets you in. No lectures, no blocking, and QuestLog never stores what you typed.

**Rewards you can see.** Time saved turns into a city you build, a Progress screen with
all-time reclaimed time, best day and a 7-day chart, and a 9:16 skyline card to share.

## How RevenueCat powers it

- **QuestLog Pro** is a RevenueCat subscription (`questlog_pro` entitlement):
  double XP and gold, a weekly Streak Freeze, unlimited Mindful Unlocks (free: one a day)
  and two exclusive buildings.
- **Offer-driven paywall.** Price and trial text come live from the current RevenueCat
  Offering — no hard-coded prices — so pricing and trial experiments are dashboard-only
  changes.
- **The trial appears when it's earned.** The first time a free player reaches a
  **7-day streak**, the paywall slides up once with milestone copy ("Seven days kept.") and
  a 7-day free trial. Trial eligibility comes from Google Play, so ineligible users never
  see false "free trial" copy.
- **Safe entitlement handling.** The app treats "Pro status unknown" differently from
  "free", so a paying user on a slow cold start is never shown a paywall, and pending
  payments are handled gracefully.

## How it's built

- **Kotlin Multiplatform** core (`shared`) with all domain logic, and a **Jetpack
  Compose** Android UI (`app`).
- **Room (KMP)** with versioned, tested migrations; **Koin** for DI; MVI-style ViewModels.
- Screen time from Android's `UsageStatsManager` — the data stays on the device.
- **Idempotent reward accounting:** a daily high-water mark means rewards are never
  double-counted, however often the tracker ticks.
- **Cloudflare Worker proxy** for the AI call: the API key never ships in the app, with
  per-install rate limiting, a category allow-list and request-size limits.
- **228 automated tests** (JVM + Android unit tests) run in CI on every PR; every merge to
  `main` deploys to the Play internal track automatically.

## What we shipped during Shipaton

- The streak-milestone Pro trial (RevenueCat Offerings, real purchase flow).
- Mindful Unlocks (TypeSafe + Cloudflare Worker).
- The Progress screen with daily history.
- The shareable skyline card.
- Store listing assets: 5 screenshots and a feature graphic.

## What's next

- iOS: the Kotlin Multiplatform core is already shared; the UI is the remaining work.
- RevenueCat Experiments on trial length and price, now that the paywall reads Offerings.
- More buildings and seasonal quests.

## Built by

Uday Sah — solo developer.
