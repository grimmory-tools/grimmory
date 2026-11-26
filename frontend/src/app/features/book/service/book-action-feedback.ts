import {inject} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from '@openng/optimus-ui/api';
import {commandErrorDetail} from '../../../core/data/command-errors';

export function injectBookActionFeedback() {
  const messages = inject(MessageService);
  const t = inject(TranslocoService);
  const show = (
    severity: 'success' | 'warn' | 'error',
    key: string,
    params?: Readonly<Record<string, string | number>>,
    detail?: string,
  ) => messages.add({
    severity,
    summary: t.translate(`${key}Summary`),
    detail: detail || t.translate(`${key}Detail`, params),
  });
  return {
    show,
    error: (key: string, error: unknown) => {
      show('error', key, undefined, commandErrorDetail(error));
    },
  };
}
