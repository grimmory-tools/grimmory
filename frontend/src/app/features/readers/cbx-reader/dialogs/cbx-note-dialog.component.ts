import {Component, EventEmitter, inject, Input, Output, OnChanges, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslocoService, TranslocoPipe} from '@jsverse/transloco';
import {ReaderIconComponent} from '../../ebook-reader/shared/icon.component';

export interface CbxNoteDialogData {
  pageNumber: number;
  noteId?: number;
  noteContent?: string;
  color?: string;
}

export interface CbxNoteDialogResult {
  noteContent: string;
  color: string;
}

@Component({
  selector: 'app-cbx-note-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslocoPipe, ReaderIconComponent],
  templateUrl: './cbx-note-dialog.component.html',
  styleUrls: ['./cbx-note-dialog.component.scss']
})
export class CbxNoteDialogComponent implements OnChanges {
  private readonly t = inject(TranslocoService);

  @Input() data: CbxNoteDialogData | null = null;
  @Output() save = new EventEmitter<CbxNoteDialogResult>();
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

  get noteColors() {
    return [
      {value: '#FFC107', label: this.t.translate('readerCbx.noteDialog.colorAmber')},
      {value: '#4CAF50', label: this.t.translate('readerCbx.noteDialog.colorGreen')},
      {value: '#2196F3', label: this.t.translate('readerCbx.noteDialog.colorBlue')},
      {value: '#E91E63', label: this.t.translate('readerCbx.noteDialog.colorPink')},
      {value: '#9C27B0', label: this.t.translate('readerCbx.noteDialog.colorPurple')},
      {value: '#FF5722', label: this.t.translate('readerCbx.noteDialog.colorDeepOrange')}
    ];
  }

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
