const mediumDate = new Intl.DateTimeFormat(undefined, {dateStyle: 'medium'});
const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})$/;

export function formatMediumDate(value: string | null | undefined): string {
  if (!value) {
    return '';
  }
  const parts = DATE_ONLY.exec(value);
  const date = parts
    ? new Date(Number(parts[1]), Number(parts[2]) - 1, Number(parts[3]))
    : new Date(value);
  return Number.isNaN(date.getTime()) ? '' : mediumDate.format(date);
}
