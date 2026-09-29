# Shipaton 2026 — submission checklist

Source: the official Devpost page (revenuecat-shipaton-2026.devpost.com), read 28 Sep 2026.
Re-check the page and the official rules before submitting — they win over this file.

## ⏰ Deadline

**Wednesday 30 September 2026, 11:45 PM Pacific** (= 1 Oct 2:45 AM EDT, 1 Oct ~12:30 PM Nepal).
Submit on Devpost well before — uploads and store reviews take time.

## Eligibility

- [ ] Ages 13+, country not excluded (see official rules).
- [ ] Uses the **RevenueCat SDK** for at least one purchase — ✅ QuestLog Pro via RevenueCat.
- [ ] Android app — ✅.
- [ ] **First public version released between 1 Aug and 30 Sep 2026** on the App Store,
      **Google Play** or **Samsung Galaxy Store**. ⚠️ see *Blocker* below.

## What Devpost requires (every item)

| # | Requirement | Have | Status |
|---|---|---|---|
| 1 | **Text description** of features and functionality | `submission.md` | ✅ (fill in links) |
| 2 | **Demo video**: shows the app running on the device it was built for; ≤ **2 min** of essential footage; **publicly visible** on YouTube or Vimeo; no third-party trademarks or copyrighted music without permission | 9 shots + script + captions, 1:30 planned | ⏳ edit + upload **Public** (not unlisted); use royalty-free music |
| 3 | **URL to the fully published app** on Google Play (or App Store / Galaxy Store) | — | ❌ **blocker** |
| 4 | **1024×1024 app icon** | `store-screenshots/app-icon-512.png` is 512 | ❌ needs a 1024 export |
| 5 | **≥ 1 screenshot at 1179 × 2556 px, no device frame** | store screenshots are 1080×1920 *with* phone frames | ❌ needs a raw capture at that size |
| 6 | **Free trial or promo code** so judges can unlock premium | 1-week free trial on the monthly plan | ✅ once the Play subscription + offer exist |

## ❌ Blocker — "fully published" on Google Play by 30 Sep

A new **personal** Play developer account must run a **closed test with 12+ testers for 14
consecutive days** before it can apply for production access. Goofy369 is still in identity
review, so a public Play release by 30 Sep isn't achievable on that account.

Options, fastest first:

1. **The account that already hosts `com.questlog.app`.** The CI deploys have been uploading
   to it successfully, so someone's Play account owns that package (the
   `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` service account has access). If that account is older
   or an organisation account, it may be able to **promote to production** directly — the
   quickest route. Revert the package switch (`com.udaysah.questlog` → `com.questlog.app`),
   push, promote, then finish listing/content forms there.
2. **Samsung Galaxy Store** — also accepted. Free seller account, but review takes several
   days and Play Billing doesn't work there (needs Samsung IAP) — likely too slow and a real
   code change.
3. **Ask RevenueCat** (Shipaton Discord / support) whether a Google Play **closed- or
   open-testing** link is accepted for accounts caught by the 12-tester rule, before the
   deadline — get the answer in writing.
4. **Next Gen Award** (students only): submit video + source code instead of a store listing.

Also: all store-listing, Data safety, content rating, target audience and ads answers are
ready in `docs/shipaton/` — they apply to whichever account you publish from.

## Fixes that don't depend on the store

- [ ] **1024×1024 icon** — export from the icon SVG (vector, scales cleanly).
- [ ] **1179×2556 screenshot, no frame** — set the emulator to that size
      (`adb shell wm size 1179x2556`), capture Today, reset with `adb shell wm size reset`.
- [ ] **Video Public**, ≤ 2:00, app running on Android, royalty-free (or no) music, no
      third-party logos beyond what's on-screen in the app.

## Judging fit

Pick the categories that match on the form:
- **RevenueCat Peace Prize** — social good: healthier phone habits, honest nudges, no
  blocking or shaming.
- **RevenueCat Design Award** — craft: the single-accent editorial design, skyline realm,
  share card.
- **Grand Prize** is judged on post-release traction and growth — mention anything you do
  after launch.

## Final submit

- [ ] Devpost form: description (`submission.md`), YouTube URL, store URL, 1024 icon,
      1179×2556 screenshot, trial noted.
- [ ] Links in `submission.md` and `youtube-description.md` filled in.
- [ ] Submitted before **30 Sep, 11:45 PM PT**.
