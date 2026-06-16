import { Module } from '@nestjs/common';
import { ImageController } from './image.controller';
import { ImageService } from './image.service';
import { FalService } from './fal.service';

@Module({
  controllers: [ImageController],
  providers: [ImageService, FalService],
})
export class ImageModule {}
