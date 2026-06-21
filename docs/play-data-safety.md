# Play Console — Data Safety answers (T9 Dialer)

Reference sheet to fill the **Data safety** form (Play Console → App content → Data
safety). Every answer is "no" because the app has **no internet permission**, no
analytics/ads/crash SDKs, and no third-party SDKs (verified: manifest declares zero
`<uses-permission>`, only `<queries>` for app visibility).

Privacy policy URL (Data safety + main listing both ask for it):
`https://officialdad.github.io/t9-app-dialer/privacy.html`

## Section 1 — Data collection and security

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | App has no internet permission and makes no network calls — it cannot send data off-device. |
| Is all of the user data collected by your app encrypted in transit? | *N/A* | No data is collected or transmitted. (Form skips this once "collect" = No.) |
| Do you provide a way for users to request that their data be deleted? | *N/A* | No data is collected. Local settings are removed on uninstall. |

Because the first answer is **No**, Play skips the entire data-type questionnaire
below — listed here only so you can confirm each category truthfully stays empty.

## Section 2 — Data types (all NOT collected, NOT shared)

- Location — **No**
- Personal info (name, email, address, phone, etc.) — **No**
- Financial info — **No**
- Health & fitness — **No**
- Messages (SMS, email, in-app) — **No**
- Photos & videos — **No**
- Audio files — **No**
- Files & docs — **No**
- Calendar — **No**
- Contacts — **No**
- App activity (interactions, searches, installed apps) — **No** *(app list is read locally and never leaves the device)*
- Web browsing history — **No**
- App info & performance (crash logs, diagnostics) — **No**
- Device or other IDs — **No**

## Section 3 — Related listing fields

- **Ads:** App contains no ads → answer "No" on the Ads declaration.
- **Target audience / Content rating:** complete in Bucket 5 (no data-safety impact).
- **Account creation:** None.

## One-line summary for review notes (if a free-text box appears)

> T9 Dialer is a fully offline app launcher. It has no internet permission, no
> analytics, ads, or crash-reporting, and no third-party SDKs. It collects and
> transmits no user or device data. The installed-app list used for search is read
> locally and never leaves the device.
