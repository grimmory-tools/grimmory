import {Component, inject, Input} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {TranslocoDirective} from '@jsverse/transloco';
import {ReaderPreferencesService} from '../reader-preferences.service';
import {PageSpread, UserSettings} from '../../user-management/user.service';
import {TooltipModule} from 'primeng/tooltip';

@Component({
  selector: 'app-pdf-reader-preferences-component',
  imports: [
    FormsModule,
    TranslocoDirective,
    TooltipModule
  ],
  templateUrl: './pdf-reader-preferences-component.html',
  styleUrl: './pdf-reader-preferences-component.scss'
})
export class PdfReaderPreferencesComponent {
  private readonly readerPreferencesService = inject(ReaderPreferencesService);

  @Input() userSettings!: UserSettings;

  readonly spreads: {name: string; key: PageSpread; icon: string; translationKey: string}[] = [
    {name: 'Even', key: 'even', icon: 'pi pi-align-left', translationKey: 'even'},
    {name: 'Odd', key: 'odd', icon: 'pi pi-align-right', translationKey: 'odd'},
    {name: 'None', key: 'off', icon: 'pi pi-minus', translationKey: 'none'}
  ];

  readonly zooms: {name: string; key: string; icon: string; translationKey: string}[] = [
    {name: 'Auto Zoom', key: 'auto', icon: 'pi pi-sparkles', translationKey: 'autoZoom'},
    {name: 'Page Fit', key: 'page-fit', icon: 'pi pi-window-maximize', translationKey: 'pageFit'},
    {name: 'Page Width', key: 'page-width', icon: 'pi pi-arrows-h', translationKey: 'pageWidth'},
    {name: 'Actual Size', key: 'page-actual', icon: 'pi pi-expand', translationKey: 'actualSize'}
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
