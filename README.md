# Fit App — AI Virtual Try-On

Upload a person photo + a garment photo and get back an image of the person wearing the garment. Face, skin tone, hair, and background are preserved — only the clothes change.

Powered by [fal.ai](https://fal.ai) Seedream v4.5.

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

## Project Structure

```
fit app/
├── backend/                        NestJS API server
│   ├── src/
│   │   ├── main.ts
│   │   ├── app.module.ts
│   │   ├── common/
│   │   │   └── file-logger.ts      Timestamped log per run
│   │   └── modules/image/
│   │       ├── image.controller.ts  POST /upload, POST /generate, GET /jobs/:id
│   │       ├── image.service.ts
│   │       ├── fal.service.ts       fal.ai API client (active)
│   │       └── dto/generate.dto.ts
│   ├── logs/                        app-{timestamp}.log per run
│   ├── outputs/                     Generated images saved here
│   ├── .env.example
│   └── package.json
│
├── android/                         Kotlin + Jetpack Compose app
│   └── app/src/main/java/com/fitapp/imageeditor/
│       ├── MainActivity.kt
│       ├── network/ApiService.kt    Retrofit interface
│       ├── data/ImageRepository.kt  Upload + generate + poll
│       ├── di/NetworkModule.kt      Hilt DI
│       └── ui/editor/
│           ├── EditorScreen.kt      Compose UI
│           └── EditorViewModel.kt   MVVM state
│
└── test-ui.html                     Standalone browser test UI
```

---

## Getting Started

### Prerequisites

- Node.js ≥ 14
- A [fal.ai API key](https://fal.ai/dashboard/keys)
- Android Studio (for the Android app)

### Backend Setup

```bash
cd backend
cp .env.example .env
# Add your key: FAL_API_KEY=your_key_here

npm install
npm run start:dev
```

Server runs on `http://localhost:3000`.

### Test in Browser

1. Start the backend
2. Open `test-ui.html` directly in Chrome
3. Select a person image (left) and a garment image (right)
4. Click **Generate**
5. Result appears below and is saved to `backend/outputs/`

### Android App

1. Open the `android/` folder in Android Studio
2. Set `BACKEND_URL` in `app/build.gradle.kts`:
   - Emulator: `http://10.0.2.2:3000` (default)
   - Physical device: `http://YOUR_LAN_IP:3000` (find via `ipconfig`)
3. Build → Build APK(s)
4. Install: `adb install android/app/build/outputs/apk/debug/app-debug.apk`

---

## API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/images/upload` | Upload one image (multipart `file`). Returns `{ mediaId }` |
| POST | `/api/images/generate` | Submit job. Body: `{ sourceMediaId, referenceMediaId, prompt }`. Returns `{ jobId }` |
| GET | `/api/images/jobs/:jobId` | Poll status. Returns `{ status, outputUrl }` when done |

---

## Configuration

| Setting | Default |
|---------|---------|
| Model | `fal-ai/bytedance/seedream/v4.5/edit` |
| Image size | `auto_2K` |
| Backend port | `3000` |
| Logs | `backend/logs/app-{timestamp}.log` |
| Outputs | `backend/outputs/output-{timestamp}-{requestId}.jpg` |

---

## Roadmap

- [ ] Test APK on physical device
- [ ] Deploy backend / set up ngrok for remote testing
- [ ] Add image download/share in Android UI
- [ ] Add error retry UI in Android app
