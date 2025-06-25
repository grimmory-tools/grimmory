import {inject, Injectable} from '@angular/core';
import {User, UserService, UserSettings} from '../user-management/user.service';
import {MessageService} from 'primeng/api';

@Injectable({ providedIn: 'root' })
export class ReaderPreferencesService {
  private readonly userService = inject(UserService);
  private readonly messageService = inject(MessageService);
  private currentUser: User | null = null;

  constructor() {
    this.userService.userState$.subscribe(user => this.currentUser = user);
  }

  updatePreference(path: string[], value: any): void {
    if (!this.currentUser) return;

    let target: any = this.currentUser.userSettings;
    for (let i = 0; i < path.length - 1; i++) {
      target = target[path[i]] ||= {};
    }
    target[path.at(-1)!] = value;

    const [rootKey] = path;
    const updatedValue = this.currentUser.userSettings[rootKey as keyof UserSettings];

    this.userService.updateUserSetting(this.currentUser.id, rootKey, updatedValue);
    this.messageService.add({
      severity: 'success',
      summary: 'Preferences Updated',
      detail: 'Your preferences have been saved successfully.',
      life: 2000
    });
  }
}
