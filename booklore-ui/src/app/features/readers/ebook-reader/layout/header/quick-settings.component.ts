import {Component, EventEmitter, Input, Output} from '@angular/core';
import {DecimalPipe} from '@angular/common';
import {TranslocoDirective} from '@jsverse/transloco';
import {ReaderStateService} from '../../state/reader-state.service';
import {ReaderIconComponent} from '../../shared/icon.component';
import {BookService} from '../../../../book/service/book.service';
import {EbookViewerSetting} from '../../../../book/model/book.model';

@Component({
  selector: 'app-reader-quick-settings',
  standalone: true,
  imports: [DecimalPipe, TranslocoDirective, ReaderIconComponent],
  templateUrl: './quick-settings.component.html',
  styleUrls: ['./quick-settings.component.scss']
})
export class ReaderQuickSettingsComponent {
  @Input() stateService!: ReaderStateService;
  @Input() bookId!: number;
  @Output() close = new EventEmitter<void>();
  @Output() openFullSettings = new EventEmitter<void>();

  constructor(private bookService: BookService) {}

  get state() {
    return this.stateService.currentState;
  }

  get isDarkMode(): boolean {
    return this.state.isDark;
  }

  private syncSettingsToBackend(): void {
    const setting: EbookViewerSetting = {
      lineHeight: this.state.lineHeight,
      justify: this.state.justify,
      hyphenate: this.state.hyphenate,
      maxColumnCount: this.state.maxColumnCount,
      gap: this.state.gap,
      fontSize: this.state.fontSize,
      theme: typeof this.state.theme === 'object' && 'name' in this.state.theme ? this.state.theme.name : (this.state.theme as any),
      maxInlineSize: this.state.maxInlineSize,
      maxBlockSize: this.state.maxBlockSize,
      fontFamily: this.state.fontFamily,
      isDark: this.state.isDark,
      flow: this.state.flow,
    };
    this.bookService.updateViewerSetting({ebookSettings: setting}, this.bookId).subscribe();
  }

  toggleDarkMode(): void {
    this.stateService.toggleDarkMode();
    this.syncSettingsToBackend();
  }

  increaseFontSize(): void {
    this.stateService.updateFontSize(1);
    this.syncSettingsToBackend();
  }

  decreaseFontSize(): void {
    this.stateService.updateFontSize(-1);
    this.syncSettingsToBackend();
  }

  increaseLineHeight(): void {
    this.stateService.updateLineHeight(0.1);
    this.syncSettingsToBackend();
  }

  decreaseLineHeight(): void {
    this.stateService.updateLineHeight(-0.1);
    this.syncSettingsToBackend();
  }

  onOpenFullSettings(): void {
    this.close.emit();
    this.openFullSettings.emit();
  }

  onOverlayClick(): void {
    this.close.emit();
  }
}
