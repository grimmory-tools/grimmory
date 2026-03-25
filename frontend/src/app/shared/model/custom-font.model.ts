export interface CustomFont {
  id: number;
  fontName: string;
  originalFileName: string;
  format: FontFormat;
  fileSize: number;
  uploadedAt: string;
}

export enum FontFormat {
  TTF = 'TTF',
  OTF = 'OTF',
  WOFF = 'WOFF',
  WOFF2 = 'WOFF2'
}

export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}
