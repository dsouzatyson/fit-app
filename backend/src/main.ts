import { NestFactory } from '@nestjs/core';
import { ValidationPipe } from '@nestjs/common';
import { AppModule } from './app.module';
import { fileLog } from './common/file-logger';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);

  // Global validation pipe
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
    }),
  );

  // CORS for mobile app
  app.enableCors({
    origin: true,   // reflects any origin, including null (file://)
    methods: ['GET', 'POST'],
    allowedHeaders: ['Content-Type', 'Authorization'],
  });

  // Log every incoming request
  app.use((req: any, res: any, next: any) => {
    fileLog.info('HTTP', `${req.method} ${req.url}`);
    res.on('finish', () => fileLog.info('HTTP', `${req.method} ${req.url} → ${res.statusCode}`));
    next();
  });

  app.setGlobalPrefix('api');

  const port = process.env.PORT ?? 3000;
  await app.listen(port);
  console.log(`🚀 Server running on http://localhost:${port}/api`);
}

bootstrap();
