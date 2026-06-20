import { IsUrl } from 'class-validator';

export class UploadUrlDto {
  @IsUrl({}, { message: 'Must be a valid URL' })
  url: string;
}
