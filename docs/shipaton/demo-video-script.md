# QuestLog — Shipaton demo video

**Target length:** ~90 seconds (check the official Shipaton rules for the actual limit).
**Format:** vertical 9:16 screen recording of the app, voiceover, one-line captions.
**Story in one sentence:** *QuestLog pays you in XP, gold and a growing city for **not** opening your distracting apps, and a small AI gate that lets you open them on purpose.*

---

## Before you record

- [ ] Pro trial live: Play subscription with 7-day free trial, attached to `pro`, in the current RevenueCat Offering.
- [ ] Record on a **free** test account (the free-unlock limit and the milestone paywall only show for non-Pro).
- [ ] Seed a realistic state: a streak of a few days, **3–4 of 6 buildings built** (so the skyline shows red, outlined and grey towers), some reclaimed time today.
- [ ] Have **Instagram or YouTube** installed and on the Distractions list.
- [ ] Turn on Do Not Disturb, set the clock to a clean time, hide notifications.
- [ ] Record with `adb shell screenrecord` (emulator) or the phone's screen recorder; 1080×1920.

---

## Shot list

| # | Time | On screen | Voiceover | Caption |
|---|---|---|---|---|
| 1 | 0:00–0:06 | Hold on **Today**: "Reclaimed today 1h 30m", streak ring, quests ticked. | "This is QuestLog. Every minute you *don't* spend on Instagram or TikTok earns you something." | Time off your phone is the game. |
| 2 | 0:06–0:14 | Slow scroll: level bar, today's three quests, "Your realm" strip. | "XP, gold, daily quests, and a streak that multiplies it all." | XP · gold · quests · streak |
| 3 | 0:14–0:22 | Tap **View** on the realm, show the build grid, build one building. | "Gold builds your city. The less you scroll, the more it grows." | Build your city with the time you saved. |
| 4 | 0:22–0:40 | Back to Today, tap **Open an app mindfully** → pick YouTube → type "watch the recipe video mum sent me" → **Check** → "5 minutes, no charge." → **Open YouTube**. | "Sometimes you *do* need the app. Say why. A small AI model checks if it's a real task — and if it is, those five minutes don't count against you." | Mindful Unlocks — AI decides in under a second. |
| 5 | 0:40–0:48 | Back, try again with "just bored" → "That sounds like drifting — it'll count today." | "Just bored? It'll tell you — and still let you in. No lectures, just honesty." | Honest, never blocking. |
| 6 | 0:48–1:00 | Third unlock attempt → **paywall** with "Start free trial" and the live RevenueCat price. (Or: the 7-day-streak milestone paywall, "Seven days kept.") | "Free players get one mindful unlock a day. Pro makes it unlimited — with a free trial that shows up right when you've earned it: your first seven-day streak." | Pro via RevenueCat · 7-day free trial |
| 7 | 1:00–1:12 | Tap the **streak ring** → **Progress**: all-time reclaimed, the four tiles, level, 7-day chart. | "Progress shows the long game — everything you've taken back." | See the long game. |
| 8 | 1:12–1:24 | Tap **Share** → share sheet with the **skyline card** preview → show the card full screen. | "And when you're proud of it, share your skyline." | Share your skyline. |
| 9 | 1:24–1:30 | End card: app icon + "questlog" wordmark on paper, tagline. | "QuestLog. Less scrolling, more building." | Less scrolling, more building. |

---

## Voiceover, as one read (~200 words)

> This is QuestLog. Every minute you don't spend on Instagram or TikTok earns you something — XP, gold, daily quests, and a streak that multiplies it all.
>
> Gold builds your city. The less you scroll, the more it grows.
>
> Sometimes you do need the app. So say why. A small AI model checks if it's a real task — and if it is, those five minutes don't count against you. Just bored? It'll tell you — and still let you in. No lectures, just honesty.
>
> Free players get one mindful unlock a day. Pro makes it unlimited, with a free trial that shows up right when you've earned it: your first seven-day streak.
>
> Progress shows the long game — everything you've taken back. And when you're proud of it, share your skyline.
>
> QuestLog. Less scrolling, more building.

---

## What to call out for judges (description / captions)

- **RevenueCat:** paywall priced live from the current Offering; 7-day free trial surfaced at the first 7-day streak; trial eligibility handled by Play (no false "free trial" copy).
- **AI:** Mindful Unlocks — TypeSafe judgment (purposeful? + category) behind a Cloudflare Worker; the reason text is never stored.
- **Craft:** Kotlin Multiplatform core, ~230 tests, idempotent reward accounting, CI deploy to Play internal track on every merge.

## Open questions

- Real numbers vs seeded demo numbers — seeded is fine, but keep them plausible (a few hours, not hundreds).
- Music: calm, low, no lyrics; duck it under the voiceover.
