import {Component, inject, Input} from '@angular/core';
import {RadioButton} from 'primeng/radiobutton';
import {FormsModule} from '@angular/forms';
import {ReaderPreferencesService} from '../reader-preferences-service';
import {UserSettings} from '../../user-management/user.service';

@Component({
  selector: 'app-pdf-reader-preferences-component',
  imports: [
    RadioButton,
    FormsModule
  ],
  templateUrl: './pdf-reader-preferences-component.html',
  styleUrl: './pdf-reader-preferences-component.scss'
})
export class PdfReaderPreferencesComponent {
  private readonly readerPreferencesService = inject(ReaderPreferencesService);

  @Input() userSettings!: UserSettings;

  readonly spreads = [
    {name: 'Even', key: 'even'},
    {name: 'Odd', key: 'odd'},
    {name: 'None', key: 'off'}
  ];

  readonly zooms = [
    {name: 'Auto Zoom', key: 'auto'},
    {name: 'Page Fit', key: 'page-fit'},
    {name: 'Page Width', key: 'page-width'},
    {name: 'Actual Size', key: 'page-actual'}
  ];

  get selectedSpread(): 'even' | 'odd' | 'off' {
    return this.userSettings.pdfReaderSetting.pageSpread;
  }

  set selectedSpread(value: 'even' | 'odd' | 'off') {
    this.userSettings.pdfReaderSetting.pageSpread = value;
    this.readerPreferencesService.updatePreference(['pdfReaderSetting', 'pageSpread'], value);
  }

  get selectedZoom(): string {
    return this.userSettings.pdfReaderSetting.pageZoom;
  }

  set selectedZoom(value: string) {
    this.userSettings.pdfReaderSetting.pageZoom = value;
    this.readerPreferencesService.updatePreference(['pdfReaderSetting', 'pageZoom'], value);
  }
}
