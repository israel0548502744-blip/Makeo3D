# Before publishing on Google Play

Status 2026-10-24. Items marked ✅ are done in the app; the rest need the owner.

## Legal documents
- ✅ Terms of use (Hebrew) in the app: Settings → משפטי ופרטיות, and accepted once on first launch. Source: `app/src/main/assets/legal/terms_he.txt`, copy in `docs/TERMS.md`.
- ✅ Privacy policy (Hebrew + English) in the app and in `docs/PRIVACY.md`.
- ✅ Contact e-mail: israel0546631352@gmail.com (both documents).
- ✅ Public pages generated into `docs/` (`python convert/legal_pages.py`): index, terms, privacy
  (Hebrew + English). ☐ Owner turns on GitHub Pages once: repository Settings → Pages → Build and
  deployment → "Deploy from a branch" → branch `ccr-755aee2e-0wbwnj`, folder `/docs` → Save.
  The privacy URL is then `https://israel0548502744-blip.github.io/makeo3d-public/privacy.html`.
- ☐ Lawyer review: `docs/LAWYER_BRIEF.md` is the message to send.

## App changes needed for the Play build
- ☐ **New name and package id.** `com.triposr.mobile` → the new name. A package id can never change after the first upload.
- ☐ **Remove the self-updater from the Play build.** Play forbids apps that download and install their own APKs, and `REQUEST_INSTALL_PACKAGES` is a restricted permission. Plan: two build flavors. `github` (today's test build, keeps the updater) and `play` (no updater, no install permission; it uses Google's In-App Updates library instead, which shows "update available" and installs through Play, which is allowed).
- ☐ **Repositories.** Code: `Makeo3D` (private). Public files: `makeo3d-public` (model releases, app builds, `config.json`, legal pages on GitHub Pages). New model releases are built here and copied over with makeo3d-public's "Copy a release" workflow. App builds go there directly once the secret `PUBLIC_REPO_TOKEN` (fine-grained token, makeo3d-public only, Contents: read and write) is set.
- ☐ **Retire the free GitHub builds at store launch:** set `min_build` in makeo3d-public/config.json to the first store build and `url` to the store page. Every older build then shows "צריך לעדכן" and stops. Also delete the old `apk-*` releases there.
- ☐ **Signing keys (most important, never lose them).** The repository is public and the test keystore `android/keystore/triposr.jks` (password in `build.gradle.kts`) is readable by anyone, so it must never sign a store release. Plan:
  1. Enroll in **Play App Signing**: Google keeps the real app-signing key in its vault, so it can't be lost.
  2. We create a separate **upload key** (one command, `keytool`), never committed. Copies: the owner's password manager + Google Drive + an offline USB stick; CI gets it as an encrypted GitHub secret.
  3. If the upload key is ever lost or leaked, Play support can reset it (only possible because of step 1).
- ☐ **AAB instead of APK.** Play needs an Android App Bundle. The models are already downloaded after install, so the bundle stays small (~100 MB with the QNN libraries).
- ☐ Model downloads come from GitHub releases. That's allowed, but for a paid product a CDN under our control is safer (rate limits, availability). Cloudflare R2 has free egress.
- ☐ Target API level: Play requires the latest-but-one Android API. Check `targetSdk` in `app/build.gradle.kts` before upload.

## Play Console forms (answers prepared)
- **Data safety:** "No data collected", "No data shared". Crash logs stay on the device and are only shared manually by the user. Data is encrypted in transit (HTTPS downloads). Users can delete data: yes (in-app clear + uninstall).
- **Content rating (IARC):** no violence, no user-to-user communication, no ads. Expect rating 3+/Everyone.
- **Target audience:** 13+ (avoid the "Designed for Families" requirements).
- **Ads:** none.
- **Foreground service declaration:** `specialUse`: "on-device 3D generation that continues while the user leaves the app". A short video of the feature may be requested.
- **Account deletion:** not applicable (no accounts).
- **Financial features / health / government:** none.

## Countries
- ☐ Don't offer the app in embargoed countries (Qualcomm export-control condition, see LEGAL.md): Cuba, Iran, North Korea, Syria, and the sanctioned regions of Ukraine.

## Store listing
- ☐ Name (≤30 chars), short description (≤80), full description (≤4000), Hebrew + English.
- ☐ Icon 512×512, feature graphic 1024×500, at least 2 phone screenshots. The CI screenshots (`ui-screenshots` branch) can be the start.
- ☐ Don't show copyrighted characters (Disney, Marvel…) in screenshots or ads.
- ☐ Developer account ($25 once) and, for selling in the EU, the DSA trader details (address, phone, e-mail are shown publicly).

## Money
- Owner's plan (2026-10-25): free for about six months, then paid. **Important:** Google Play does
  not allow a free app to become a paid app later. So the app stays a free download and the paid
  part is added later as an in-app purchase (one-time "Pro" unlock or subscription, Play Billing).
  Early users can be grandfathered (everyone who installed before the switch keeps Pro).
- No business registration is needed while the app is free; it is needed before the first sale.
- ☐ Payments profile in Play Console, bank account, tax info (Israel: a sole trader file (עוסק פטור/מורשה) is needed to receive the payments).
