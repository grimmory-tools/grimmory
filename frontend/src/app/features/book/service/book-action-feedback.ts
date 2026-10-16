import {HttpErrorResponse} from '@angular/common/http';
import {inject} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from '@openng/optimus-ui/api';
import {BulkBookCommandPartialError} from '../data/book-command.models';

export function injectBookActionFeedback() {
  const messages = inject(MessageService);
  const t = inject(TranslocoService);
  const show = (
    severity: 'success' | 'warn' | 'error',
    key: string,
    params?: Record<string, unknown>,
    detail?: string,
  ) => messages.add({
    severity,
    summary: t.translate(`${key}Summary`),
    detail: detail || t.translate(`${key}Detail`, params),
  });
  return {
    show,
    error: (key: string, error: unknown) => {
      const cause = error instanceof BulkBookCommandPartialError ? error.cause : error;
      const detail = cause instanceof HttpErrorResponse ? cause.error?.message || cause.message
        : cause instanceof Error ? cause.message : undefined;
      show('error', key, undefined, detail);
    },
  };
}
