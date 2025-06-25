import {Component, inject, Input} from '@angular/core';
import {Button} from 'primeng/button';
import {Select} from 'primeng/select';
import {Tooltip} from 'primeng/tooltip';
import {FormsModule} from '@angular/forms';
import {ReaderPreferencesService} from '../reader-preferences-service';
import {UserSettings} from '../../user-management/user.service';

@Component({
  selector: 'app-epub-reader-preferences-component',
  imports: [
    Button,
    Select,
    Tooltip,
    FormsModule
  ],
  templateUrl: './epub-reader-preferences-component.html',
  styleUrl: './epub-reader-preferences-component.scss'
})
export class EpubReaderPreferencesComponent {

  @Input() userSettings!: UserSettings;

  private readonly readerPreferencesService = inject(ReaderPreferencesService);

  readonly fonts = [
    {name: 'Book Default', key: null},
    {name: 'Serif', key: 'serif'},
    {name: 'Sans Serif', key: 'sans-serif'},
    {name: 'Roboto', key: 'roboto'},
    {name: 'Cursive', key: 'cursive'},
    {name: 'Monospace', key: 'monospace'}
  ];

  readonly flowOptions = [
    {name: 'Book Default', key: null},
    {name: 'Paginated', key: 'paginated'},
    {name: 'Scrolled', key: 'scrolled'}
  ];

  readonly themes = [
    {name: 'Book Default', key: null},
    {name: 'White', key: 'white'},
    {name: 'Black', key: 'black'},
    {name: 'Grey', key: 'grey'},
    {name: 'Sepia', key: 'sepia'}
  ];

  get selectedTheme(): string | null {
    return this.userSettings.epubReaderSetting.theme;
  }

  set selectedTheme(value: string | null) {
    if (typeof value === "string") {
      this.userSettings.epubReaderSetting.theme = value;
    }
    this.readerPreferencesService.updatePreference(['epubReaderSetting', 'theme'], value);
  }

  get selectedFont(): string | null {
    return this.userSettings.epubReaderSetting.font;
  }

  set selectedFont(value: string | null) {
    if (typeof value === "string") {
      this.userSettings.epubReaderSetting.font = value;
    }
    this.readerPreferencesService.updatePreference(['epubReaderSetting', 'font'], value);
  }

  get selectedFlow(): string | null {
    return this.userSettings.epubReaderSetting.flow;
  }

  set selectedFlow(value: string | null) {
    if (typeof value === "string") {
      this.userSettings.epubReaderSetting.flow = value;
    }
    this.readerPreferencesService.updatePreference(['epubReaderSetting', 'flow'], value);
  }

  get fontSize(): number {
    return this.userSettings.epubReaderSetting.fontSize;
  }

  set fontSize(value: number) {
    this.userSettings.epubReaderSetting.fontSize = value;
    this.readerPreferencesService.updatePreference(['epubReaderSetting', 'fontSize'], value);
  }

  increaseFontSize() {
    if (this.fontSize < 250) {
      this.fontSize += 10;
    }
  }

  decreaseFontSize() {
    if (this.fontSize > 50) {
      this.fontSize -= 10;
    }
  }
}
