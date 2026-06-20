# TryOnMe — Claude Instructions

## What This App Does
AI-powered virtual try-on: user selects a person photo + a garment photo → backend calls fal.ai Seedream v4.5 → returns person wearing the garment. Face, skin tone, hair, and background are preserved — only clothes change.

Primary flow: share a product link from Amazon → app extracts the garment image automatically → user adds their photo → AI swaps the clothing → result is saved to gallery → user is redirected back to Amazon with an affiliate tag appended to the URL.

---

## Stack

| Layer | Tech |
|-------|------|
| Backend | NestJS (Node.js/TypeScript), port 3000 |
| AI provider | fal.ai — `fal-ai/bytedance/seedream/v4.5/edit` |
| Android | Kotlin + Jetpack Compose, MVVM, Hilt, Retrofit |
| Test UI | `test-ui.html` — open directly in Chrome |

---

## Project Structure

```
fit-app/
├── backend/
│   ├── src/modules/image/
│   │   ├── fal.service.ts              ← ACTIVE AI client (fal.ai)
│   │   ├── higgsfield.service.ts       ← INACTIVE, kept for reference only
│   │   ├── image.controller.ts         ← all HTTP endpoints
│   │   ├── image.service.ts
│   │   └── dto/
│   │       ├── generate.dto.ts
│   │       ├── upload-url.dto.ts
│   │       ├── extract-product.dto.ts
│   │       └── log-redirect.dto.ts     ← affiliate redirect logging
│   ├── src/common/
│   │   └── file-logger.ts              ← fileLog utility (stdout + file)
│   ├── .env                            ← FAL_API_KEY + PORT (gitignored)
│   ├── .env.example
│   └── package.json
├── android/
│   └── app/src/main/
│       ├── AndroidManifest.xml         ← app name "TryOnMe", share target
│       ├── res/
│       │   ├── mipmap-mdpi/            ← ic_launcher.png, ic_launcher_fg.png
│       │   ├── mipmap-hdpi/
│       │   ├── mipmap-xhdpi/
│       │   ├── mipmap-xxhdpi/
│       │   ├── mipmap-xxxhdpi/
│       │   ├── mipmap-anydpi-v26/      ← adaptive icon XML (API 26+)
│       │   └── values/themes.xml       ← dark base theme (#080808)
│       └── java/com/fitapp/imageeditor/
│           ├── network/ApiService.kt
│           ├── data/
│           │   ├── ImageRepository.kt
│           │   └── PersonPhotoStore.kt ← persists person photo across restarts
│           ├── di/NetworkModule.kt
│           └── ui/
│               ├── theme/Theme.kt      ← luxury dark palette + typography
│               └── editor/
│                   ├── EditorScreen.kt ← 3-step wizard + all UI components
│                   └── EditorViewModel.kt
├── test-ui.html                        ← Gallery + URL tab for garment
├── start-backend.command               ← double-click to install + start backend
└── CLAUDE.md                           ← this file
```

---

## Running Locally

### Backend
Double-click `start-backend.command` (installs deps + starts server).
Or manually:
```bash
cd backend
npm install
npm run start:dev
```
Server runs at `http://localhost:3000/api`.

If port 3000 is already in use: `lsof -ti :3000 | xargs kill -9`

### Browser Test UI
1. Start the backend
2. Open `test-ui.html` directly in Chrome
3. Select person photo (left) + garment via **Gallery tab** (file pick) or **URL tab** (paste image/product URL) → Generate

### Android Emulator
1. Start the backend
2. Open `android/` in Android Studio
3. Create a Pixel 9 virtual device: **Google APIs, API 35, x86_64** (not Google Play)
4. Run the app — emulator reaches backend via `http://10.0.2.2:3000`
5. Upload test images to emulator by dragging files onto the emulator window

### Android Share Flow (main use case)
1. Open Amazon app → find a garment → tap Share → select **TryOnMe**
2. App opens on the Garment step with the product image auto-extracted
3. Tap Proceed → add person photo → Generate
4. On result screen, tap **REDIRECT TO MAIN APP** — saves image to gallery + appends affiliate tag + returns to Amazon

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/images/upload` | Upload one image (multipart `file`). Returns `{ mediaId: CDN_URL }` |
| POST | `/api/images/upload-url` | Fetch image from external URL + upload to fal.ai. Body: `{ url }`. Returns `{ mediaId }` |
| POST | `/api/images/extract-product` | Scrape a product page URL, extract main image, upload to fal.ai. Body: `{ url }`. Returns `{ mediaId, imageUrl, productTitle }` |
| POST | `/api/images/generate` | Submit job. Body: `{ sourceMediaId, referenceMediaId, prompt }`. Returns `{ jobId }` |
| GET | `/api/images/jobs/:jobId` | Poll status. Returns `{ jobId, status, outputUrl }` |
| POST | `/api/images/log-redirect` | Log redirect event. Body: `{ originalUrl, finalUrl, affiliateTagAdded, affiliateTag? }` |

---

## fal.ai Integration

- **Model:** `fal-ai/bytedance/seedream/v4.5/edit`
- **Queue endpoint:** `https://queue.fal.run/fal-ai/bytedance/seedream/v4.5/edit`
- **Upload:** POST to `https://rest.alpha.fal.ai/storage/upload/initiate` → PUT bytes to presigned URL
- **Critical:** `status_url` and `response_url` are captured from the submit response and stored in `jobUrls` Map in `FalService`. Do NOT construct these URLs manually — caused 405 errors previously.

**Hardcoded generation prompt (in fal.service.ts):**
```
Replace the clothing on the person in Figure 1 with the garment shown in Figure 2.
Keep the person's face, skin tone, hair, body shape, and background exactly the same as in Figure 1.
Only swap the clothes/outfit.
```

---

## Environment

`backend/.env` (gitignored):
```
FAL_API_KEY=your_key_here
PORT=3000
```
Get a key at https://fal.ai/dashboard/keys.

`android/local.properties` (gitignored):
```
sdk.dir=/Users/Tyson/Library/Android/Sdk
```

---

## Android Build Config (`app/build.gradle.kts`)

All feature flags live here — change the value and rebuild, no Kotlin edits needed.

| Field | Default | Purpose |
|-------|---------|---------|
| `BACKEND_URL` | `http://10.0.2.2:3000` | Backend host. Use LAN IP for physical device |
| `MOCK_GENERATION` | `true` | Skip fal.ai calls; person photo used as fake result. Flip to `false` before real demo |
| `ENABLE_GALLERY_PICKER` | `false` | Show/hide Gallery tab on garment screen. Gallery code is preserved when `false` |
| `AFFILIATE_TAG` | `viralcartf031-21` | Amazon Associates tag appended to redirect URL. Set `""` to disable |

---

## Android App — UI Design

**Theme:** Luxury dark editorial style defined in `ui/theme/Theme.kt`:
- **Palette**: Obsidian `#080808` bg · Gold `#C8A96E` accent · Cream `#F4EEE4` text
- **Typography**: Tight display headings, wide-tracked ALL CAPS labels (2–2.5sp letter-spacing)
- **Shapes**: Minimal (2–8dp) — sharp, architectural feel
- Dynamic Material You disabled — fixed brand palette always applied

**Screen flow (3 steps):**

### Step 1: Garment
- `ENABLE_GALLERY_PICKER=false` → only "PASTE URL" / "PRODUCT URL" field shown (Gallery code preserved, re-enable via flag)
- Share from Amazon: `ACTION_SEND` intent → backend scrapes `og:image` → product card auto-populated
- "P R O C E E D" full-width gold CTA bar at bottom

### Step 2: Try It On
- Underline-style text fields (no bordered boxes)
- Person photo persisted across app kills via `PersonPhotoStore`
- "GENERATE LOOK" gold CTA
- Non-dismissable progress dialog: large gold `%` counter + thin animated gold progress line

### Step 3: Result
- Before/After side-by-side (gold border on After panel)
- Full result image below
- **Floating "REDIRECT TO MAIN APP" button**: positioned 5% above the bottom of the screen, non-draggable, elevated with an emerald drop shadow
- Scroll content has bottom padding to stay clear of the floating button

---

## Affiliate Tagging

Logic lives in `appendAffiliateTag()` in `EditorScreen.kt`:

| Condition | Behaviour |
|-----------|-----------|
| No `tag` param in URL | Appends `?tag=viralcartf031-21` |
| `tag` already equals our tag | No-op (idempotent) |
| Different `tag` present | URL returned untouched (third-party affiliate respected) |
| `AFFILIATE_TAG` is `""` | Disabled entirely |

After tagging, the event is logged to:
- **Android Logcat** — tag `FitApp/Redirect` (visible in Android Studio)
- **Backend log file** — via `POST /api/images/log-redirect` → `fileLog.info('Redirect', …)` → `backend/logs/app-{timestamp}.log`

---

## App Icon

TryOnMe logo placed in all mipmap density folders (`ic_launcher.png` + `ic_launcher_fg.png`).
Adaptive icon (`mipmap-anydpi-v26/ic_launcher.xml`) uses `ic_launcher_fg` as foreground to break the self-reference loop that caused the Android robot icon to appear on API 26+.

---

## Key Architecture Decisions

- **No NestJS CLI** — uses `ts-node-dev` + `nodemon` directly for Node 12+ compatibility
- **CORS `origin: true`** — reflects any origin including `file://` so `test-ui.html` works without a dev server
- **`BACKEND_URL` in build.gradle.kts** — set to `http://10.0.2.2:3000` for emulator (10.0.2.2 = host localhost from emulator)
- **`android:usesCleartextTraffic="true"`** in AndroidManifest — required for HTTP to localhost during development
- **`android:launchMode="singleTop"`** — prevents duplicate activity when sharing multiple links
- **`higgsfield.service.ts` kept but not wired** — `image.module.ts` only registers `FalService`
- **Product image extraction** — uses `og:image` meta tag (works across Amazon, Flipkart, Myntra etc.); strips Amazon size suffixes for max resolution; falls back to `data-old-hires` attribute
- **Person photo persistence** — gallery `content://` URIs are temporary grants; `PersonPhotoStore` copies bytes to `filesDir` and saves the path to SharedPreferences so the photo survives process death
- **`referenceMediaId` short-circuit** — when garment is loaded via share (already uploaded to fal.ai), `generate()` skips the upload step entirely and uses the stored mediaId directly
- **Gallery picker config** — `ENABLE_GALLERY_PICKER=false` hides the UI tab but all Gallery Kotlin code is preserved and re-activates when the flag is flipped
- **Affiliate tag safety** — third-party `tag` params are never overwritten; our tag is only injected when no `tag` param is present
- **fileLog for redirect events** — NestJS `Logger` only goes to stdout; `fileLog` writes to both stdout and the timestamped log file
- **Floating CTA** — `BoxWithConstraints` used in ResultStep to position the button at `maxHeight * 0.05f` from the bottom; `Modifier.clickable` only (no drag gesture), so position is fixed

---

## Known Issues Resolved

| Issue | Fix |
|-------|-----|
| Higgsfield API returning 522 | Switched to fal.ai |
| Node.js 12 `??` operator incompatibility | Removed NestJS CLI, use ts-node directly |
| CORS blocked from `file://` origin | `origin: true` in NestJS CORS config |
| fal.ai status endpoint returning 405 | Capture `status_url` from submit response; never construct manually |
| Clothes not swapping | Switched to `fal-ai/bytedance/seedream/v4.5/edit` with `image_urls` array |
| Amazon CDN images blocked in browser | `upload-url` endpoint fetches server-side; bypasses CORS |
| `AxiosHeaderValue` TypeScript error on `content-type` | Cast to `String()` before calling `.split()` |
| Gallery URI lost on app kill | `PersonPhotoStore` copies to internal storage; stable `file://` URI saved to SharedPreferences |
| Adaptive icon showing Android robot | Foreground renamed to `ic_launcher_fg` to avoid self-reference loop on API 26+ |
| Redirect logs missing from log file | Replaced NestJS `Logger` with `fileLog` in the log-redirect controller endpoint |
| ValidationPipe rejecting log-redirect body | Added `class-validator` decorators (`@IsString`, `@IsBoolean`, `@IsOptional`) to `LogRedirectDto` |
| `Theme.Material.NoTitleBar` AAPT error | Reverted to valid parent `android:Theme.Material.Light.NoActionBar` in themes.xml |

---

## Pending

- [ ] Test APK on physical device (set `BACKEND_URL` to LAN IP, e.g. `http://192.168.x.x:3000`)
- [ ] Deploy backend or set up ngrok for remote testing
- [ ] Add image share sheet on result screen (in addition to gallery save)
- [ ] Handle Amazon short-link redirect on devices without Amazon app installed
- [ ] Flip `MOCK_GENERATION` to `false` before production
