import {inject} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from '@openng/optimus-ui/api';
import {injectMutation} from '@tanstack/angular-query-experimental';

import {AuthorCommandService} from '../data/author-command.service';

export function injectAuthorQuickMatch() {
  const authorCommands = inject(AuthorCommandService);
  const messageService = inject(MessageService);
  const transloco = inject(TranslocoService);
  const mutation = injectMutation(() => authorCommands.quickMatch());

  function toast(severity: 'success' | 'error', key: 'success' | 'failed'): void {
    messageService.add({
      severity,
      summary: transloco.translate(`author.quickMatch.${key}Summary`),
      detail: transloco.translate(`author.quickMatch.${key}Detail`),
    });
  }

  return {
    isPending: mutation.isPending,
    run(authorId: number, onMatched?: () => void): void {
      mutation.mutate({authorId}, {
        onSuccess: () => {
          onMatched?.();
          toast('success', 'success');
        },
        onError: () => toast('error', 'failed'),
      });
    },
  };
}
