import {effect, inject, Injectable} from '@angular/core';
import {User, UserService, UserSettings} from '../user-management/user.service';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';

type MutableSettingsBranch = Record<string, unknown>;

@Injectable({providedIn: 'root'})
export class ReaderPreferencesService {
  private readonly userService = inject(UserService);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);
  private currentUser: User | null = null;

  constructor() {
    effect(() => {
      const user = this.userService.currentUser();
      if (user) {
        this.currentUser = user;
      }
    });
  }

  updatePreference(path: string[], value: unknown, options?: { silent?: boolean }): void {
    if (!this.currentUser) return;

    let target = this.currentUser.userSettings as unknown as MutableSettingsBranch;
    for (let i = 0; i < path.length - 1; i++) {
      const next = target[path[i]];
      if (!next || typeof next !== 'object') {
        target[path[i]] = {};
      }
      target = target[path[i]] as MutableSettingsBranch;
    }
    target[path.at(-1)!] = value;

    const [rootKey] = path;
    const updatedValue = this.currentUser.userSettings[rootKey as keyof UserSettings];

    this.userService.updateUserSetting(this.currentUser.id, rootKey, updatedValue);
    if (!options?.silent) {
      this.messageService.add({
        severity: 'success',
        summary: this.t.translate('settingsReader.toast.preferencesUpdated'),
        detail: this.t.translate('settingsReader.toast.preferencesUpdatedDetail'),
        life: 2000
      });
    }
  }
}
