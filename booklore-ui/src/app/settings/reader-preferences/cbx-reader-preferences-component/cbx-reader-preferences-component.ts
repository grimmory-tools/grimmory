import {Component, inject, Input} from '@angular/core';
import {RadioButton} from 'primeng/radiobutton';
import {Tooltip} from 'primeng/tooltip';
import {FormsModule} from '@angular/forms';
import {CbxPageSpread, CbxPageViewMode} from '../../../book/model/book.model';
import {UserSettings} from '../../user-management/user.service';
import {ReaderPreferencesService} from '../reader-preferences-service';

@Component({
  selector: 'app-cbx-reader-preferences-component',
  imports: [
    RadioButton,
    Tooltip,
    FormsModule
  ],
  templateUrl: './cbx-reader-preferences-component.html',
  styleUrl: './cbx-reader-preferences-component.scss'
})
export class CbxReaderPreferencesComponent {

  @Input() userSettings!: UserSettings;

  private readonly readerPreferencesService = inject(ReaderPreferencesService);

  readonly cbxSpreads = [
    { name: 'Even', key: 'EVEN' },
    { name: 'Odd', key: 'ODD' }
  ];

  readonly cbxViewModes = [
    { name: 'Single Page', key: 'SINGLE_PAGE' },
    { name: 'Two Page', key: 'TWO_PAGE' },
  ];

  get selectedCbxSpread(): CbxPageSpread {
    return this.userSettings.cbxReaderSetting.pageSpread;
  }
  set selectedCbxSpread(value: CbxPageSpread) {
    this.userSettings.cbxReaderSetting.pageSpread = value;
    this.readerPreferencesService.updatePreference(['cbxReaderSetting', 'pageSpread'], value);
  }

  get selectedCbxViewMode(): CbxPageViewMode {
    return this.userSettings.cbxReaderSetting.pageViewMode;
  }
  set selectedCbxViewMode(value: CbxPageViewMode) {
    this.userSettings.cbxReaderSetting.pageViewMode = value;
    this.readerPreferencesService.updatePreference(['cbxReaderSetting', 'pageViewMode'], value);
  }
}
