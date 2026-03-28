import {ChangeDetectionStrategy, Component, inject, Input, OnInit} from '@angular/core';
import {TranslocoDirective} from '@jsverse/transloco';
import {ReaderPreferencesService} from '../reader-preferences.service';
import {UserSettings} from '../../user-management/user.service';

@Component({
  selector: 'app-settings-application-mode',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './settings-application-mode.component.html',
  standalone: true,
  styleUrls: ['./settings-application-mode.component.scss'],
  imports: [TranslocoDirective]
})
export class SettingsApplicationModeComponent implements OnInit {
  @Input() userSettings!: UserSettings;

  readonly scopeOptions = [
    {name: 'Global', key: 'Global', icon: 'pi pi-globe'},
    {name: 'Individual', key: 'Individual', icon: 'pi pi-user'}
  ];

  selectedPdfScope!: string;
  selectedNewPdfScope!: string;
  selectedEpubScope!: string;
  selectedCbxScope!: string;

  private readonly readerPreferencesService = inject(ReaderPreferencesService);

  ngOnInit(): void {
    this.loadPreferences();
  }

  private loadPreferences(): void {
    this.selectedPdfScope = this.userSettings.perBookSetting.pdf;
    this.selectedNewPdfScope = this.userSettings.perBookSetting.newPdf || 'Global';
    this.selectedEpubScope = this.userSettings.perBookSetting.epub;
    this.selectedCbxScope = this.userSettings.perBookSetting.cbx;
  }

  onPdfScopeChange() {
    this.readerPreferencesService.updatePreference(['perBookSetting', 'pdf'], this.selectedPdfScope);
  }

  onNewPdfScopeChange() {
    this.readerPreferencesService.updatePreference(['perBookSetting', 'newPdf'], this.selectedNewPdfScope);
  }

  onEpubScopeChange() {
    this.readerPreferencesService.updatePreference(['perBookSetting', 'epub'], this.selectedEpubScope);
  }

  onCbxScopeChange() {
    this.readerPreferencesService.updatePreference(['perBookSetting', 'cbx'], this.selectedCbxScope);
  }
}
