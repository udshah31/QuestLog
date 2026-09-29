# QuestLog — Ads and Advertising ID declarations

## Ads

Play Console → *Policy and programs → App content → Ads*

*"Does your app contain ads?"* → **No, my app does not contain ads**

Play counts as ads: ad-network SDKs, banners/interstitials/rewarded video, and house ads or
cross-promotion for *other* apps. QuestLog has none of these.

Not ads (no change to the answer):
- the **GET PRO** badge, the Pro paywall and the 7-day-streak trial offer — promoting the app's
  own in-app subscription;
- the share card — the user exports their own image through Android's share sheet.

## Advertising ID

Play Console → *App content → Advertising ID* (shown for apps targeting Android 13+)

*"Does your app use advertising ID?"* → **No**

Verified against the built APK (`aapt2 dump permissions`): the merged manifest requests only
`PACKAGE_USAGE_STATS`, `INTERNET`, `ACCESS_NETWORK_STATE` and `com.android.vending.BILLING` —
**no `com.google.android.gms.permission.AD_ID`**. The app never calls
`AdvertisingIdClient` or RevenueCat's `collectDeviceIdentifiers()`.

If a future SDK adds `AD_ID` to the merged manifest, either answer **Yes** (with purpose) or
strip it:

```xml
<uses-permission android:name="com.google.android.gms.permission.AD_ID" tools:node="remove" />
```

## Consistency

| Where | Says |
|---|---|
| Data safety (`data-safety.md`) | no advertising purpose on any data type ✔ |
| Privacy policy (`docs/privacy.md`) | "No advertising or advertising IDs." ✔ |
| Content rating (`content-rating.md`) | no ads ✔ |
