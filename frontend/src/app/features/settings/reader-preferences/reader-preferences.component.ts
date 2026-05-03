import {Component, effect, inject} from '@angular/core';
import {FormsModule} from '@angular/forms';


import {TranslocoDirective} from '@jsverse/transloco';
import {UserService, UserSettings} from '../user-management/user.service';
import {EpubReaderPreferencesComponent} from './epub-reader-preferences/epub-reader-preferences-component';
import {PdfReaderPreferencesComponent} from './pdf-reader-preferences/pdf-reader-preferences-component';
import {CbxReaderPreferencesComponent} from './cbx-reader-preferences/cbx-reader-preferences-component';
import {CustomFontsComponent} from '../custom-fonts/custom-fonts.component';
import {SettingsApplicationModeComponent} from './settings-application-mode/settings-application-mode.component';
import {LocalSettingsComponent} from "./local-settings/local-settings.component";
import {OfflineSettingsComponent} from "./offline-settings/offline-settings.component";

@Component({
  selector: 'app-reader-preferences',
  templateUrl: './reader-preferences.component.html',
  standalone: true,
  styleUrls: ['./reader-preferences.component.scss'],
  imports: [FormsModule, TranslocoDirective, EpubReaderPreferencesComponent, PdfReaderPreferencesComponent, CbxReaderPreferencesComponent, CustomFontsComponent, SettingsApplicationModeComponent, LocalSettingsComponent, OfflineSettingsComponent]
})
export class ReaderPreferences {
  private readonly userService = inject(UserService);

  userSettings!: UserSettings;

  hasFontManagementPermission = false;

  constructor() {
    effect(() => {
      const user = this.userService.currentUser();
      if (!user) return;

      this.userSettings = user.userSettings;
      const perms = user.permissions;
      this.hasFontManagementPermission = (perms.admin || perms.canManageFonts);
    });
  }
}
