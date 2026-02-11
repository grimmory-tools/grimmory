import {Component, EventEmitter, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
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
  imports: [CommonModule, ReaderIconComponent],
  templateUrl: './shortcuts-help.component.html',
  styleUrls: ['./shortcuts-help.component.scss']
})
export class EbookShortcutsHelpComponent {
  @Output() close = new EventEmitter<void>();

  shortcutGroups: ShortcutGroup[] = [
    {
      title: 'Navigation',
      shortcuts: [
        {keys: ['←'], description: 'Previous page', mobileGesture: 'Swipe right'},
        {keys: ['→'], description: 'Next page', mobileGesture: 'Swipe left'},
        {keys: ['Space'], description: 'Next page'},
        {keys: ['Shift', 'Space'], description: 'Previous page'},
        {keys: ['Home'], description: 'First section'},
        {keys: ['End'], description: 'Last section'},
        {keys: ['Page Up'], description: 'Previous page'},
        {keys: ['Page Down'], description: 'Next page'}
      ]
    },
    {
      title: 'Panels',
      shortcuts: [
        {keys: ['T'], description: 'Table of contents'},
        {keys: ['S'], description: 'Search'},
        {keys: ['N'], description: 'Notes'}
      ]
    },
    {
      title: 'Display',
      shortcuts: [
        {keys: ['F'], description: 'Toggle fullscreen'},
        {keys: ['Escape'], description: 'Exit fullscreen / Close dialogs'}
      ]
    },
    {
      title: 'Other',
      shortcuts: [
        {keys: ['?'], description: 'Show this help dialog'}
      ]
    }
  ];

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
