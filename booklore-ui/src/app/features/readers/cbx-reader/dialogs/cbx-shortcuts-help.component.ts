import {Component, EventEmitter, inject, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslocoService, TranslocoPipe} from '@jsverse/transloco';
import {ReaderIconComponent} from '../../ebook-reader/shared/icon.component';

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
  selector: 'app-cbx-shortcuts-help',
  standalone: true,
  imports: [CommonModule, TranslocoPipe, ReaderIconComponent],
  templateUrl: './cbx-shortcuts-help.component.html',
  styleUrls: ['./cbx-shortcuts-help.component.scss']
})
export class CbxShortcutsHelpComponent {
  private readonly t = inject(TranslocoService);

  @Output() close = new EventEmitter<void>();

  get shortcutGroups(): ShortcutGroup[] {
    return [
      {
        title: this.t.translate('readerCbx.shortcutsHelp.groupNavigation'),
        shortcuts: [
          {keys: ['←', '→'], description: this.t.translate('readerCbx.shortcutsHelp.previousNextPage'), mobileGesture: this.t.translate('readerCbx.shortcutsHelp.swipeLeftRight')},
          {keys: ['Space'], description: this.t.translate('readerCbx.shortcutsHelp.nextPage')},
          {keys: ['Shift', 'Space'], description: this.t.translate('readerCbx.shortcutsHelp.previousPage')},
          {keys: ['Home'], description: this.t.translate('readerCbx.shortcutsHelp.firstPage')},
          {keys: ['End'], description: this.t.translate('readerCbx.shortcutsHelp.lastPage')},
          {keys: ['Page Up'], description: this.t.translate('readerCbx.shortcutsHelp.previousPage')},
          {keys: ['Page Down'], description: this.t.translate('readerCbx.shortcutsHelp.nextPage')}
        ]
      },
      {
        title: this.t.translate('readerCbx.shortcutsHelp.groupDisplay'),
        shortcuts: [
          {keys: ['F'], description: this.t.translate('readerCbx.shortcutsHelp.toggleFullscreen')},
          {keys: ['D'], description: this.t.translate('readerCbx.shortcutsHelp.toggleReadingDirection')},
          {keys: ['Escape'], description: this.t.translate('readerCbx.shortcutsHelp.exitFullscreenCloseDialogs')},
          {keys: ['Double-click'], description: this.t.translate('readerCbx.shortcutsHelp.toggleZoom'), mobileGesture: this.t.translate('readerCbx.shortcutsHelp.doubleTap')}
        ]
      },
      {
        title: this.t.translate('readerCbx.shortcutsHelp.groupPlayback'),
        shortcuts: [
          {keys: ['P'], description: this.t.translate('readerCbx.shortcutsHelp.toggleSlideshow')}
        ]
      },
      {
        title: this.t.translate('readerCbx.shortcutsHelp.groupOther'),
        shortcuts: [
          {keys: ['?'], description: this.t.translate('readerCbx.shortcutsHelp.showHelpDialog')}
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
