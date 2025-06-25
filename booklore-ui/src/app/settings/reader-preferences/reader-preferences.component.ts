import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';

import {Observable, Subscription} from 'rxjs';
import {RadioButton} from 'primeng/radiobutton';
import {Divider} from 'primeng/divider';
import {Tooltip} from 'primeng/tooltip';
import {User, UserService, UserSettings} from '../user-management/user.service';
import {EpubReaderPreferencesComponent} from './epub-reader-preferences-component/epub-reader-preferences-component';
import {PdfReaderPreferencesComponent} from './pdf-reader-preferences-component/pdf-reader-preferences-component';
import {CbxReaderPreferencesComponent} from './cbx-reader-preferences-component/cbx-reader-preferences-component';
import {ReaderPreferencesService} from './reader-preferences-service';

@Component({
  selector: 'app-reader-preferences',
  templateUrl: './reader-preferences.component.html',
  standalone: true,
  styleUrls: ['./reader-preferences.component.scss'],
  imports: [FormsModule, RadioButton, Divider, Tooltip, EpubReaderPreferencesComponent, PdfReaderPreferencesComponent, CbxReaderPreferencesComponent]
})
export class ReaderPreferences implements OnInit, OnDestroy {
  readonly scopeOptions = ['Global', 'Individual'];

  selectedPdfScope!: string;
  selectedEpubScope!: string;
  selectedCbxScope!: string;

  private readonly userService = inject(UserService);
  private readonly readerPreferencesService = inject(ReaderPreferencesService);

  userData$: Observable<User | null> = this.userService.userState$;
  private subscription?: Subscription;
  userSettings!: UserSettings;

  ngOnInit(): void {
    this.subscription = this.userData$.subscribe(user => {
      if (user) {
        this.userSettings = user.userSettings;
        this.loadPreferences(user.userSettings);
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private loadPreferences(settings: UserSettings): void {
    this.selectedPdfScope = settings.perBookSetting.pdf;
    this.selectedEpubScope = settings.perBookSetting.epub;
    this.selectedCbxScope = settings.perBookSetting.cbx;
  }

  onPdfScopeChange() {
    this.readerPreferencesService.updatePreference(['perBookSetting', 'pdf'], this.selectedPdfScope);
  }

  onEpubScopeChange() {
    this.readerPreferencesService.updatePreference(['perBookSetting', 'epub'], this.selectedEpubScope);
  }

  onCbxScopeChange() {
    this.readerPreferencesService.updatePreference(['perBookSetting', 'cbx'], this.selectedCbxScope);
  }
}
