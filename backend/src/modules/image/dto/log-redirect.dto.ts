import { IsString, IsBoolean, IsOptional } from 'class-validator';

export class LogRedirectDto {
  @IsString()
  originalUrl: string;

  @IsString()
  finalUrl: string;

  @IsBoolean()
  affiliateTagAdded: boolean;

  @IsOptional()
  @IsString()
  affiliateTag?: string;
}
