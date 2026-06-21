# Play Console — Graphic assets checklist (T9 App Dialer)

What you produce on-device and upload to Play Console → **Store listing → Graphics**.
All images must have **no transparency where noted**, no Play badges, no device
frames added by you (Play frames screenshots itself), and no misleading content.

## Required

| Asset | Spec | Count | Notes |
|---|---|---|---|
| **App icon** | 512 × 512 px, 32-bit PNG, **no alpha** (opaque), < 1 MB | 1 | This is the listing icon, separate from the in-app launcher icon. Use the same artwork as `res/mipmap` / `README icon.png` at 512². Don't add rounded corners or shadow — Play masks it. |
| **Feature graphic** | 1024 × 500 px, PNG or JPG, no alpha | 1 | Shown at top of listing + used for promo. Keep text minimal (it gets cropped on some surfaces); app name + a one-line hook on the brand background. |
| **Phone screenshots** | 16:9 or 9:16, each side 320–3840 px, PNG/JPG | **2–8** (min 2) | Pull frames from the existing demo (README video) or capture fresh: (1) keypad with a live search match, (2) icon-pack or theme view, (3) movable/resizable container, (4) About dialog. 9:16 portrait recommended (the app is portrait-first). |

## Optional (improves listing quality, not required to submit)

| Asset | Spec | Notes |
|---|---|---|
| 7" tablet screenshots | min 2, same ratio rules | Only if you want the "designed for tablets" surfaces. |
| 10" tablet screenshots | min 2 | Same. |
| Promo video | YouTube URL | The existing demo video could be uploaded to YouTube and linked. Optional. |

## Capture tips (on-device, Termux/phone)
- Use a clean home state: a few recognizable apps so the T9 match reads clearly
  in a screenshot (e.g. type to match "GMAIL" / "MAPS").
- Shoot in both light and black themes if you want variety — but keep the set
  consistent in style.
- Don't show other apps' content in a way that looks like your app owns it.
- No personal info (real contact names, notification content) visible.

## Gotchas that get listings rejected
- Icon with transparency → rejected. Flatten to opaque 512².
- Screenshots with added marketing text covering the whole frame → discouraged;
  keep them showing the actual UI.
- Feature graphic with a Play "Get it on Google Play" badge → not allowed.
- Mismatched aspect ratios within the screenshot set → keep them uniform.
