import {Component, EventEmitter, Input, Output, OnChanges, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslocoDirective, TranslocoPipe} from '@jsverse/transloco';
import {ReaderIconComponent} from '../shared/icon.component';

export interface NoteDialogData {
  cfi: string;
  selectedText?: string;
  chapterTitle?: string;
  noteId?: number;
  noteContent?: string;
  color?: string;
}

export interface NoteDialogResult {
  noteContent: string;
  color: string;
}

@Component({
  selector: 'app-reader-note-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslocoDirective, TranslocoPipe, ReaderIconComponent],
  templateUrl: './note-dialog.component.html',
  styleUrls: ['./note-dialog.component.scss']
})
export class ReaderNoteDialogComponent implements OnChanges {
  @Input() data: NoteDialogData | null = null;
  @Output() save = new EventEmitter<NoteDialogResult>();
  @Output() cancel = new EventEmitter<void>();

  noteContent = '';
  selectedColor = '#FFC107';

  get isEditing(): boolean {
    return !!this.data?.noteId;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['data'] && this.data) {
      this.noteContent = this.data.noteContent || '';
      this.selectedColor = this.data.color || '#FFC107';
    }
  }

  noteColors = [
    {value: '#FFC107', label: 'Amber'},
    {value: '#4CAF50', label: 'Green'},
    {value: '#2196F3', label: 'Blue'},
    {value: '#E91E63', label: 'Pink'},
    {value: '#9C27B0', label: 'Purple'},
    {value: '#FF5722', label: 'Deep Orange'}
  ];

  onSave(): void {
    if (this.noteContent.trim()) {
      this.save.emit({
        noteContent: this.noteContent.trim(),
        color: this.selectedColor
      });
    }
  }

  onCancel(): void {
    this.cancel.emit();
  }

  selectColor(color: string): void {
    this.selectedColor = color;
  }

  onOverlayClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('dialog-overlay')) {
      this.onCancel();
    }
  }
}
