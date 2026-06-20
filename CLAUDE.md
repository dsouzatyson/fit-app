# Fit App — Claude Instructions

## What This App Does
AI-powered virtual try-on: user selects a person photo + a garment photo → backend calls fal.ai Seedream v4.5 → returns person wearing the garment. Face, skin tone, hair, and background are preserved — only clothes change.

Primary flow: share a product link from Amazon → app extracts the garment image automatically → user adds their photo → AI swaps the clothing → result is saved to gallery and user is redirected back to Amazon.

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
│   │   ├── fal.service.ts            ← ACTIVE AI client (fal.ai)
│   │   ├── higgsfield.service.ts     ← INACTIVE, kept for reference only
│   │   ├── image.controller.ts       ← all HTTP endpoints
│   │   ├── image.service.ts
│   │   └── dto/
│   │       ├── generate.dto.ts
│   │       ├── upload-url.dto.ts     ← NEW
│   │       └── extract-product.dto.ts ← NEW
│   ├── .env                          ← FAL_API_KEY + PORT (gitignored)
│   ├── .env.example
│   └── package.json
├── android/
│   ├── app/src/main/java/com/fitapp/imageeditor/
│   │   ├── network/ApiService.kt
│   │   ├── data/
│   │   │   ├── ImageRepository.kt
│   │   │   └── PersonPhotoStore.kt   ← NEW: persists person photo across restarts
│   │   ├── di/NetworkModule.kt
│   │   └── ui/editor/
│   │       ├── EditorScreen.kt       ← 3-step wizard (Garment → Person → Result)
│   │       └── EditorViewModel.kt
│   ├── app/src/main/AndroidManifest.xml ← includes ACTION_SEND share target
│   └── local.properties              ← sdk.dir (gitignored)
├── test-ui.html                      ← Gallery + URL tab for garment
├── start-backend.command             ← double-click to install + start backend
└── CLAUDE.md                         ← this file
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
1. Open Amazon app → find a garment → tap Share → select **AI Image Editor**
2. App opens on the Garment step with the product image auto-extracted
3. Tap Proceed → add person photo → Generate
4. On result screen, tap **Redirect to main app** — saves image to gallery + returns to Amazon

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/images/upload` | Upload one image (multipart `file`). Returns `{ mediaId: CDN_URL }` |
| POST | `/api/images/upload-url` | Fetch image from external URL + upload to fal.ai. Body: `{ url }`. Returns `{ mediaId }` |
| POST | `/api/images/extract-product` | Scrape a product page URL, extract main image, upload to fal.ai. Body: `{ url }`. Returns `{ mediaId, imageUrl, productTitle }` |
| POST | `/api/images/generate` | Submit job. Body: `{ sourceMediaId, referenceMediaId, prompt }`. Returns `{ jobId }` |
| GET | `/api/images/jobs/:jobId` | Poll status. Returns `{ jobId, status, outputUrl }` |

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

## Android App — UI Flow (3 Steps)

### Step 1: Garment
- Opens automatically when app launches or receives a share intent
- **Share from Amazon**: `ACTION_SEND` intent delivers the URL → backend scrapes `og:image` → product card auto-populated
- **Manual**: Gallery tab (file picker) or URL tab (paste direct image URL)
- "Proceed →" button enabled once a garment is ready

### Step 2: Try It On
- Garment summary thumbnail shown at top
- Person photo picker — tap to select; shows "Choose another image" overlay after selection
- Person photo is **persisted across app kills** via `PersonPhotoStore` (copied to `filesDir/person_photo.jpg`, path saved to SharedPreferences)
- Edit instruction + model dropdown
- Generate button → triggers progress dialog

### Step 3: Result
- Before/After side-by-side comparison
- Full result image below
- **"Redirect to main app"** (green button): saves image to `Pictures/FitApp/` in device gallery → opens original Amazon share URL via `ACTION_VIEW`

### Generation Progress Dialog
- Non-dismissable `Dialog` (back press and outside tap disabled)
- Circular progress indicator with live % in centre
- Linear progress bar
- Messages cycle: Uploading → Swapping clothes → Matching fabric → Preserving features → Refining edges → Almost there…

---

## Mock / Debug Mode

Controlled by `BuildConfig.MOCK_GENERATION` in `android/app/build.gradle.kts`:

```kotlin
buildConfigField("Boolean", "MOCK_GENERATION", "true")   // test UI, no API cost
buildConfigField("Boolean", "MOCK_GENERATION", "false")  // real fal.ai generation
```

When `true`: skips all network calls, simulates the full progress animation (~8s), uses the person's own photo as the fake result. Flip to `false` before any real demo.

---

## Key Architecture Decisions

- **No NestJS CLI** — uses `ts-node-dev` + `nodemon` directly for Node 12+ compatibility
- **CORS `origin: true`** — reflects any origin including `file://` so `test-ui.html` works without a dev server
- **`BACKEND_URL` in build.gradle.kts** — set to `http://10.0.2.2:3000` for emulator (10.0.2.2 = host localhost from emulator)
- **`android:usesCleartextTraffic="true"`** in AndroidManifest — required for HTTP to localhost during development
- **`android:launchMode="singleTop"`** — prevents duplicate activity when sharing multiple links
- **`higgsfield.service.ts` kept but not wired** — `image.module.ts` only registers `FalService`
- **Product image extraction** — uses `og:image` meta tag (works across Amazon, Flipkart, Myntra etc.); strips Amazon size suffixes (e.g. `._SY879_`) for max resolution; falls back to `data-old-hires` attribute
- **Person photo persistence** — gallery `content://` URIs are temporary grants; `PersonPhotoStore` copies bytes to `filesDir` and saves the path to SharedPreferences so the photo survives process death
- **`referenceMediaId` short-circuit** — when garment is loaded via share (already uploaded to fal.ai), `generate()` skips the upload step entirely and uses the stored mediaId directly

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

---

## Pending

- [ ] Test APK on physical device (set `BACKEND_URL` to LAN IP, e.g. `http://192.168.x.x:3000`)
- [ ] Deploy backend or set up ngrok for remote testing
- [ ] Add image share sheet on result screen (in addition to gallery save)
- [ ] Handle Amazon short-link redirect on devices without Amazon app installed
- [ ] Flip `MOCK_GENERATION` to `false` before production
