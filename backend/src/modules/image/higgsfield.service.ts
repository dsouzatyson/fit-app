import { Injectable, Logger, InternalServerErrorException, ServiceUnavailableException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import axios, { AxiosInstance } from 'axios';
import { fileLog } from '../../common/file-logger';

const CTX = 'HiggsfieldService';

const RETRYABLE_STATUS = [522, 521, 520, 503, 502, 429];
const RETRY_DELAYS_MS  = [5_000, 10_000, 20_000]; // 3 attempts, ~35s total

async function withRetry<T>(label: string, fn: () => Promise<T>): Promise<T> {
  for (let attempt = 0; attempt <= RETRY_DELAYS_MS.length; attempt++) {
    try {
      return await fn();
    } catch (err) {
      const status = err?.response?.status;
      const retryable = RETRYABLE_STATUS.includes(status);
      const hasMore = attempt < RETRY_DELAYS_MS.length;

      if (retryable && hasMore) {
        const delay = RETRY_DELAYS_MS[attempt];
        fileLog.warn(CTX, `${label} got ${status} — retrying in ${delay / 1000}s (attempt ${attempt + 1})`);
        await new Promise(r => setTimeout(r, delay));
        continue;
      }
      throw err;
    }
  }
}

export interface MediaUploadResult {
  mediaId: string;
  uploadUrl: string;
  cdnUrl: string;
}

export interface GenerationResult {
  jobId: string;
  status: 'pending' | 'processing' | 'completed' | 'failed';
  outputUrl?: string;
}

@Injectable()
export class HiggsfieldService {
  private readonly logger = new Logger(HiggsfieldService.name);
  private readonly http: AxiosInstance;

  constructor(private readonly config: ConfigService) {
    this.http = axios.create({
      baseURL: config.get('HIGGSFIELD_BASE_URL', 'https://api.higgsfield.ai'),
      headers: {
        Authorization: `Bearer ${config.get('HIGGSFIELD_API_KEY')}`,
        'Content-Type': 'application/json',
      },
      timeout: 30_000,
    });
  }

  /**
   * Step 1: Request a presigned upload URL from Higgsfield
   */
  async requestUploadUrl(filename: string, contentType: string): Promise<MediaUploadResult> {
    const body = { method: 'upload_url', filename, content_type: contentType };
    fileLog.info(CTX, 'requestUploadUrl → POST /v1/media/upload', body);
    try {
      const res = await withRetry('requestUploadUrl', () => this.http.post('/v1/media/upload', body));
      fileLog.info(CTX, `requestUploadUrl ← ${res.status}`, res.data);
      const { media_id, upload_url, cdn_url } = res.data;
      return { mediaId: media_id, uploadUrl: upload_url, cdnUrl: cdn_url };
    } catch (err) {
      const status = err?.response?.status;
      fileLog.error(CTX, 'requestUploadUrl FAILED (all retries exhausted)', {
        status,
        data: err?.response?.data,
        message: err.message,
      });
      if (RETRYABLE_STATUS.includes(status)) {
        throw new ServiceUnavailableException('Higgsfield API is temporarily unavailable. Please try again in a minute.');
      }
      throw new InternalServerErrorException('Failed to get upload URL from Higgsfield');
    }
  }

  /**
   * Step 2: PUT raw image bytes to the presigned S3 URL
   */
  async uploadImageBytes(uploadUrl: string, buffer: Buffer, contentType: string): Promise<void> {
    fileLog.info(CTX, `uploadImageBytes → PUT presigned S3 URL`, { bytes: buffer.length, contentType });
    try {
      const res = await withRetry('uploadImageBytes', () =>
        axios.put(uploadUrl, buffer, {
          headers: { 'Content-Type': contentType },
          maxBodyLength: Infinity,
          timeout: 60_000,
        })
      );
      fileLog.info(CTX, `uploadImageBytes ← ${res.status}`);
    } catch (err) {
      fileLog.error(CTX, 'uploadImageBytes FAILED', {
        status: err?.response?.status,
        data: err?.response?.data,
        message: err.message,
      });
      throw new InternalServerErrorException('Failed to upload image to storage');
    }
  }

  /**
   * Step 3: Confirm the upload so Higgsfield processes it
   */
  async confirmUpload(mediaId: string): Promise<string> {
    const body = { media_id: mediaId, type: 'image' };
    fileLog.info(CTX, 'confirmUpload → POST /v1/media/confirm', body);
    try {
      const res = await withRetry('confirmUpload', () => this.http.post('/v1/media/confirm', body));
      fileLog.info(CTX, `confirmUpload ← ${res.status}`, res.data);
      return res.data.media_id as string;
    } catch (err) {
      const status = err?.response?.status;
      fileLog.error(CTX, 'confirmUpload FAILED', {
        status,
        data: err?.response?.data,
        message: err.message,
      });
      if (RETRYABLE_STATUS.includes(status)) {
        throw new ServiceUnavailableException('Higgsfield API is temporarily unavailable. Please try again in a minute.');
      }
      throw new InternalServerErrorException('Failed to confirm image upload');
    }
  }

  /**
   * Full upload pipeline: request URL → upload bytes → confirm
   * Returns confirmed media UUID for use in generation
   */
  async uploadImage(buffer: Buffer, filename: string, contentType: string): Promise<string> {
    fileLog.info(CTX, `uploadImage START — file=${filename} size=${buffer.length} type=${contentType}`);
    const { mediaId, uploadUrl } = await this.requestUploadUrl(filename, contentType);
    await this.uploadImageBytes(uploadUrl, buffer, contentType);
    const confirmedId = await this.confirmUpload(mediaId);
    fileLog.info(CTX, `uploadImage DONE — mediaId=${confirmedId}`);
    return confirmedId;
  }

  /**
   * Submit an image generation job
   */
  async generateImage(params: {
    model: string;
    prompt: string;
    sourceMediaId: string;
    referenceMediaId: string;
    aspectRatio?: string;
  }): Promise<string> {
    const { model, prompt, sourceMediaId, referenceMediaId, aspectRatio } = params;

    const body: any = {
      model,
      prompt,
      medias: [
        { value: sourceMediaId, role: 'image' },
        { value: referenceMediaId, role: 'image' },
      ],
    };

    if (aspectRatio) body.aspect_ratio = aspectRatio;

    try {
      const res = await this.http.post('/v1/images/generate', body);
      const jobId = res.data?.job_id ?? res.data?.id;
      this.logger.log(`Generation job submitted: ${jobId}`);
      return jobId;
    } catch (err) {
      this.logger.error('Generation failed', err?.response?.data ?? err.message);
      throw new InternalServerErrorException('Image generation failed');
    }
  }

  /**
   * Poll job status and return result URL when complete
   */
  async getJobStatus(jobId: string): Promise<GenerationResult> {
    try {
      const res = await this.http.get(`/v1/jobs/${jobId}`);
      const data = res.data;

      const status = this.normalizeStatus(data.status);
      const outputUrl = data.results?.[0]?.url ?? data.output_url ?? null;

      return { jobId, status, outputUrl };
    } catch (err) {
      this.logger.error('Failed to get job status', err?.response?.data ?? err.message);
      throw new InternalServerErrorException('Failed to fetch job status');
    }
  }

  private normalizeStatus(raw: string): GenerationResult['status'] {
    const s = (raw ?? '').toLowerCase();
    if (s === 'completed' || s === 'done' || s === 'success') return 'completed';
    if (s === 'failed' || s === 'error') return 'failed';
    if (s === 'processing' || s === 'running') return 'processing';
    return 'pending';
  }
}
