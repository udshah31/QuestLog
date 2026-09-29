# QuestLog — Play Data safety answers

Play Console → *Policy and programs → App content → Data safety*.
Answers reflect the code as of this commit — re-check if you add an SDK or a network call.

## What actually leaves the device

| Data | Where it goes | Why | Stored? |
|---|---|---|---|
| Screen-time usage (per-app minutes) | **Nowhere** — read via Usage Access, stays in the local Room DB | Rewards, streak, quests | On device only |
| Mindful Unlock **reason** (text you type) + the **app's name** (e.g. "YouTube") | Our Cloudflare Worker → TypeSafe API | One purposeful/not verdict | Worker never logs bodies. TypeSafe may keep inputs "as long as reasonably necessary" (no fixed period) |
| Random **install ID** (UUID made on first unlock) | Our Cloudflare Worker only (not forwarded) | Rate limit: 10 checks/min per install | Not logged; only held in the rate-limit counter |
| **Purchase history** + RevenueCat's anonymous app user ID | RevenueCat | Subscription status (Pro) | Yes, by RevenueCat (per RevenueCat's docs) |

No ads, no analytics SDK, no crash reporting, no account/login, no location, no contacts.

---

## Section 1 — Data collection and security

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS to the Worker and RevenueCat) |
| Which methods of account creation does your app support? | **My app does not allow users to create an account** |
| Do you provide a way for users to request that their data is deleted? | **Yes** — the privacy policy gives a contact email for deletion requests (RevenueCat supports deleting a customer); on-device data is deleted by clearing app data or uninstalling |

## Section 2 — Data types

Tick **only** these four.

### Financial info → Purchase history
| Question | Answer |
|---|---|
| Collected / shared | **Collected** (RevenueCat is a service provider acting for you → not "shared") |
| Processed ephemerally? | **No** |
| Required or optional? | **Required** |
| Purposes | **App functionality**, **Analytics** (RevenueCat's recommendation) |

### App activity → Other user-generated content  *(the unlock reason)*
| Question | Answer |
|---|---|
| Collected / shared | **Collected** (TypeSafe processes it for you → not "shared") |
| Processed ephemerally? | **No** — TypeSafe may retain it; we can't promise ephemeral |
| Required or optional? | **Optional** — Mindful Unlocks is an optional feature |
| Purposes | **App functionality** |

### App activity → Installed apps  *(the name of the app being unlocked)*
| Question | Answer |
|---|---|
| Collected / shared | **Collected** |
| Processed ephemerally? | **No** (sent to TypeSafe alongside the reason) |
| Required or optional? | **Optional** |
| Purposes | **App functionality** |

### Device or other IDs  *(the random install ID)*
| Question | Answer |
|---|---|
| Collected / shared | **Collected** |
| Processed ephemerally? | **Yes** — used only for the rate-limit counter, never logged or stored |
| Required or optional? | **Optional** (only sent when using Mindful Unlocks) |
| Purposes | **Fraud prevention, security, and compliance** |

### Leave unticked
Location · Personal info · Financial (other than purchase history) · Health and fitness ·
Messages · Photos and videos · Audio · Files and docs · Calendar · Contacts ·
App activity → App interactions / In-app search / Other actions · Web browsing ·
App info and performance · Device or other IDs → (only the install ID above).

**Why screen time isn't declared:** Play's Data safety covers data *transmitted off the
device*. Usage stats are read and kept locally and never sent anywhere.

---

## Also needed (separate App content forms)

- **Privacy policy URL** (`docs/privacy.md`) — required for any app declaring data collection. It should say:
  screen time stays on device; the unlock reason + app name go to TypeSafe via our proxy and
  aren't saved by QuestLog; RevenueCat handles purchases; no ads or analytics.
  A GitHub Pages page or a page in the repo works.
- **Permissions declaration → Usage Access (`PACKAGE_USAGE_STATS`)**: core purpose is
  showing the user their own time on chosen apps and rewarding time away; include a short
  video of the Usage Access prompt and the Today screen.
- **Target audience:** 18+ (or 13+) — not "designed for children".
- **Ads:** No.
