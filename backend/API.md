# Image Editor Backend — API Reference

## Setup

```bash
cp .env.example .env
# Add your HIGGSFIELD_API_KEY to .env

npm install
npm run start:dev
```

---

## Flow (Mobile App)

```
1. Upload image 1  →  POST /api/images/upload  →  mediaId_1
2. Upload image 2  →  POST /api/images/upload  →  mediaId_2
3. Generate        →  POST /api/images/generate  →  jobId
4. Poll until done →  GET /api/images/jobs/:jobId  →  outputUrl
```

---

## Endpoints

### POST `/api/images/upload`
Upload one image. Call twice — once per image.

**Request:** `multipart/form-data`
| Field | Type | Description |
|-------|------|-------------|
| `file` | File | JPEG, PNG, or WebP. Max 10MB. |

**Response:**
```json
{
  "success": true,
  "mediaId": "abc123-uuid",
  "message": "Image uploaded. Use this mediaId in /generate."
}
```

---

### POST `/api/images/generate`
Submit the editing job.

**Request:** `application/json`
```json
{
  "sourceMediaId": "abc123",       // mediaId of image 1 (to edit)
  "referenceMediaId": "def456",    // mediaId of image 2 (reference/style)
  "prompt": "Apply the style and colors from the reference image",
  "model": "flux_kontext",         // optional, default: flux_kontext
  "aspectRatio": "1:1"             // optional
}
```

**Available models:**
| Model | Best for |
|-------|----------|
| `flux_kontext` | Context-aware editing + style transfer (**default**) |
| `seedream_v5_lite` | Instruction-based editing with reasoning |
| `gpt_image_2` | Detailed edits, 4K resolution |
| `seedream_v4_5` | Precise transformations |
| `image_auto` | Let Higgsfield auto-pick the best model |

**Response:** `202 Accepted`
```json
{
  "success": true,
  "jobId": "job_xyz789",
  "message": "Generation started. Poll /jobs/:jobId for the result."
}
```

---

### GET `/api/images/jobs/:jobId`
Poll for result. Call every 2–3 seconds.

**Response:**
```json
{
  "success": true,
  "jobId": "job_xyz789",
  "status": "completed",
  "outputUrl": "https://cdn.higgsfield.ai/results/output.png"
}
```

**Statuses:** `pending` → `processing` → `completed` | `failed`

---

## Error Responses

```json
{
  "statusCode": 400,
  "message": "No file provided. Use field name \"file\".",
  "error": "Bad Request"
}
```
