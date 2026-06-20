import { Injectable, InternalServerErrorException, ServiceUnavailableException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import axios from 'axios';
import * as fs from 'fs';
import * as path from 'path';
import { fileLog } from '../../common/file-logger';

const OUTPUT_DIR = path.join(process.cwd(), 'outputs');
if (!fs.existsSync(OUTPUT_DIR)) fs.mkdirSync(OUTPUT_DIR, { recursive: true });

const CTX = 'FalService';
const QUEUE_BASE = 'https://queue.fal.run/fal-ai/bytedance/seedream/v4.5/edit';
const STORAGE_BASE = 'https://rest.alpha.fal.ai/storage/upload';

export interface FalGenerationResult {
  requestId: string;
  status: 'pending' | 'processing' | 'completed' | 'failed';
  outputUrl?: string;
}

// In-memory map of requestId → fal.ai convenience URLs
interface JobUrls { statusUrl: string; resultUrl: string; }

@Injectable()
export class FalService {
  private readonly apiKey: string;
  private readonly jobUrls = new Map<string, JobUrls>();

  constructor(private readonly config: ConfigService) {
    this.apiKey = config.get<string>('FAL_API_KEY') ?? '';
  }

  private get authHeader() {
    return { Authorization: `Key ${this.apiKey}` };
  }

  // ── Step 1: Upload image to fal storage → returns CDN URL ─────────────────

  async uploadImage(buffer: Buffer, filename: string, mimeType: string): Promise<string> {
    fileLog.info(CTX, `uploadImage START — ${filename} ${buffer.length} bytes`);

    // Initiate upload → get presigned URL
    let uploadUrl: string;
    let fileUrl: string;
    try {
      const res = await axios.post(
        `${STORAGE_BASE}/initiate`,
        { content_type: mimeType, file_name: filename },
        { headers: { ...this.authHeader, 'Content-Type': 'application/json' } }
      );
      fileLog.info(CTX, 'uploadImage initiate ←', res.data);
      uploadUrl = res.data.upload_url;
      fileUrl   = res.data.file_url;
    } catch (err) {
      fileLog.error(CTX, 'uploadImage initiate FAILED', {
        status: err?.response?.status,
        data: err?.response?.data,
        message: err.message,
      });
      throw new InternalServerErrorException('Failed to initiate fal.ai upload');
    }

    // PUT bytes to presigned URL
    try {
      await axios.put(uploadUrl, buffer, {
        headers: { 'Content-Type': mimeType },
        maxBodyLength: Infinity,
        timeout: 60_000,
      });
      fileLog.info(CTX, `uploadImage PUT done — CDN URL: ${fileUrl}`);
    } catch (err) {
      fileLog.error(CTX, 'uploadImage PUT FAILED', { message: err.message });
      throw new InternalServerErrorException('Failed to upload image bytes to fal.ai');
    }

    return fileUrl;
  }

  // ── Step 1b: Upload image from external URL to fal storage ────────────────

  async uploadImageFromUrl(imageUrl: string): Promise<string> {
    fileLog.info(CTX, `uploadImageFromUrl START — ${imageUrl}`);

    let buffer: Buffer;
    let mimeType: string;
    let filename: string;

    try {
      const res = await axios.get(imageUrl, {
        responseType: 'arraybuffer',
        timeout: 30_000,
        maxContentLength: 20 * 1024 * 1024, // 20 MB cap
      });
      buffer = Buffer.from(res.data);
      mimeType = (String(res.headers['content-type'] ?? 'image/jpeg')).split(';')[0].trim();
      const urlPath = new URL(imageUrl).pathname;
      filename = urlPath.split('/').pop() || 'garment.jpg';
    } catch (err) {
      fileLog.error(CTX, 'uploadImageFromUrl fetch FAILED', { message: err.message });
      throw new InternalServerErrorException(`Failed to fetch image from URL: ${err.message}`);
    }

    return this.uploadImage(buffer, filename, mimeType);
  }

  // ── Step 1c: Extract product image from a product page URL ───────────────

  async extractProductImage(pageUrl: string): Promise<{
    imageUrl: string;
    mediaId: string;
    productTitle: string;
  }> {
    fileLog.info(CTX, `extractProductImage START — ${pageUrl}`);

    // Resolve short links (e.g. amzn.in/d/xxx) to full URL
    let resolvedUrl = pageUrl;
    try {
      const head = await axios.get(pageUrl, {
        maxRedirects: 10,
        timeout: 15_000,
        headers: { 'User-Agent': 'Mozilla/5.0 (compatible; FitApp/1.0)' },
        validateStatus: () => true,
      });
      resolvedUrl = head.request?.res?.responseUrl ?? head.config?.url ?? pageUrl;
      fileLog.info(CTX, `extractProductImage resolved → ${resolvedUrl}`);
    } catch {
      // proceed with original URL
    }

    // Fetch page HTML
    let html: string;
    try {
      const res = await axios.get(resolvedUrl, {
        timeout: 20_000,
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36',
          'Accept-Language': 'en-US,en;q=0.9',
        },
        responseType: 'text',
      });
      html = res.data as string;
    } catch (err) {
      fileLog.error(CTX, 'extractProductImage page fetch FAILED', { message: err.message });
      throw new InternalServerErrorException(`Failed to fetch product page: ${err.message}`);
    }

    // Extract og:image (most reliable across Amazon, Flipkart, Myntra, etc.)
    const ogImageMatch = html.match(/<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']/i)
      ?? html.match(/<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']/i);

    // Fallback: Amazon landingImage data-old-hires or data-a-dynamic-image
    const landingMatch = html.match(/data-old-hires=["']([^"']+)["']/i)
      ?? html.match(/"large"\s*:\s*"(https:\/\/[^"]+m\.media-amazon\.com[^"]+)"/);

    const rawImageUrl = ogImageMatch?.[1] ?? landingMatch?.[1];
    if (!rawImageUrl) {
      throw new InternalServerErrorException('Could not extract a product image from this URL. Try sharing the direct image URL instead.');
    }

    // Strip Amazon image size suffixes to get highest resolution
    // e.g. ._SY879_ or ._AC_SX522_ → remove
    const imageUrl = rawImageUrl.replace(/\._[A-Z0-9_,]+_(\.\w+)$/, '$1');
    fileLog.info(CTX, `extractProductImage imageUrl = ${imageUrl}`);

    // Extract product title from og:title
    const titleMatch = html.match(/<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)["']/i)
      ?? html.match(/<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:title["']/i)
      ?? html.match(/<title[^>]*>([^<]+)<\/title>/i);
    const productTitle = (titleMatch?.[1] ?? 'Product').replace(/ - Amazon\..*$/, '').trim();

    // Upload image to fal.ai
    const mediaId = await this.uploadImageFromUrl(imageUrl);
    return { imageUrl, mediaId, productTitle };
  }

  // ── Step 2: Submit generation job ─────────────────────────────────────────

  async generateImage(params: {
    sourceImageUrl: string;
    referenceImageUrl: string;
    prompt: string;
    aspectRatio?: string;
  }): Promise<string> {
    const { sourceImageUrl, referenceImageUrl, prompt, aspectRatio } = params;

    // Clothing-only transfer: apply garment from reference image, preserve everything else.
    // Figure 1 = person (source), Figure 2 = garment (reference)
    const fullPrompt =
      `Replace the clothing on the person in Figure 1 with the garment shown in Figure 2. ` +
      `Keep the person's face, skin tone, hair, body shape, and background exactly the same as in Figure 1. ` +
      `Only swap the clothes/outfit.`;

    const body: any = {
      prompt: fullPrompt,
      image_urls: [sourceImageUrl, referenceImageUrl],
      num_images: 1,
      image_size: 'auto_2K',
    };

    fileLog.info(CTX, 'generateImage → POST queue', {
      endpoint: QUEUE_BASE,
      sourceImageUrl,
      referenceImageUrl,
      prompt: fullPrompt,
    });

    try {
      const res = await axios.post(QUEUE_BASE, body, {
        headers: { ...this.authHeader, 'Content-Type': 'application/json' },
        timeout: 30_000,
      });
      fileLog.info(CTX, `generateImage ← ${res.status}`, res.data);
      const requestId  = res.data?.request_id;
      const statusUrl  = res.data?.status_url  ?? `${QUEUE_BASE}/requests/${requestId}/status`;
      const resultUrl  = res.data?.response_url ?? `${QUEUE_BASE}/requests/${requestId}`;
      this.jobUrls.set(requestId, { statusUrl, resultUrl });
      fileLog.info(CTX, `generateImage stored URLs`, { statusUrl, resultUrl });
      return requestId;
    } catch (err) {
      fileLog.error(CTX, 'generateImage FAILED', {
        status: err?.response?.status,
        data: err?.response?.data,
        message: err.message,
      });
      if ([503, 502, 429].includes(err?.response?.status)) {
        throw new ServiceUnavailableException('fal.ai is temporarily unavailable. Try again shortly.');
      }
      throw new InternalServerErrorException('Image generation failed');
    }
  }

  // ── Step 3: Poll job status ────────────────────────────────────────────────

  async getJobStatus(requestId: string): Promise<FalGenerationResult> {
    const urls = this.jobUrls.get(requestId);
    const statusUrl = urls?.statusUrl ?? `${QUEUE_BASE}/requests/${requestId}/status`;
    fileLog.info(CTX, `getJobStatus → GET ${statusUrl}`);

    try {
      const res = await axios.get(statusUrl, {
        headers: this.authHeader,
        timeout: 15_000,
      });
      fileLog.info(CTX, `getJobStatus ← ${res.status}`, res.data);

      const raw = (res.data?.status ?? '').toUpperCase();
      const status = this.normalizeStatus(raw);

      // Fetch result URL if completed
      let outputUrl: string | undefined;
      if (status === 'completed') {
        outputUrl = await this.fetchResult(requestId);
      }

      return { requestId, status, outputUrl };
    } catch (err) {
      fileLog.error(CTX, 'getJobStatus FAILED', {
        status: err?.response?.status,
        data: err?.response?.data,
        message: err.message,
      });
      throw new InternalServerErrorException('Failed to fetch job status');
    }
  }

  private async fetchResult(requestId: string): Promise<string | undefined> {
    const urls = this.jobUrls.get(requestId);
    const resultUrl = urls?.resultUrl ?? `${QUEUE_BASE}/requests/${requestId}`;
    fileLog.info(CTX, `fetchResult → GET ${resultUrl}`);
    try {
      const res = await axios.get(resultUrl, { headers: this.authHeader, timeout: 15_000 });
      fileLog.info(CTX, 'fetchResult ←', res.data);
      const imageUrl = res.data?.images?.[0]?.url;
      if (imageUrl) await this.saveOutputImage(requestId, imageUrl);
      return imageUrl;
    } catch (err) {
      fileLog.error(CTX, 'fetchResult FAILED', { message: err.message });
      return undefined;
    }
  }

  private async saveOutputImage(requestId: string, imageUrl: string): Promise<void> {
    try {
      const ts = new Date().toISOString().replace(/:/g, '-').replace(/\..+/, '');
      const ext = imageUrl.endsWith('.png') ? 'png' : 'jpg';
      const filename = `output-${ts}-${requestId.slice(0, 8)}.${ext}`;
      const filepath = path.join(OUTPUT_DIR, filename);

      const imgRes = await axios.get(imageUrl, { responseType: 'arraybuffer', timeout: 30_000 });
      fs.writeFileSync(filepath, Buffer.from(imgRes.data));
      fileLog.info(CTX, `Output image saved → ${filepath}`);
    } catch (err) {
      fileLog.error(CTX, 'saveOutputImage FAILED', { message: err.message });
    }
  }

  private normalizeStatus(raw: string): FalGenerationResult['status'] {
    if (raw === 'COMPLETED') return 'completed';
    if (raw === 'FAILED')    return 'failed';
    if (raw === 'IN_PROGRESS') return 'processing';
    return 'pending';
  }
}
