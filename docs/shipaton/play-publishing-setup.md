# QuestLog — signing, first upload and CI publishing

Until now CI only *built* the app bundle: the keystore and upload steps were skipped (no
secrets, and the `if:` couldn't see step-level env — fixed in `deploy-internal.yml`). This is
everything needed for a push to `main` to land a signed build on the Play internal track.

Package: `com.udaysah.questlog` · Account: Goofy369 (personal)

## 1. Create the upload keystore (once, on your Mac)

Play App Signing holds the real app-signing key; you sign uploads with an **upload key**.
If it's ever lost, Play support can reset it — but keep a backup anyway.

```bash
keytool -genkeypair -v -keystore ~/questlog-upload.jks -alias questlog-upload -keyalg RSA -keysize 4096 -validity 10000
```

- Choose a strong store password; press Enter to reuse it for the key password.
- Name/organisation prompts: your real name is fine.
- **Back up** `~/questlog-upload.jks` and the password in a password manager. Never commit
  it (`*.jks` and `keystore.properties` are gitignored — check with `git check-ignore`).

## 2. Local signing (optional, for `bundleRelease` on your Mac)

Add to `keystore.properties` in the repo root (you already have `revenueCatKey` and
`unlockProxyUrl` there):

```
storeFile=/Users/udaysah/questlog-upload.jks
storePassword=…
keyAlias=questlog-upload
keyPassword=…
```

Then:

```bash
./gradlew :app:bundleRelease --no-daemon
```

The signed bundle is `app/build/outputs/bundle/release/app-release.aab`.

⚠️ `revenueCatKey` must be the **`goog_` key** for any release build — a `test_` key crashes
release builds.

## 3. First upload — by hand (Play's API can't do the first one)

1. Play Console → *Create app*: name **QuestLog**, app, free, declarations ticked.
2. *Test and release → Testing → Internal testing → Create new release*.
3. Accept **Play App Signing** (Google-generated app signing key — the default).
4. Upload `app-release.aab` from step 2, add release notes, *Save → Review → Start rollout*.
5. *Testers*: create an email list with your own Google account → copy the opt-in link.

## 4. Service account for CI

1. Google Cloud Console → project → enable **Google Play Android Developer API**.
2. *IAM → Service accounts → Create* (e.g. `questlog-ci`) → *Keys → Add key → JSON* → download.
3. Play Console → *Users and permissions → Invite new users* → the service account email →
   *App permissions → QuestLog*:
   - Release apps to testing tracks
   - (plus *View app information*; add *Release to production* later if CI should promote)

   You can use one service account for CI and RevenueCat — then also grant the financial
   permissions from `play-subscription-setup.md` step 2.

New service accounts can take up to 36 h to start working.

## 5. GitHub secrets

Paste each value at the prompt yourself — never into chat or a file in the repo.

```bash
gh secret set GOOGLE_PLAY_SERVICE_ACCOUNT_JSON < ~/Downloads/questlog-ci-XXXX.json
```

```bash
base64 -i ~/questlog-upload.jks | gh secret set ANDROID_KEYSTORE_BASE64
```

```bash
gh secret set ANDROID_KEYSTORE_PASSWORD
```

```bash
gh secret set ANDROID_KEY_ALIAS
```
(value: `questlog-upload`)

```bash
gh secret set ANDROID_KEY_PASSWORD
```

```bash
gh secret set REVENUECAT_API_KEY
```
(the `goog_…` key — replaces the current value)

Then delete the downloaded JSON from `~/Downloads`.

## 6. Check the pipeline

```bash
gh workflow run deploy-internal.yml
```

In the run, **Decode Release Keystore** and **Upload to Google Play Internal Track** must now
show as *success*, not *skipped*. The upload goes up as a **draft** release (required while
the app has never been published) — open Play Console → Internal testing and roll it out.
After the first production release, change `status: draft` to `status: completed` in
`.github/workflows/deploy-internal.yml` so internal builds roll out automatically.

## 7. Path to a public release (new personal accounts)

1. **Closed testing** track with **at least 12 testers opted in for 14 consecutive days**.
2. Then *Dashboard → Apply for production access* (a short questionnaire about the test).
3. Complete store listing + App content forms (answers are in `docs/shipaton/`).
4. Promote the tested build to **Production**.
