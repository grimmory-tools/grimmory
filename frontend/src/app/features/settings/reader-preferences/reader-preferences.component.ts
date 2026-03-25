import {Component, effect, inject} from '@angular/core';
import {FormsModule} from '@angular/forms';

import {TooltipModule} from 'primeng/tooltip';
import {TranslocoDirective} from '@jsverse/transloco';
import {UserService, UserSettings} from '../user-management/user.service';
import {ReaderPreferencesService} from './reader-preferences.service';
import {EpubReaderPreferencesComponent} from './epub-reader-preferences/epub-reader-preferences-component';
import {PdfReaderPreferencesComponent} from './pdf-reader-preferences/pdf-reader-preferences-component';
import {CbxReaderPreferencesComponent} from './cbx-reader-preferences/cbx-reader-preferences-component';
import {CustomFontsComponent} from '../custom-fonts/custom-fonts.component';
import {SettingsApplicationModeComponent} from './settings-application-mode/settings-application-mode.component';

@Component({
  selector: 'app-reader-preferences',
  templateUrl: './reader-preferences.component.html',
  standalone: true,
  styleUrls: ['./reader-preferences.component.scss'],
  imports: [FormsModule, TooltipModule, TranslocoDirective, EpubReaderPreferencesComponent, PdfReaderPreferencesComponent, CbxReaderPreferencesComponent, CustomFontsComponent, SettingsApplicationModeComponent]
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
