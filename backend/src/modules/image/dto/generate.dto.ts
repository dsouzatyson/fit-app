import { IsString, IsOptional, IsIn } from 'class-validator';

// Supported models for image-to-image editing
export const SUPPORTED_MODELS = [
  'flux_kontext',       // Context-aware editing + style transfer — best for ref-based editing
  'seedream_v5_lite',   // Instruction-based editing with visual reasoning
  'gpt_image_2',        // OpenAI image editing, 4K support
  'seedream_v4_5',      // Precise transformations
  'image_auto',         // Auto-selects best model
] as const;

export type SupportedModel = (typeof SUPPORTED_MODELS)[number];

export class GenerateDto {
  @IsString()
  sourceMediaId: string; // Higgsfield UUID for image 1 (to be edited)

  @IsString()
  referenceMediaId: string; // Higgsfield UUID for image 2 (reference/style)

  @IsString()
  prompt: string; // Edit instruction, e.g. "Apply the style from the reference image"

  @IsOptional()
  @IsIn(SUPPORTED_MODELS)
  model?: SupportedModel; // Defaults to flux_kontext

  @IsOptional()
  @IsString()
  aspectRatio?: string; // e.g. "1:1", "16:9", "9:16"
}
