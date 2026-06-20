import {
  Controller,
  Post,
  Get,
  Param,
  Body,
  UploadedFile,
  UseInterceptors,
  BadRequestException,
  HttpCode,
  HttpStatus,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { memoryStorage } from 'multer';
import { ImageService } from './image.service';
import { GenerateDto } from './dto/generate.dto';
import { UploadUrlDto } from './dto/upload-url.dto';
import { ExtractProductDto } from './dto/extract-product.dto';

const ALLOWED_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const MAX_FILE_SIZE_MB = 10;

@Controller('images')
export class ImageController {
  constructor(private readonly imageService: ImageService) {}

  /**
   * POST /api/images/upload
   * Upload one image. Returns mediaId to use in /generate.
   *
   * Mobile usage:
   *   - Call this twice: once for image 1, once for image 2
   *   - Use returned mediaIds in /generate
   */
  @Post('upload')
  @HttpCode(HttpStatus.OK)
  @UseInterceptors(
    FileInterceptor('file', {
      storage: memoryStorage(),
      limits: { fileSize: MAX_FILE_SIZE_MB * 1024 * 1024 },
      fileFilter: (_, file, cb) => {
        if (!ALLOWED_MIME_TYPES.includes(file.mimetype)) {
          return cb(new BadRequestException(`Unsupported file type: ${file.mimetype}`), false);
        }
        cb(null, true);
      },
    }),
  )
  async upload(@UploadedFile() file: Express.Multer.File) {
    if (!file) throw new BadRequestException('No file provided. Use field name "file".');
    const result = await this.imageService.uploadImage(file);
    return {
      success: true,
      mediaId: result.mediaId,
      message: 'Image uploaded. Use this mediaId in /generate.',
    };
  }

  /**
   * POST /api/images/upload-url
   * Fetch an image from an external URL and upload it to fal.ai storage.
   * Returns mediaId (CDN URL) to use in /generate — same as /upload.
   *
   * Body: { url: "https://..." }
   */
  @Post('upload-url')
  @HttpCode(HttpStatus.OK)
  async uploadFromUrl(@Body() dto: UploadUrlDto) {
    const result = await this.imageService.uploadImageFromUrl(dto.url);
    return {
      success: true,
      mediaId: result.mediaId,
      message: 'Image fetched and uploaded. Use this mediaId in /generate.',
    };
  }

  /**
   * POST /api/images/extract-product
   * Given any product page URL (Amazon share link, full URL, etc.),
   * extracts the main product image, uploads it to fal.ai, and returns a mediaId.
   *
   * Body: { url: "https://amzn.in/d/..." }
   * Returns: { mediaId, imageUrl, productTitle }
   */
  @Post('extract-product')
  @HttpCode(HttpStatus.OK)
  async extractProduct(@Body() dto: ExtractProductDto) {
    const result = await this.imageService.extractProductImage(dto.url);
    return {
      success: true,
      ...result,
      message: 'Product image extracted. Use mediaId in /generate.',
    };
  }

  /**
   * POST /api/images/generate
   * Submit an edit job. Returns jobId to poll.
   *
   * Body: { sourceMediaId, referenceMediaId, prompt, model?, aspectRatio? }
   */
  @Post('generate')
  @HttpCode(HttpStatus.ACCEPTED)
  async generate(@Body() dto: GenerateDto) {
    const result = await this.imageService.generate(dto);
    return {
      success: true,
      jobId: result.jobId,
      message: 'Generation started. Poll /jobs/:jobId for the result.',
    };
  }

  /**
   * GET /api/images/jobs/:jobId
   * Poll job status. When status === "completed", outputUrl contains the image URL.
   *
   * Poll every 2-3 seconds from the mobile app until status is "completed" or "failed".
   */
  @Get('jobs/:jobId')
  async getJobStatus(@Param('jobId') jobId: string) {
    const result = await this.imageService.getJobStatus(jobId);
    return {
      success: true,
      jobId,
      ...result,
    };
  }
}
