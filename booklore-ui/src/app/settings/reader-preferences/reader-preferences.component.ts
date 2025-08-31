import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {filter, takeUntil} from 'rxjs/operators';

import {Observable, Subject} from 'rxjs';
import {RadioButton} from 'primeng/radiobutton';
import {UserService, UserSettings, UserState} from '../user-management/user.service';
import {EpubReaderPreferencesComponent} from './epub-reader-preferences-component/epub-reader-preferences-component';
import {PdfReaderPreferencesComponent} from './pdf-reader-preferences-component/pdf-reader-preferences-component';
import {CbxReaderPreferencesComponent} from './cbx-reader-preferences-component/cbx-reader-preferences-component';
import {ReaderPreferencesService} from './reader-preferences-service';

@Component({
  selector: 'app-reader-preferences',
  templateUrl: './reader-preferences.component.html',
  standalone: true,
  styleUrls: ['./reader-preferences.component.scss'],
  imports: [FormsModule, RadioButton, EpubReaderPreferencesComponent, PdfReaderPreferencesComponent, CbxReaderPreferencesComponent]
})
export class ReaderPreferences implements OnInit, OnDestroy {
  readonly scopeOptions = ['Global', 'Individual'];

  selectedPdfScope!: string;
  selectedEpubScope!: string;
  selectedCbxScope!: string;

  private readonly userService = inject(UserService);
  private readonly readerPreferencesService = inject(ReaderPreferencesService);
  private readonly destroy$ = new Subject<void>();

  userData$: Observable<UserState> = this.userService.userState$;
  userSettings!: UserSettings;

  ngOnInit(): void {
    this.userData$.pipe(
      filter(userState => !!userState?.user && userState.loaded),
      takeUntil(this.destroy$)
    ).subscribe(userState => {
      this.userSettings = userState.user!.userSettings;
      this.loadPreferences(userState.user!.userSettings);
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
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
