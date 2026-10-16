import {inject, signal, type Signal} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from '@openng/optimus-ui/api';

import {resolveSelectedIds, type BrowseSelection} from './selection';

export function injectSelectedIds(
  selection: Signal<BrowseSelection>,
  fetchIds: Signal<() => Promise<readonly number[]>>,
) {
  const transloco = inject(TranslocoService);
  const messageService = inject(MessageService);
  const isResolving = signal(false);

  async function withSelectedIds(run: (ids: readonly number[]) => void): Promise<void> {
    if (isResolving()) {
      return;
    }
    isResolving.set(true);
    let ids: readonly number[];
    try {
      ids = await resolveSelectedIds(selection().state(), fetchIds());
    } catch {
      messageService.add({
        severity: 'error',
        summary: transloco.translate('browse.bulk.selectionLoadError'),
      });
      return;
    } finally {
      isResolving.set(false);
    }
    if (ids.length === 0) {
      selection().clear();
      messageService.add({
        severity: 'info',
        summary: transloco.translate('browse.bulk.selectionEmpty'),
      });
      return;
    }
    run(ids);
  }

  return {isResolving: isResolving.asReadonly(), withSelectedIds};
}
