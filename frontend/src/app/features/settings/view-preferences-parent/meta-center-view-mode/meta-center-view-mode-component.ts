import {Component, effect, inject} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {UserService} from '../../user-management/user.service';
import {MessageService} from 'primeng/api';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-meta-center-view-mode-component',
  imports: [
    FormsModule,
    TranslocoDirective
  ],
  templateUrl: './meta-center-view-mode-component.html',
  styleUrl: './meta-center-view-mode-component.scss'
})
export class MetaCenterViewModeComponent {
  viewMode: 'route' | 'dialog' = 'route';
  seriesViewMode: boolean = false;

  private userService = inject(UserService);
  private messageService = inject(MessageService);
  private t = inject(TranslocoService);
  private hasInitialized = false;

  constructor() {
    effect(() => {
      const user = this.userService.currentUser();
      if (this.hasInitialized || !user) return;
      this.hasInitialized = true;

      const preference = user.userSettings?.metadataCenterViewMode;
      if (preference === 'dialog' || preference === 'route') {
        this.viewMode = preference;
      }
      const seriesPref = user.userSettings?.enableSeriesView;
      if (typeof seriesPref === 'boolean') {
        this.seriesViewMode = seriesPref;
      }
    });
  }

  onViewModeChange(value: 'route' | 'dialog'): void {
    this.viewMode = value;
    this.savePreference(value);
  }

  onSeriesViewModeChange(value: boolean): void {
    this.seriesViewMode = value;
    this.saveSeriesViewPreference(value);
  }

  private savePreference(value: 'route' | 'dialog'): void {
    const user = this.userService.getCurrentUser();
    if (!user) return;

    user.userSettings.metadataCenterViewMode = value;
    this.userService.updateUserSetting(user.id, 'metadataCenterViewMode', value);

    this.messageService.add({
      severity: 'success',
      summary: this.t.translate('settingsView.metaCenter.prefsUpdated'),
      detail: this.t.translate('settingsView.metaCenter.metaCenterSaved'),
      life: 1500,
    });
  }

  private saveSeriesViewPreference(value: boolean): void {
    const user = this.userService.getCurrentUser();
    if (!user) return;

    user.userSettings.enableSeriesView = value;
    this.userService.updateUserSetting(user.id, 'enableSeriesView', value);

    this.messageService.add({
      severity: 'success',
      summary: this.t.translate('settingsView.metaCenter.prefsUpdated'),
      detail: this.t.translate('settingsView.metaCenter.seriesViewSaved'),
      life: 1500,
    });
  }
}
