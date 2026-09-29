# QuestLog Privacy Policy

**Effective:** 28 September 2026
**App:** QuestLog (Android, package `com.udaysah.questlog`)
**Developer:** Uday Sah
**Contact:** [CONTACT EMAIL]

QuestLog rewards you for time you *don't* spend on distracting apps. This policy explains what
data the app uses, where it goes, and what stays on your phone.

## The short version

- Your screen-time data **never leaves your phone**.
- If you use **Mindful Unlocks**, the reason you type and the name of the app you're opening
  are sent to an AI service for a one-time check. QuestLog doesn't save them.
- Subscriptions are handled by **Google Play** and **RevenueCat**.
- No accounts, no ads, no analytics, no tracking, and we don't sell data.

## Data that stays on your device

- **App usage time.** With your permission (Android's *Usage Access*), QuestLog reads how long
  you use the apps you mark as distractions. It uses this to calculate your rewards, streak and
  quests. It's stored only in the app's local database and is never uploaded.
- **Your progress** — XP, gold, level, streak, buildings, quest history, your distraction list,
  and a count of mindful unlocks used each day. Stored only on your device.

Uninstalling QuestLog, or clearing its data in Android settings, deletes all of this.

## Data that leaves your device

### Mindful Unlocks (optional)

When you choose *Open an app mindfully* and tap **Check**, QuestLog sends:

- the **reason you typed** (up to 200 characters),
- the **name of the app** you want to open (for example, "YouTube"), and
- a **random install ID** created by QuestLog (not linked to your name, Google account or
  device identifiers).

These go over an encrypted connection to QuestLog's server (a Cloudflare Worker). The server:

- uses the install ID only to limit how many checks a phone can make per minute, and doesn't
  store or log it;
- forwards only the reason and app name to **TypeSafe** (typesafe.ai), an AI service that
  returns whether the reason sounds like a specific task and what kind;
- never logs or stores the reason or app name itself.

TypeSafe processes the text on our behalf and may retain it according to its own privacy
policy. QuestLog’s own records keep only which app, the result (a category and a score) and the time,
on your device. If you don't use Mindful Unlocks, none of this is sent.

### Purchases (QuestLog Pro)

Payments are processed by **Google Play** — QuestLog never sees your payment details. QuestLog
uses **RevenueCat** to check whether you have an active subscription. RevenueCat receives your
purchase history and an anonymous app user ID it creates; it doesn't receive your name or
email. See RevenueCat's privacy policy at https://www.revenuecat.com/privacy.

## What we don't do

- No user accounts or sign-in.
- No advertising or advertising IDs.
- No analytics, crash reporting or tracking SDKs.
- No location, contacts, photos, messages or files.
- We don't sell or share personal data with third parties for their own purposes.

## Children

QuestLog is not directed at children under 13 and does not knowingly collect their data.

## Your choices

- Revoke *Usage Access* at any time in Android settings (the app then can't track saved time).
- Don't use Mindful Unlocks if you don't want your reason sent for checking.
- Clear the app's data or uninstall to delete everything stored on your device.
- For questions or deletion requests about RevenueCat purchase records, contact us at the
  address above.

## Changes

If this policy changes, we'll update this page and its effective date.
