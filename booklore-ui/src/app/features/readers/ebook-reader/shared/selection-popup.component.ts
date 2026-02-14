import {Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslocoDirective} from '@jsverse/transloco';
import {ReaderIconComponent} from './icon.component';

export type AnnotationStyle = 'highlight' | 'underline' | 'strikethrough' | 'squiggly';

export interface TextSelectionAction {
  type: 'select' | 'annotate' | 'delete' | 'dismiss' | 'preview' | 'search' | 'note';
  color?: string;
  style?: AnnotationStyle;
  annotationId?: number;
  searchText?: string;
}

@Component({
  selector: 'app-text-selection-popup',
  standalone: true,
  imports: [CommonModule, TranslocoDirective, ReaderIconComponent],
  templateUrl: './selection-popup.component.html',
  styleUrls: ['./selection-popup.component.scss']
})
export class TextSelectionPopupComponent {
  @Input() set visible(value: boolean) {
    this._visible = value;
    if (value) {
      this.showAnnotationOptions = false;
      this.hasPreview = false;
    }
  }
  get visible(): boolean {
    return this._visible;
  }
  private _visible = false;

  @Input() position = {x: 0, y: 0};
  @Input() showBelow = false;
  @Input() overlappingAnnotationId: number | null = null;
  @Input() selectedText = '';
  @Output() action = new EventEmitter<TextSelectionAction>();

  showAnnotationOptions = false;
  selectedColor = '#FACC15';
  selectedStyle: AnnotationStyle = 'highlight';
  private hasPreview = false;

  highlightColors = [
    {value: '#FACC15', label: 'Yellow'},
    {value: '#4ADE80', label: 'Green'},
    {value: '#38BDF8', label: 'Blue'},
    {value: '#F472B6', label: 'Pink'},
    {value: '#FB923C', label: 'Orange'}
  ];

  lineColors = [
    {value: '#B8860B', label: 'Dark Gold'},
    {value: '#228B22', label: 'Forest Green'},
    {value: '#1E90FF', label: 'Dodger Blue'},
    {value: '#DC143C', label: 'Crimson'},
    {value: '#FF8C00', label: 'Dark Orange'}
  ];

  get colors() {
    return this.selectedStyle === 'highlight' ? this.highlightColors : this.lineColors;
  }

  styles: { value: AnnotationStyle; label: string; icon: string }[] = [
    {value: 'highlight', label: 'Highlight', icon: 'H'},
    {value: 'underline', label: 'Underline', icon: 'U'},
    {value: 'squiggly', label: 'Squiggly', icon: '~'},
    {value: 'strikethrough', label: 'Strikethrough', icon: 'S'}
  ];

  onSelect(): void {
    this.action.emit({type: 'select'});
    this.showAnnotationOptions = false;
    this.hasPreview = false;
  }

  toggleAnnotationOptions(): void {
    this.showAnnotationOptions = !this.showAnnotationOptions;
    if (this.showAnnotationOptions) {
      this.emitPreview();
    }
  }

  selectColor(color: string): void {
    this.selectedColor = color;
    this.emitPreview();
  }

  selectStyle(style: AnnotationStyle): void {
    const wasHighlight = this.selectedStyle === 'highlight';
    const isHighlight = style === 'highlight';

    this.selectedStyle = style;

    if (wasHighlight !== isHighlight) {
      this.selectedColor = isHighlight ? this.highlightColors[0].value : this.lineColors[0].value;
    }
    this.emitPreview();
  }

  private emitPreview(): void {
    this.hasPreview = true;
    this.action.emit({
      type: 'preview',
      color: this.selectedColor,
      style: this.selectedStyle
    });
  }

  onDelete(): void {
    if (this.overlappingAnnotationId) {
      this.action.emit({type: 'delete', annotationId: this.overlappingAnnotationId});
    }
  }

  onSearch(): void {
    this.action.emit({type: 'search', searchText: this.selectedText});
    this.showAnnotationOptions = false;
    this.hasPreview = false;
  }

  onNote(): void {
    this.action.emit({type: 'note'});
    this.showAnnotationOptions = false;
    this.hasPreview = false;
  }

  onDismiss(event: MouseEvent): void {
    event.stopPropagation();
    event.preventDefault();

    if (this.hasPreview) {
      this.action.emit({
        type: 'annotate',
        color: this.selectedColor,
        style: this.selectedStyle
      });
    } else {
      this.action.emit({type: 'dismiss'});
    }

    this.showAnnotationOptions = false;
    this.hasPreview = false;
  }
}
