import {DecimalPipe} from '@angular/common';
import {Component, effect, inject, Input, OnDestroy, OnInit} from '@angular/core';
import {Button} from 'primeng/button';
import {FormsModule} from '@angular/forms';
import {TranslocoDirective} from '@jsverse/transloco';
import {ReaderPreferencesService} from '../reader-preferences.service';
import {UserSettings} from '../../user-management/user.service';
import {Tooltip} from 'primeng/tooltip';
import {CustomFontService} from '../../../../shared/service/custom-font.service';
import {CustomFont} from '../../../../shared/model/custom-font.model';
import {Subject} from 'rxjs';
import {addCustomFontsToDropdown} from '../../../../shared/util/custom-font.util';
import {Skeleton} from 'primeng/skeleton';
import {themes} from '../../../readers/ebook-reader/state/themes.constant';

@Component({
  selector: 'app-epub-reader-preferences-component',
  imports: [
    Button,
    DecimalPipe,
    FormsModule,
    TranslocoDirective,
    Tooltip,
    Skeleton
  ],
  templateUrl: './epub-reader-preferences-component.html',
  styleUrl: './epub-reader-preferences-component.scss'
})
export class EpubReaderPreferencesComponent implements OnInit, OnDestroy {

  @Input() userSettings!: UserSettings;

  private readonly readerPreferencesService = inject(ReaderPreferencesService);
  private readonly customFontService = inject(CustomFontService);
  private readonly destroy$ = new Subject<void>();

  customFonts: CustomFont[] = [];

  fonts = [
    {name: 'Book Default', displayName: 'Default', key: null},
    {name: 'Serif', displayName: 'Serif', key: 'serif'},
    {name: 'Sans Serif', displayName: 'Sans Serif', key: 'sans-serif'},
    {name: 'Roboto', displayName: 'Roboto', key: 'roboto'},
    {name: 'Cursive', displayName: 'Cursive', key: 'cursive'},
    {name: 'Monospace', displayName: 'Monospace', key: 'monospace'}
  ];

  readonly themes = themes;

  customFontsReady = false;

  private readonly syncFontsEffect = effect(() => {
    const fonts = this.customFontService.fonts();
    if (fonts.length > 0 || !this.customFontService.isFontsLoading()) {
      this.customFontsReady = true;
    }
    if (this.hasCustomFontsChanged(fonts)) {
      this.onFontsChanged(fonts);
    }
  });

  ngOnInit(): void {
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private async onFontsChanged(fonts: CustomFont[]): Promise<void> {
    try {
      const selectedFontDeleted = this.isCurrentlySelectedFontDeleted(fonts);

      this.customFonts = fonts;
      await this.customFontService.loadAllFonts(fonts);
      this.updateFontsDropdown(fonts);

      if (selectedFontDeleted) {
        this.resetToDefaultFont();
      }
    } catch (err) {
      console.error('Failed to process custom fonts:', err);
    }
  }

  private hasCustomFontsChanged(newFonts: CustomFont[]): boolean {
    if (newFonts.length !== this.customFonts.length) {
      return true;
    }
    const newIds = new Set(newFonts.map(f => f.id));
    const currentIds = new Set(this.customFonts.map(f => f.id));
    return newFonts.some(f => !currentIds.has(f.id)) || this.customFonts.some(f => !newIds.has(f.id));
  }

  private updateFontsDropdown(fonts: CustomFont[]): void {
    this.fonts = this.fonts.filter(font => !font.key || !font.key.startsWith('custom:'));
    addCustomFontsToDropdown(fonts, this.fonts, 'preference');
  }

  private isCurrentlySelectedFontDeleted(newFonts: CustomFont[]): boolean {
    const fontFamily = this.userSettings.ebookReaderSetting.fontFamily;
    if (!fontFamily || !fontFamily.startsWith('custom:')) {
      return false;
    }

    const fontId = fontFamily.split(':')[1];
    const fontStillExists = newFonts.some(font => font.id === parseInt(fontId, 10));
    return !fontStillExists;
  }

  private resetToDefaultFont(): void {
    console.log('Selected custom font was deleted, resetting to default font');
    this.selectedFont = null;
  }

  get selectedTheme(): string | null {
    return this.userSettings.ebookReaderSetting.theme;
  }

  set selectedTheme(value: string | null) {
    if (typeof value === "string") {
      this.userSettings.ebookReaderSetting.theme = value;
    }
    this.readerPreferencesService.updatePreference(['ebookReaderSetting', 'theme'], value);
  }

  get selectedFont(): string | null {
    return this.userSettings.ebookReaderSetting.fontFamily;
  }

  set selectedFont(value: string | null) {
    if (typeof value === "string") {
      this.userSettings.ebookReaderSetting.fontFamily = value;
    } else {
      this.userSettings.ebookReaderSetting.fontFamily = null as any;
    }
    this.readerPreferencesService.updatePreference(['ebookReaderSetting', 'fontFamily'], value);
  }

  get fontSize(): number {
    return this.userSettings.ebookReaderSetting.fontSize;
  }

  set fontSize(value: number) {
    this.userSettings.ebookReaderSetting.fontSize = value;
    this.readerPreferencesService.updatePreference(['ebookReaderSetting', 'fontSize'], value);
  }

  get lineHeight(): number {
    return this.userSettings.ebookReaderSetting.lineHeight;
  }

  set lineHeight(value: number) {
    this.userSettings.ebookReaderSetting.lineHeight = value;
    this.readerPreferencesService.updatePreference(['ebookReaderSetting', 'lineHeight'], value);
  }

  get justify(): boolean {
    return this.userSettings.ebookReaderSetting.justify;
  }

  set justify(value: boolean) {
    this.userSettings.ebookReaderSetting.justify = value;
    this.readerPreferencesService.updatePreference(['ebookReaderSetting', 'justify'], value);
  }

  get hyphenate(): boolean {
    return this.userSettings.ebookReaderSetting.hyphenate;
  }

  set hyphenate(value: boolean) {
    this.userSettings.ebookReaderSetting.hyphenate = value;
    this.readerPreferencesService.updatePreference(['ebookReaderSetting', 'hyphenate'], value);
  }

  get maxColumnCount(): number {
    return this.userSettings.ebookReaderSetting.maxColumnCount;
  }

  set maxColumnCount(value: number) {
    this.userSettings.ebookReaderSetting.maxColumnCount = value;
    this.readerPreferencesService.updatePreference(['ebookReaderSetting', 'maxColumnCount'], value);
  }

  get gap(): number {
    return this.userSettings.ebookReaderSetting.gap;
  }

  set gap(value: number) {
    this.userSettings.ebookReaderSetting.gap = value;
    this.readerPreferencesService.updatePreference(['ebookReaderSetting', 'gap'], value);
  }

  get maxInlineSize(): number {
    return this.userSettings.ebookReaderSetting.maxInlineSize;
  }

  set maxInlineSize(value: number) {
    this.userSettings.ebookReaderSetting.maxInlineSize = value;
    this.readerPreferencesService.updatePreference(['ebookReaderSetting', 'maxInlineSize'], value);
  }

  get maxBlockSize(): number {
    return this.userSettings.ebookReaderSetting.maxBlockSize;
  }

  set maxBlockSize(value: number) {
    this.userSettings.ebookReaderSetting.maxBlockSize = value;
    this.readerPreferencesService.updatePreference(['ebookReaderSetting', 'maxBlockSize'], value);
  }

  get isDark(): boolean {
    return this.userSettings.ebookReaderSetting.isDark;
  }

  set isDark(value: boolean) {
    this.userSettings.ebookReaderSetting.isDark = value;
    this.readerPreferencesService.updatePreference(['ebookReaderSetting', 'isDark'], value);
  }

  get flow(): 'paginated' | 'scrolled' {
    return this.userSettings.ebookReaderSetting.flow;
  }

  set flow(value: 'paginated' | 'scrolled') {
    this.userSettings.ebookReaderSetting.flow = value;
    this.readerPreferencesService.updatePreference(['ebookReaderSetting', 'flow'], value);
  }

  increaseFontSize() {
    if (this.fontSize < 72) {
      this.fontSize += 1;
    }
  }

  decreaseFontSize() {
    if (this.fontSize > 8) {
      this.fontSize -= 1;
    }
  }

  increaseLineHeight() {
    if (this.lineHeight < 3) {
      this.lineHeight = Math.round((this.lineHeight + 0.1) * 10) / 10;
    }
  }

  decreaseLineHeight() {
    if (this.lineHeight > 1) {
      this.lineHeight = Math.round((this.lineHeight - 0.1) * 10) / 10;
    }
  }

  increaseGap() {

    if (this.gap < 0.5) {
      this.gap = Math.round((this.gap + 0.05) * 100) / 100;
    }
  }

  decreaseGap() {
    if (this.gap > 0) {
      this.gap = Math.round((this.gap - 0.05) * 100) / 100;
    }
  }

  increaseMaxInlineSize() {
    if (this.maxInlineSize < 2000) {
      this.maxInlineSize += 50;
    }
  }

  decreaseMaxInlineSize() {
    if (this.maxInlineSize > 400) {
      this.maxInlineSize -= 50;
    }
  }

  increaseMaxBlockSize() {
    if (this.maxBlockSize < 2000) {
      this.maxBlockSize += 50;
    }
  }

  decreaseMaxBlockSize() {
    if (this.maxBlockSize > 400) {
      this.maxBlockSize -= 50;
    }
  }

  getCustomFontName(fontKey: string): string | null {
    if (!fontKey || !fontKey.startsWith('custom:')) {
      return null;
    }
    const fontId = parseInt(fontKey.split(':')[1]);
    const customFont = this.customFonts.find(f => f.id === fontId);
    return customFont ? customFont.fontName : null;
  }
}
