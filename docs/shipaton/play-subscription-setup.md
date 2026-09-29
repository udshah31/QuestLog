# QuestLog Pro — Google Play subscription setup

Goal: the same offer the Test Store has now — **monthly, $9.99, 1-week free trial** — sold
through Google Play and wired to RevenueCat's `questlog_pro` entitlement and `default` offering.

Package name: `com.udaysah.questlog` · Developer account: Goofy369 (personal)

---

## 0. Before you start

- [ ] Play developer account verified (identity + phone).
- [ ] **Payments profile / merchant account** set up: Play Console → *Settings → Payments profile*.
      Subscriptions can't be created without it.
- [ ] App created in Play Console for `com.udaysah.questlog`.
- [ ] **One build uploaded** to a track (internal testing is fine). Play only unlocks
      *Monetize → Subscriptions* after it sees a build with the billing permission — the
      RevenueCat SDK adds `com.android.vending.BILLING` automatically.
      Build it with `./gradlew :app:bundleRelease` and upload the `.aab` by hand the first time.

## 1. Create the subscription (Play Console)

*Monetize with Play → Products → Subscriptions → Create subscription*

| Field | Value |
|---|---|
| Product ID | `questlog_pro` (permanent — can't be changed or reused) |
| Name | QuestLog Pro |

Then **Add base plan**:

| Field | Value |
|---|---|
| Base plan ID | `monthly` |
| Type | Auto-renewing |
| Billing period | 1 month |
| Grace period / account hold | defaults are fine |
| Price | **$9.99 USD** → *Update prices for other countries* to let Play convert |

**Activate** the base plan.

Then **Add offer** on that base plan:

| Field | Value |
|---|---|
| Offer ID | `trial-7d` |
| Eligibility | **New customer acquisition → Never had this subscription** |
| Phase | **Free trial, 1 week** (or 7 days) |

**Activate** the offer. Players who aren't eligible just don't get the free phase; the app then
shows "Unlock — $9.99 / month" instead of "Start free trial" — no code change needed.

## 2. Service account so RevenueCat can read purchases

1. Google Cloud Console → pick or create a project → enable
   **Google Play Android Developer API** and **Google Play Developer Reporting API**.
2. *IAM → Service accounts → Create*, name it e.g. `revenuecat-questlog`. No project roles needed.
   *Keys → Add key → JSON* — download it. **Treat it as a secret; never commit or paste it in chat.**
3. Play Console → *Users and permissions → Invite new users* → the service account's email →
   *Account permissions*:
   - View app information and download bulk reports
   - View financial data, orders and cancellation survey responses
   - Manage orders and subscriptions

   The CI deploy needs its own account (or add *Release to testing tracks* to this one) —
   that JSON goes in the `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` GitHub secret.

Google can take **up to 36 hours** before a new service account's credentials work.

## 3. RevenueCat

Project **QuestLog** → *Apps & providers → New app configuration → Google Play*

1. Package name `com.udaysah.questlog`.
2. Upload the service-account JSON from step 2. (Optional: set up Real-time developer
   notifications via Pub/Sub — RevenueCat's page walks you through it.)
3. *Product catalog → Products → Google Play → New* (or *Import*): identifier
   **`questlog_pro:monthly`** (subscription ID : base plan ID).
4. Attach it to the **`questlog_pro`** entitlement.
5. *Offerings → default → Edit →* `$rc_monthly` package → set the **Google Play** product to
   `questlog_pro:monthly`. Keep the Test Store product (`monthly_trial`) in the same package —
   one product per app is allowed.
6. Copy the **Google Play public SDK key** (`goog_…`) from *Apps & providers*.

## 4. Keys

| Where | Value |
|---|---|
| GitHub secret `REVENUECAT_API_KEY` (release builds via CI) | `goog_…` — **must not** be the `test_` key: a Test Store key crashes release builds |
| `keystore.properties` `revenueCatKey` (local) | `test_…` for emulator testing, `goog_…` to test real Play billing on a phone |

```bash
gh secret set REVENUECAT_API_KEY
```
(paste the `goog_` key at the prompt)

## 5. Test a real sandbox purchase

1. Play Console → *Settings → License testing* → add your Google account.
2. Add the same account as a tester on the **internal testing** track and install from the
   opt-in link (a sideloaded APK can't use Play Billing).
3. Open the paywall → it should read **"1 week free, then $9.99 / month."** and
   **"Start free trial"**. License testers are never charged; trials and renewals run on
   accelerated test times (a month renews in minutes).
4. After buying: the **PRO** badge appears, and the purchase shows under the customer in
   RevenueCat (*Sandbox data* toggle on).

## Checklist

- [ ] Payments profile
- [ ] App + first build uploaded
- [ ] `questlog_pro` subscription, `monthly` base plan $9.99, `trial-7d` offer — all active
- [ ] Service account created, invited with financial permissions
- [ ] RevenueCat Google Play app + JSON uploaded
- [ ] `questlog_pro:monthly` → `questlog_pro` entitlement → `default` / `$rc_monthly`
- [ ] `REVENUECAT_API_KEY` secret = `goog_…`
- [ ] Sandbox purchase on a license-tester phone unlocks Pro
