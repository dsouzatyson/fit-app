import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { ImageModule } from './modules/image/image.module';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    ImageModule,
  ],
})
export class AppModule {}
