# TryOnMe — AI Virtual Try-On

Share any Amazon garment link → see yourself wearing it → get redirected back to buy it with your affiliate tag applied automatically.

Powered by [fal.ai](https://fal.ai) Seedream v4.5.

---

## How It Works

```
User shares Amazon product link → TryOnMe Android app
         ↓
Backend scrapes product image (og:image)
         ↓
User adds their photo → fal.ai swaps the clothing
         ↓
Result saved to gallery → Amazon reopens with affiliate tag appended
```

---

## Project Structure

```
fit-app/
├── backend/
│   ├── src/
│   │   ├── common/
│   │   │   └── file-logger.ts           Writes to logs/app-{timestamp}.log
│   │   └── modules/image/
│   │       ├── image.controller.ts      All HTTP endpoints
│   │       ├── image.service.ts
│   │       ├── fal.service.ts           fal.ai client (active)
│   │       ├── higgsfield.service.ts    Inactive, kept for reference
│   │       └── dto/
│   │           ├── generate.dto.ts
│   │           ├── upload-url.dto.ts
│   │           ├── extract-product.dto.ts
│   │           └── log-redirect.dto.ts  NEW — affiliate redirect logging
│   ├── logs/                            app-{timestamp}.log per server run
│   ├── outputs/                         Generated images
│   ├── .env.example
│   └── package.json
│
├── android/
│   └── app/src/main/
│       ├── AndroidManifest.xml          App name: TryOnMe, share target
│       ├── res/
│       │   ├── mipmap-{density}/        ic_launcher + ic_launcher_fg PNGs
│       │   ├── mipmap-anydpi-v26/       Adaptive icon XML (API 26+)
│       │   └── values/themes.xml        Dark base theme (#080808 bg)
│       └── java/com/fitapp/imageeditor/
│           ├── MainActivity.kt
│           ├── ImageEditorApp.kt
│           ├── network/ApiService.kt    Retrofit — includes logRedirect
│           ├── data/
│           │   ├── ImageRepository.kt   Upload, generate, poll, logRedirect
│           │   └── PersonPhotoStore.kt  Persists person photo across restarts
│           ├── di/NetworkModule.kt
│           └── ui/
│               ├── theme/Theme.kt       Luxury dark palette + typography
│               └── editor/
│                   ├── EditorScreen.kt  All screens + luxury UI components
│                   └── EditorViewModel.kt
│
├── test-ui.html                         Browser test UI
└── start-backend.command                Double-click to start backend
```

---

## Getting Started

### Backend

```bash
cd backend
cp .env.example .env        # add FAL_API_KEY=your_key_here
npm install
npm run start:dev           # or double-click start-backend.command
```

Server runs at `http://localhost:3000/api`.

### Browser Test UI

1. Start the backend
2. Open `test-ui.html` in Chrome
3. Select person photo + garment via Gallery or URL tab → Generate

### Android (Emulator)

1. Start the backend
2. Open `android/` in Android Studio
3. Create a Pixel 9 device: **Google APIs, API 35, x86_64** (not Google Play)
4. Run — emulator reaches backend at `http://10.0.2.2:3000`

### Android Share Flow

1. Open Amazon → find a garment → tap **Share** → select **TryOnMe**
2. Product image is auto-extracted
3. Add your photo → Generate
4. Tap **REDIRECT TO MAIN APP** → image saved to gallery, Amazon reopens with affiliate tag

---

## API Reference

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/images/upload` | Upload image (multipart `file`). Returns `{ mediaId }` |
| POST | `/api/images/upload-url` | Fetch image from external URL + upload. Body: `{ url }`. Returns `{ mediaId }` |
| POST | `/api/images/extract-product` | Scrape product page, extract main image, upload. Body: `{ url }`. Returns `{ mediaId, imageUrl, productTitle }` |
| POST | `/api/images/generate` | Submit job. Body: `{ sourceMediaId, referenceMediaId, prompt }`. Returns `{ jobId }` |
| GET | `/api/images/jobs/:jobId` | Poll status. Returns `{ status, outputUrl }` |
| POST | `/api/images/log-redirect` | Log redirect event. Body: `{ originalUrl, finalUrl, affiliateTagAdded, affiliateTag? }` |

---

## Android Build Config (`app/build.gradle.kts`)

| Field | Default | Description |
|-------|---------|-------------|
| `BACKEND_URL` | `http://10.0.2.2:3000` | Backend address. Use LAN IP for physical device |
| `MOCK_GENERATION` | `true` | Skip fal.ai calls for UI testing. Flip to `false` for real demo |
| `ENABLE_GALLERY_PICKER` | `false` | Show Gallery tab on garment screen. `true` to re-enable |
| `AFFILIATE_TAG` | `viralcartf031-21` | Amazon Associates tag. Set `""` to disable |

---

## UI Design

The app uses a bespoke luxury dark theme:

- **Palette**: Obsidian (`#080808`) background · Gold (`#C8A96E`) accent · Cream (`#F4EEE4`) text
- **Typography**: Editorial style — tight display headings, wide-tracked ALL CAPS labels
- **Screens**: 3-step wizard (Garment → Try It On → Result)
- **Result screen**: Floating "REDIRECT TO MAIN APP" button (5% above bottom), before/after comparison

---

## Affiliate Tagging

When the user taps **REDIRECT TO MAIN APP**:

1. `tag=viralcartf031-21` is appended to the Amazon URL
2. If the URL already contains a **different** affiliate tag, it is left untouched
3. The original and final URLs are logged to both Android Logcat (`FitApp/Redirect`) and the backend log file

Change your tag or disable it in `build.gradle.kts` — no code changes required.

---

## Logs

Backend logs live in `backend/logs/app-{timestamp}.log` (new file per server run).

Redirect events appear as:
```
[INFO ] [Redirect] Affiliate tag injected (viralcartf031-21)
{
  "originalUrl": "https://amzn.in/d/...",
  "finalUrl":    "https://amzn.in/d/...?tag=viralcartf031-21"
}
```

---

## Roadmap

- [ ] Test APK on physical device (set `BACKEND_URL` to LAN IP)
- [ ] Deploy backend or set up ngrok for remote testing
- [ ] Add image share sheet on result screen
- [ ] Handle Amazon short-link redirect without Amazon app installed
- [ ] Flip `MOCK_GENERATION` to `false` before production
