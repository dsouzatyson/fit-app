import { Injectable, Logger } from '@nestjs/common';
import { FalService } from './fal.service';
import { GenerateDto } from './dto/generate.dto';

@Injectable()
export class ImageService {
  private readonly logger = new Logger(ImageService.name);

  constructor(private readonly fal: FalService) {}

  async uploadImage(file: Express.Multer.File): Promise<{ mediaId: string }> {
    this.logger.log(`Uploading: ${file.originalname} (${file.size} bytes)`);
    // Returns a fal.ai CDN URL — used directly as mediaId in /generate
    const mediaId = await this.fal.uploadImage(file.buffer, file.originalname, file.mimetype);
    return { mediaId };
  }

  async generate(dto: GenerateDto): Promise<{ jobId: string }> {
    this.logger.log(`Generating — source=${dto.sourceMediaId}, ref=${dto.referenceMediaId}`);
    const jobId = await this.fal.generateImage({
      sourceImageUrl: dto.sourceMediaId,
      referenceImageUrl: dto.referenceMediaId,
      prompt: dto.prompt,
      aspectRatio: dto.aspectRatio,
    });
    return { jobId };
  }

  async getJobStatus(jobId: string) {
    return this.fal.getJobStatus(jobId);
  }
}
