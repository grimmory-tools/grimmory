import {Component, inject, Input} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {TranslocoDirective} from '@jsverse/transloco';
import {CbxBackgroundColor, CbxFitMode, CbxPageSpread, CbxPageViewMode, CbxScrollMode, UserSettings} from '../../user-management/user.service';
import {ReaderPreferencesService} from '../reader-preferences.service';
import {TooltipModule} from 'primeng/tooltip';

@Component({
  selector: 'app-cbx-reader-preferences-component',
  imports: [
    FormsModule,
    TranslocoDirective,
    TooltipModule
  ],
  templateUrl: './cbx-reader-preferences-component.html',
  styleUrl: './cbx-reader-preferences-component.scss'
})
export class CbxReaderPreferencesComponent {

  @Input() userSettings!: UserSettings;

  private readonly readerPreferencesService = inject(ReaderPreferencesService);

  private static readonly SETTING_ROOT = 'cbxReaderSetting';
  private static readonly PROP_PAGE_SPREAD = 'pageSpread';
  private static readonly PROP_PAGE_VIEW_MODE = 'pageViewMode';
  private static readonly PROP_FIT_MODE = 'fitMode';
  private static readonly PROP_SCROLL_MODE = 'scrollMode';
  private static readonly PROP_BACKGROUND_COLOR = 'backgroundColor';

  readonly cbxSpreads = [
    {name: 'Even', key: CbxPageSpread.EVEN, icon: 'pi pi-align-left', translationKey: 'even'},
    {name: 'Odd', key: CbxPageSpread.ODD, icon: 'pi pi-align-right', translationKey: 'odd'}
  ];

  readonly cbxViewModes = [
    {name: 'Single Page', key: CbxPageViewMode.SINGLE_PAGE, icon: 'pi pi-book', translationKey: 'singlePage'},
    {name: 'Two Page', key: CbxPageViewMode.TWO_PAGE, icon: 'pi pi-copy', translationKey: 'twoPage'},
  ];

  readonly cbxFitModes = [
    {name: 'Fit Page', key: CbxFitMode.FIT_PAGE, icon: 'pi pi-window-maximize', translationKey: 'fitPage'},
    {name: 'Fit Width', key: CbxFitMode.FIT_WIDTH, icon: 'pi pi-arrows-h', translationKey: 'fitWidth'},
    {name: 'Fit Height', key: CbxFitMode.FIT_HEIGHT, icon: 'pi pi-arrows-v', translationKey: 'fitHeight'},
    {name: 'Actual Size', key: CbxFitMode.ACTUAL_SIZE, icon: 'pi pi-expand', translationKey: 'actualSize'},
    {name: 'Automatic', key: CbxFitMode.AUTO, icon: 'pi pi-sparkles', translationKey: 'automatic'}
  ];

  readonly cbxScrollModes = [
    {name: 'Paginated', key: CbxScrollMode.PAGINATED, icon: 'pi pi-book', translationKey: 'paginated'},
    {name: 'Infinite', key: CbxScrollMode.INFINITE, icon: 'pi pi-sort-alt', translationKey: 'infinite'}
  ];

  readonly cbxBackgroundColors = [
    {name: 'Gray', key: CbxBackgroundColor.GRAY, color: '#808080', translationKey: 'gray'},
    {name: 'Black', key: CbxBackgroundColor.BLACK, color: '#000000', translationKey: 'black'},
    {name: 'White', key: CbxBackgroundColor.WHITE, color: '#FFFFFF', translationKey: 'white'}
  ];

  get selectedCbxSpread(): CbxPageSpread {
    return this.userSettings.cbxReaderSetting.pageSpread ?? CbxPageSpread.EVEN;
  }

  set selectedCbxSpread(value: CbxPageSpread) {
    this.userSettings.cbxReaderSetting.pageSpread = value;
    this.readerPreferencesService.updatePreference([CbxReaderPreferencesComponent.SETTING_ROOT, CbxReaderPreferencesComponent.PROP_PAGE_SPREAD], value);
  }

  get selectedCbxViewMode(): CbxPageViewMode {
    return this.userSettings.cbxReaderSetting.pageViewMode ?? CbxPageViewMode.SINGLE_PAGE;
  }

  set selectedCbxViewMode(value: CbxPageViewMode) {
    this.userSettings.cbxReaderSetting.pageViewMode = value;
    this.readerPreferencesService.updatePreference([CbxReaderPreferencesComponent.SETTING_ROOT, CbxReaderPreferencesComponent.PROP_PAGE_VIEW_MODE], value);
  }

  get selectedCbxFitMode(): CbxFitMode {
    return this.userSettings.cbxReaderSetting.fitMode ?? CbxFitMode.FIT_PAGE;
  }

  set selectedCbxFitMode(value: CbxFitMode) {
    this.userSettings.cbxReaderSetting.fitMode = value;
    this.readerPreferencesService.updatePreference([CbxReaderPreferencesComponent.SETTING_ROOT, CbxReaderPreferencesComponent.PROP_FIT_MODE], value);
  }

  get selectedCbxScrollMode(): CbxScrollMode {
    return this.userSettings.cbxReaderSetting.scrollMode ?? CbxScrollMode.PAGINATED;
  }

  set selectedCbxScrollMode(value: CbxScrollMode) {
    this.userSettings.cbxReaderSetting.scrollMode = value;
    this.readerPreferencesService.updatePreference([CbxReaderPreferencesComponent.SETTING_ROOT, CbxReaderPreferencesComponent.PROP_SCROLL_MODE], value);
  }

  get selectedCbxBackgroundColor(): CbxBackgroundColor {
    return this.userSettings.cbxReaderSetting.backgroundColor ?? CbxBackgroundColor.GRAY;
  }

  set selectedCbxBackgroundColor(value: CbxBackgroundColor) {
    this.userSettings.cbxReaderSetting.backgroundColor = value;
    this.readerPreferencesService.updatePreference([CbxReaderPreferencesComponent.SETTING_ROOT, CbxReaderPreferencesComponent.PROP_BACKGROUND_COLOR], value);
  }
}
