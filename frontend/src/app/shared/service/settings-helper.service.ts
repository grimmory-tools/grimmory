import {Injectable, inject} from '@angular/core';
import {of} from 'rxjs';
import {AppSettingsService} from './app-settings.service';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {catchError, map} from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class SettingsHelperService {

  private readonly appSettingsService = inject(AppSettingsService);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);

  saveSetting(key: string, value: unknown): void {
    this.appSettingsService.saveSettings([{key, newValue: value}]).pipe(
      map(() => true),
      catchError((error) => {
        console.error('Failed to save setting:', error);
        return of(false);
      })
    ).subscribe((saved) => {
      if (saved) {
        this.showSuccessMessage();
      } else {
        this.showErrorMessage();
      }
    });
  }

  private showSuccessMessage(): void {
    this.messageService.add({
      severity: 'success',
      summary: this.t.translate('shared.settingsHelper.settingsSavedSummary'),
      detail: this.t.translate('shared.settingsHelper.settingsSavedDetail')
    });
  }

  private showErrorMessage(): void {
    this.messageService.add({
      severity: 'error',
      summary: this.t.translate('common.error'),
      detail: this.t.translate('shared.settingsHelper.saveErrorDetail')
    });
  }

  showMessage(severity: 'success' | 'error', summary: string, detail: string): void {
    this.messageService.add({severity, summary, detail});
  }
}

