import {inject, linkedSignal} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from '@openng/optimus-ui/api';

import {browseFacetMatchAll, withBrowseFacetMatchAll} from '../../../shared/browse/facets';
import {UserService} from '../../settings/user-management/user.service';
import {type BookBrowseUrlState} from './book-browse-url-state';

export function createBookBrowseFilterSettings(urlState: BookBrowseUrlState) {
  const userService = inject(UserService);
  const messages = inject(MessageService);
  const transloco = inject(TranslocoService);

  const matchAll = linkedSignal(() =>
    browseFacetMatchAll(urlState.facets()) ?? userService.currentUser()?.userSettings?.filterMatchAll === true);
  const excludeOnTick = linkedSignal(() => userService.currentUser()?.userSettings?.filterExcludeOnTick === true);

  function onSaveError(): void {
    messages.add({
      severity: 'error',
      summary: transloco.translate('common.error'),
      detail: transloco.translate('settingsView.saveFailedDetail'),
    });
  }

  function saveSetting(key: string, value: boolean): void {
    const user = userService.currentUser();
    if (user) {
      userService.updateUserSetting(user.id, key, value, onSaveError);
    }
  }

  function saveMatchAll(next: boolean): void {
    matchAll.set(next);
    saveSetting('filterMatchAll', next);
  }

  return {
    matchAll: matchAll.asReadonly(),
    excludeOnTick: excludeOnTick.asReadonly(),
    saveMatchAll,

    setMatchAll(next: boolean): void {
      saveMatchAll(next);
      urlState.setFacets(withBrowseFacetMatchAll(urlState.facets(), next));
    },

    setExcludeOnTick(next: boolean): void {
      excludeOnTick.set(next);
      saveSetting('filterExcludeOnTick', next);
    },
  };
}
