import {HttpErrorResponse, HttpStatusCode} from '@angular/common/http';
import {inject} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from '@openng/optimus-ui/api';
import {MutationCache} from '@tanstack/angular-query';

declare module '@tanstack/angular-query' {
  interface Register {
    mutationMeta: {readonly errorToast?: false};
  }
}

export function injectCommandErrorToasts(): MutationCache {
  const messages = inject(MessageService);
  const t = inject(TranslocoService);
  return new MutationCache({
    onError: (error, _variables, _onMutateResult, mutation) => {
      if (mutation.meta?.errorToast === false || httpError(error)?.status === HttpStatusCode.Unauthorized) {
        return;
      }
      messages.add({
        severity: 'error',
        summary: t.translate('common.commandFailed'),
        detail: commandErrorDetail(error) ?? t.translate('common.serverUnreachable'),
      });
    },
  });
}

export function commandErrorDetail(error: unknown): string | undefined {
  const response = httpError(error);
  if (!response) {
    return error instanceof Error ? error.message : undefined;
  }
  const body: unknown = response.error;
  const message = typeof body === 'object' && body !== null && 'message' in body ? body.message : undefined;
  return typeof message === 'string' && message ? message : undefined;
}

function httpError(error: unknown): HttpErrorResponse | undefined {
  if (error instanceof HttpErrorResponse) {
    return error;
  }
  return error instanceof Error && error.cause instanceof HttpErrorResponse ? error.cause : undefined;
}
