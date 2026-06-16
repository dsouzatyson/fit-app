# Fit App — Project Handoff

## What This App Does
AI-powered virtual try-on: upload a person image + a garment image → backend calls fal.ai Seedream v4.5 API → returns person wearing the garment. Face, skin tone, hair, and background are preserved.

---

## Architecture

```
test-ui.html  (browser test UI)
     ↓ HTTP
NestJS Backend (Node.js)          ← localhost:3000
     ↓ REST API
fal.ai → fal-ai/bytedance/seedream/v4.5/edit
     ↓
Output image saved locally + returned to UI

Android App (Kotlin + Compose)    ← connects to same backend
```

---

## File Structure

```
fit app/
├── backend/                        NestJS API server
│   ├── src/
│   │   ├── main.ts                 Server entry point (CORS: origin: true)
│   │   ├── app.module.ts
│   │   ├── common/
│   │   │   └── file-logger.ts      Timestamped log per server run → logs/app-{ts}.log
│   │   └── modules/image/
│   │       ├── image.controller.ts  POST /upload, POST /generate, GET /jobs/:id
│   │       ├── image.service.ts     Thin orchestration layer
│   │       ├── fal.service.ts       ← ACTIVE: fal.ai API client (upload + queue + poll)
│   │       ├── higgsfield.service.ts ← INACTIVE: kept for reference, not wired up
│   │       ├── image.module.ts      Registers FalService (not HiggsfieldService)
│   │       └── dto/generate.dto.ts
│   ├── logs/                       app-{timestamp}.log per run
│   ├── outputs/                    Generated images saved here post-generation
│   ├── .env                        FAL_API_KEY=... PORT=3000
│   ├── .env.example
│   └── package.json                Uses ts-node + nodemon (no NestJS CLI — Node 12 compat)
│
├── android/                        Kotlin + Jetpack Compose app
│   ├── app/src/main/
│   │   ├── AndroidManifest.xml
│   │   └── java/com/fitapp/imageeditor/
│   │       ├── MainActivity.kt
│   │       ├── ImageEditorApp.kt   @HiltAndroidApp
│   │       ├── network/ApiService.kt       Retrofit interface
│   │       ├── data/ImageRepository.kt     Upload + generate + poll
│   │       ├── di/NetworkModule.kt         Hilt DI
│   │       └── ui/editor/
│   │           ├── EditorScreen.kt         Compose UI
│   │           └── EditorViewModel.kt      MVVM state
│   ├── build.gradle.kts            BACKEND_URL = http://10.0.2.2:3000 (emulator)
│   ├── settings.gradle.kts
│   └── gradle/libs.versions.toml
│
└── test-ui.html                    Standalone browser test UI (open directly in Chrome)
```

---

## Backend API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/images/upload` | Upload one image (multipart `file`). Returns `{ mediaId: CDN_URL }` |
| POST | `/api/images/generate` | Submit job. Body: `{ sourceMediaId, referenceMediaId, prompt, model? }`. Returns `{ jobId }` |
| GET | `/api/images/jobs/:jobId` | Poll status. Returns `{ status, outputUrl }` when done |

---

## fal.ai Integration (fal.service.ts)

**Model:** `fal-ai/bytedance/seedream/v4.5/edit`  
**Endpoint:** `https://queue.fal.run/fal-ai/bytedance/seedream/v4.5/edit`

**Flow:**
1. `uploadImage` → POST to `https://rest.alpha.fal.ai/storage/upload/initiate` → PUT bytes to presigned URL → returns CDN URL
2. `generateImage` → POST to queue with `{ prompt, image_urls: [personUrl, garmentUrl] }` → returns `request_id` + stores `status_url` / `response_url` from response
3. `getJobStatus` → GET `status_url` (from stored map, not constructed manually) → when COMPLETED, calls `fetchResult` → downloads image to `outputs/`

**Prompt used:**
```
Replace the clothing on the person in Figure 1 with the garment shown in Figure 2.
Keep the person's face, skin tone, hair, body shape, and background exactly the same as in Figure 1.
Only swap the clothes/outfit.
```

**Key fix:** `status_url` and `response_url` are captured from the submit response and stored in `jobUrls` Map. Do NOT construct these manually — fal.ai's 405 error was caused by a wrong manually-constructed URL.

---

## Running the Backend

```bash
cd "C:\Users\Tyson\Claude\Projects\fit app\backend"

# First time only
cp .env.example .env
# Add: FAL_API_KEY=your_key_from_fal.ai/dashboard/keys

npm install
npm run start:dev
```

Node.js version must be ≥ 14. The project uses `ts-node-dev` (not NestJS CLI) for Node 12+ compat.  
If Node 12, switch to `nodemon` + `ts-node@9.1.1` (already configured in package.json).

---

## Testing via Browser

1. Start backend (`npm run start:dev`)
2. Open `test-ui.html` directly in Chrome
3. Select person image (left) + garment image (right)
4. Click **Generate**
5. Result appears below; also saved to `backend/outputs/`

---

## Building the Android APK

1. Open `fit app/android/` in Android Studio
2. Update `BACKEND_URL` in `app/build.gradle.kts`:
   - Emulator: `http://10.0.2.2:3000` (default)
   - Physical phone: `http://YOUR_PC_LAN_IP:3000` (run `ipconfig` to find it)
3. `Build → Build Bundle(s) / APK(s) → Build APK(s)`
4. APK at: `android/app/build/outputs/apk/debug/app-debug.apk`
5. Install: `adb install app-debug.apk`

---

## Issues Resolved

| Issue | Fix |
|-------|-----|
| Higgsfield API returning 522 (server down) | Switched to fal.ai |
| Node.js 12 incompatibility (`??` operator) | Removed NestJS CLI, use ts-node directly |
| CORS blocked from `file://` origin | `origin: true` in NestJS CORS config |
| Garment image not uploading (second upload silent fail) | Added per-upload try/catch + console logging |
| fal.ai status endpoint returning 405 | Capture `status_url` from submit response; don't construct manually |
| Clothes not swapping (wrong model/endpoint) | Switched to `fal-ai/bytedance/seedream/v4.5/edit` with `image_urls` array |

---

## Current Defaults

| Setting | Value |
|---------|-------|
| Model | `seedream_v4_5` (Seedream 4.5) |
| fal.ai endpoint | `fal-ai/bytedance/seedream/v4.5/edit` |
| Image size | `auto_2K` |
| Backend port | `3000` |
| Log location | `backend/logs/app-{timestamp}.log` |
| Output location | `backend/outputs/output-{timestamp}-{requestId}.jpg` |

---

## Next Steps / Pending

- [ ] Build and test APK on physical device
- [ ] Set production `BACKEND_URL` (deploy backend or use ngrok for remote testing)
- [ ] Consider adding image result sharing/download in Android UI
- [ ] Add error retry UI in Android app
