import {inject} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {TranslocoService} from '@jsverse/transloco';

import {BOOK_READ_STATUS_LABEL_KEYS, CLEAR_BOOK_READ_STATUS_LABEL_KEY} from '../../../book/components/book-read-status-options';
import {type KnownBookReadStatus} from '../../../book/data/book-response.models';

export const STATS_CHART_HOST_CLASS = 'flex h-full min-h-0 min-w-0 flex-1 flex-col gap-4';

export function injectStatsText(prefix: string) {
  const transloco = inject(TranslocoService);
  const activeLang = toSignal(transloco.langChanges$, {
    initialValue: transloco.getActiveLang(),
  });
  const translate = (key: string, params?: Readonly<Record<string, string | number>>) => {
    activeLang();
    return transloco.translate(key, params);
  };

  return {
    locale: activeLang,
    t: (key: string, params?: Readonly<Record<string, string | number>>) => translate(`${prefix}.${key}`, params),
    readStatus: (status: KnownBookReadStatus) =>
      translate(status === 'UNSET' ? CLEAR_BOOK_READ_STATUS_LABEL_KEY : BOOK_READ_STATUS_LABEL_KEYS[status]),
    number: (value: number, options?: Intl.NumberFormatOptions) =>
      value.toLocaleString(activeLang(), options),
    stars: (value: number, options: Intl.NumberFormatOptions = {maximumFractionDigits: 1}) =>
      `${value.toLocaleString(activeLang(), options)}★`,
    date: (key: string, options: Intl.DateTimeFormatOptions) =>
      new Intl.DateTimeFormat(activeLang(), {...options, timeZone: 'UTC'})
        .format(Date.parse(key.length === 7 ? `${key}-01` : key)),
    hour: (hour: number, minute?: '2-digit') =>
      new Intl.DateTimeFormat(activeLang(), {hour: 'numeric', minute, timeZone: 'UTC'})
        .format(Date.UTC(2023, 0, 1, 0, Math.round(hour * 60))),
    duration: (minutes: number) => {
      const format = (value: number, unit: 'hour' | 'minute') =>
        value.toLocaleString(activeLang(), {style: 'unit', unit, unitDisplay: 'narrow'});
      const rounded = Math.round(minutes);
      if (rounded === 0 && minutes > 0) return `<${format(1, 'minute')}`;
      const hours = Math.floor(rounded / 60);
      if (hours === 0) return format(rounded, 'minute');
      return rounded % 60 === 0 ? format(hours, 'hour') : `${format(hours, 'hour')} ${format(rounded % 60, 'minute')}`;
    },
    weekday: (isoWeekday: number, style: 'short' | 'long' = 'short') =>
      new Intl.DateTimeFormat(activeLang(), {weekday: style, timeZone: 'UTC'}).format(Date.UTC(2023, 0, 1 + isoWeekday)),
  };
}
