# QuestLog — Store listing graphics checklist

Play Console → *Grow users → Store presence → Main store listing → Graphics*.
Files live in `docs/shipaton/store-screenshots/` unless noted.

| Asset | Play requirement | Have | Status |
|---|---|---|---|
| **App icon** | 512×512 PNG, 32-bit (alpha OK), ≤ 1 MB, full square — Play applies the rounded mask | `app-icon-512.png` 512×512, full-bleed | ✅ |
| **Feature graphic** | 1024×500 JPEG or 24-bit PNG, **no alpha**, ≤ 15 MB | `feature-graphic.png` 1024×500, no alpha, 44 KB | ✅ |
| **Phone screenshots** | 2–8; PNG/JPEG ≤ 8 MB each; 320–3840 px per side; long side ≤ 2× short side; 1080×1920+ recommended for promotion | 5 × 1080×1920, no alpha, 112–140 KB | ✅ |
| 7" tablet screenshots | Optional (required only to be featured for tablets) | — | ⏭ skip |
| 10" tablet screenshots | Optional | — | ⏭ skip |
| Chromebook / Wear / TV | Only if you target them | — | ⏭ skip |
| Promo video | Optional; public or unlisted **YouTube** URL, ads off, not age-restricted | demo video once uploaded | ⏳ after editing |

## Upload order for phone screenshots

1. `01-today.png` — Time off your phone is the game.
2. `02-mindful-unlock.png` — Need the app? Say why.
3. `03-realm.png` — Build a city with the time you saved.
4. `04-progress.png` — See everything you've taken back.
5. `05-share-skyline.png` — Share your skyline.

The first 2–3 show in search results; keep Today and Mindful Unlock first.

## App icon

Skyline mark designed in Stitch (paper `#FAF7FF`, three red towers + one outlined, charcoal
ground line). The same geometry is the adaptive launcher icon
(`drawable/ic_launcher_foreground.xml` + `ic_launcher_background.xml`, also used as the themed
monochrome layer), scaled into the 66 dp safe zone. The template `mipmap-*/*.webp` fallbacks
were removed — `minSdk` is 26, so only `mipmap-anydpi-v26` is ever used.

## Final pre-upload checks

- [ ] Every image matches the real app — no invented features (the Stitch screenshots were
      already corrected; re-check after any UI change).
- [ ] No device frames or text that imply a ranking/award ("#1", "Best app").
- [ ] No Google Play badge or other store logos inside the images.
- [ ] Text on screenshots is legible on a phone (headlines only, no small print).
- [ ] Feature graphic keeps its text inside the centre safe area (it does).
