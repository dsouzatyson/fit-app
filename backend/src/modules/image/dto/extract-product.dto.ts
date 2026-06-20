import { IsUrl } from 'class-validator';

export class ExtractProductDto {
  @IsUrl({}, { message: 'Must be a valid URL' })
  url: string;
}
