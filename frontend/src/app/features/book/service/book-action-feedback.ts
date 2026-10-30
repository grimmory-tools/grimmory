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
      show('error', key, undefined, errorDetail(error instanceof BulkBookCommandPartialError ? error.cause : error));
    },
  };
}

function errorDetail(cause: unknown): string | undefined {
  if (cause instanceof HttpErrorResponse) {
    const body: unknown = cause.error;
    const serverMessage = typeof body === 'object' && body !== null && 'message' in body ? body.message : undefined;
    return typeof serverMessage === 'string' && serverMessage ? serverMessage : cause.message;
  }
  return cause instanceof Error ? cause.message : undefined;
}
