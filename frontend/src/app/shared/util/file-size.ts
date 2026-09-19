export const FILE_SIZE_UNITS = [
  {label: 'KB', kb: 1},
  {label: 'MB', kb: 1024},
  {label: 'GB', kb: 1024 ** 2},
  {label: 'TB', kb: 1024 ** 3},
] as const;

export type FileSizeUnit = (typeof FILE_SIZE_UNITS)[number];

export function fileSizeDisplayUnit(fileSizeKb: number): FileSizeUnit {
  return [...FILE_SIZE_UNITS].reverse().find(unit => fileSizeKb >= unit.kb) ?? FILE_SIZE_UNITS[0];
}

export function formatFileSizeKb(fileSizeKb: number): string {
  const unit = fileSizeDisplayUnit(fileSizeKb);
  const size = fileSizeKb / unit.kb;
  const decimals = size >= 100 ? 0 : size >= 10 ? 1 : 2;
  return `${size.toFixed(decimals)} ${unit.label}`;
}
