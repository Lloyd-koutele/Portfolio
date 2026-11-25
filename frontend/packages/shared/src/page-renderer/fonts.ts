import type { FontFamily } from '../types/page';

const STACKS: Record<FontFamily, string> = {
  Inter: "'Inter Variable', 'Inter', ui-sans-serif, system-ui, sans-serif",
  Arial: 'Arial, Helvetica, sans-serif',
  Helvetica: 'Helvetica, Arial, sans-serif',
  Georgia: "Georgia, 'Times New Roman', serif",
  'Times New Roman': "'Times New Roman', Times, serif",
  'Courier New': "'Courier New', Courier, monospace",
  Verdana: 'Verdana, Geneva, sans-serif',
  'Trebuchet MS': "'Trebuchet MS', Helvetica, sans-serif",
  Poppins: "'Poppins', ui-sans-serif, system-ui, sans-serif",
  Montserrat: "'Montserrat Variable', 'Montserrat', ui-sans-serif, system-ui, sans-serif",
  Roboto: "'Roboto Variable', 'Roboto', ui-sans-serif, system-ui, sans-serif",
  'Open Sans': "'Open Sans Variable', 'Open Sans', ui-sans-serif, system-ui, sans-serif",
  'Playfair Display': "'Playfair Display Variable', 'Playfair Display', Georgia, serif",
  Lora: "'Lora Variable', 'Lora', Georgia, serif",
  Merriweather: "'Merriweather Variable', 'Merriweather', Georgia, serif",
  'Fira Code': "'Fira Code Variable', 'Fira Code', ui-monospace, monospace",
};

export function fontStack(family: FontFamily): string {
  return STACKS[family] ?? STACKS.Inter;
}
