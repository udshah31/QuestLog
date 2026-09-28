# QuestLog — Play Store listing

Paste into **Play Console → Grow users → Store presence → Main store listing**.
Assets: `docs/shipaton/store-screenshots/` (01–05 phone screenshots, `feature-graphic.png`).

## App name (max 30)

```
QuestLog: Screen Time Quest
```
(27 chars)

## Short description (max 80)

```
Earn XP, gold and a city for time off your phone. Less scrolling, more building.
```
(80 chars)

## Full description (max 4000)

```
Time off your phone is the game.

QuestLog turns a digital detox into a quest. Every minute you don't spend on the apps that pull you in earns you XP and gold, and gold builds your own small city. The less you scroll, the more it grows.

HOW IT WORKS
• Pick your distractions — Instagram, TikTok, YouTube or anything else — and give each one a daily allowance if you like.
• QuestLog reads your screen time and turns the time you saved into XP and gold. Only time past your allowance counts against you.
• Three fresh daily quests each day, and a streak that multiplies the XP and gold you earn from time saved.
• Spend gold on buildings for your realm: Market, Library, Zen Garden and more, around your Town Hall.

MINDFUL UNLOCKS
Sometimes you really do need the app. Open it through QuestLog and type why. A small AI model checks whether it's a real task — replying to a message, looking something up, work — and if it is, you get five minutes that don't count against you. Just bored? It tells you honestly and still lets you in. No lectures, no blocking. What you type is never stored.

SEE THE LONG GAME
The Progress screen shows everything you've taken back: all-time time reclaimed, your best day, your level and the last seven days at a glance.

SHARE YOUR SKYLINE
Proud of it? Share a card of your city and the hours you've reclaimed.

QUESTLOG PRO
• Double XP and gold for every minute you save
• Streak Freeze: protects your streak through one missed day a week
• Unlimited Mindful Unlocks (free players get one check a day)
• Two extra buildings: Crystal Castle and Aurora Fountain
A free trial is offered when you reach your first seven-day streak. Subscriptions renew automatically until cancelled in Google Play; you can cancel any time before the trial ends.

PRIVACY
Your screen-time data stays on your device. QuestLog needs Usage Access permission to see how long you use the apps you've chosen; it doesn't read what you do inside them.

Less scrolling, more building.
```

## Category and tags

- Category: **Productivity** (alternative: Health & Fitness)
- Tags: digital wellbeing, screen time, habit tracker, focus

## Checks before publishing

- Usage Access (`PACKAGE_USAGE_STATS`) needs the **Permissions declaration** form and a **Data safety** answer (screen-time data, processed on device).
- The Mindful Unlock reason is sent to the proxy for a verdict and not stored — declare it under Data safety as "App activity / Other user-generated content, processed ephemerally, not shared".
- Pro price and trial length must match the Play subscription exactly.
