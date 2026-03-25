import {Component, EventEmitter, inject, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {ReaderIconComponent} from '../shared/icon.component';

interface ShortcutItem {
  keys: string[];
  description: string;
  mobileGesture?: string;
}

interface ShortcutGroup {
  title: string;
  shortcuts: ShortcutItem[];
}

@Component({
  selector: 'app-ebook-shortcuts-help',
  standalone: true,
  imports: [CommonModule, TranslocoPipe, ReaderIconComponent],
  templateUrl: './shortcuts-help.component.html',
  styleUrls: ['./shortcuts-help.component.scss']
})
export class EbookShortcutsHelpComponent {
  @Output() close = new EventEmitter<void>();

  private readonly t = inject(TranslocoService);

  get shortcutGroups(): ShortcutGroup[] {
    return [
      {
        title: this.t.translate('readerEbook.shortcutsHelp.navigation'),
        shortcuts: [
          {keys: ['\u2190'], description: this.t.translate('readerEbook.shortcutsHelp.previousPage'), mobileGesture: this.t.translate('readerEbook.shortcutsHelp.swipeRight')},
          {keys: ['\u2192'], description: this.t.translate('readerEbook.shortcutsHelp.nextPage'), mobileGesture: this.t.translate('readerEbook.shortcutsHelp.swipeLeft')},
          {keys: ['Space'], description: this.t.translate('readerEbook.shortcutsHelp.nextPage')},
          {keys: ['Shift', 'Space'], description: this.t.translate('readerEbook.shortcutsHelp.previousPage')},
          {keys: ['Home'], description: this.t.translate('readerEbook.shortcutsHelp.firstSection')},
          {keys: ['End'], description: this.t.translate('readerEbook.shortcutsHelp.lastSection')},
          {keys: ['Page Up'], description: this.t.translate('readerEbook.shortcutsHelp.previousPage')},
          {keys: ['Page Down'], description: this.t.translate('readerEbook.shortcutsHelp.nextPage')}
        ]
      },
      {
        title: this.t.translate('readerEbook.shortcutsHelp.panels'),
        shortcuts: [
          {keys: ['T'], description: this.t.translate('readerEbook.shortcutsHelp.tableOfContents')},
          {keys: ['S'], description: this.t.translate('readerEbook.shortcutsHelp.searchShortcut')},
          {keys: ['N'], description: this.t.translate('readerEbook.shortcutsHelp.notesShortcut')}
        ]
      },
      {
        title: this.t.translate('readerEbook.shortcutsHelp.display'),
        shortcuts: [
          {keys: ['F'], description: this.t.translate('readerEbook.shortcutsHelp.toggleFullscreen')},
          {keys: ['Escape'], description: this.t.translate('readerEbook.shortcutsHelp.exitFullscreenCloseDialogs')}
        ]
      },
      {
        title: this.t.translate('readerEbook.shortcutsHelp.other'),
        shortcuts: [
          {keys: ['?'], description: this.t.translate('readerEbook.shortcutsHelp.showHelpDialog')}
        ]
      }
    ];
  }

  isMobile = window.innerWidth < 768;

  onClose(): void {
    this.close.emit();
  }

  onOverlayClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('dialog-overlay')) {
      this.onClose();
    }
  }
}
