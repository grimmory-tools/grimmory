import {inject, Injectable, signal} from '@angular/core';
import {Subject} from 'rxjs';
import {switchMap, takeUntil, tap} from 'rxjs/operators';
import {of} from 'rxjs';
import {Annotation} from '../../../../../shared/service/annotation.service';
import {ReaderViewManagerService} from '../../core/view-manager.service';
import {ReaderAnnotationHttpService} from '../annotations/annotation.service';
import {ReaderLeftSidebarService} from '../../layout/panel/panel.service';
import {TextSelectionAction, AnnotationStyle} from '../../shared/selection-popup.component';

export interface SelectionState {
  visible: boolean;
  position: { x: number; y: number };
  showBelow: boolean;
  overlappingAnnotationId: number | null;
  selectedText: string;
  linkUrl?: string;
}

export interface SelectionDetail {
  text: string;
  cfi: string;
  range: Range;
  index: number;
  linkUrl?: string;
}

@Injectable()
export class ReaderSelectionService {
  private viewManager = inject(ReaderViewManagerService);
  private annotationService = inject(ReaderAnnotationHttpService);
  private leftSidebarService = inject(ReaderLeftSidebarService);

  private bookId!: number;
  private annotations: Annotation[] = [];
  private currentSelection: SelectionDetail | null = null;
  private destroy$ = new Subject<void>();

  private _visible = false;
  private _position = { x: 0, y: 0 };
  private _showBelow = false;
  private _overlappingAnnotationId: number | null = null;

  private previewCfi: string | null = null;
  private previewColor: string | null = null;
  private previewStyle: AnnotationStyle | null = null;

  private readonly defaultState: SelectionState = {
    visible: false,
    position: {x: 0, y: 0},
    showBelow: false,
    overlappingAnnotationId: null,
    selectedText: '',
    linkUrl: undefined
  };

  private readonly _state = signal<SelectionState>(this.defaultState);
  readonly state = this._state.asReadonly();

  private annotationsChangedSubject = new Subject<Annotation[]>();
  public annotationsChanged$ = this.annotationsChangedSubject.asObservable();

  initialize(bookId: number, destroy$: Subject<void>): void {
    this.bookId = bookId;
    this.destroy$ = destroy$;
  }

  setAnnotations(annotations: Annotation[]): void {
    this.annotations = annotations;
  }

  handleTextSelected(detail: SelectionDetail, popupPosition?: { x: number; y: number; showBelow?: boolean }): void {
    this.currentSelection = detail;
    this._overlappingAnnotationId = this.findOverlappingAnnotation(detail.cfi);
    this._visible = true;
    this._position = popupPosition || { x: 0, y: 0 };
    this._showBelow = popupPosition?.showBelow || false;

    this.emitState();
  }

  handleAction(action: TextSelectionAction): void {
    if (action.type === 'preview' && this.currentSelection) {
      this.updatePreview(
        this.currentSelection.cfi,
        action.color || '#FFFF00',
        action.style || 'highlight'
      );
      return;
    }

    this._visible = false;
    this._overlappingAnnotationId = null;

    if (action.type === 'dismiss') {
      this.clearPreview();
      this.viewManager.clearSelection();
      this.currentSelection = null;
      this.emitState();
      return;
    }

    if (action.type === 'select') {
      this.clearPreview();
      if (this.currentSelection?.text) {
        navigator.clipboard.writeText(this.currentSelection.text);
      }
      this.viewManager.clearSelection();
      this.emitState();
    } else if (action.type === 'search' && action.searchText) {
      this.clearPreview();
      this.viewManager.clearSelection();
      this.leftSidebarService.openWithSearch(action.searchText);
      this.emitState();
    } else if (action.type === 'delete' && action.annotationId) {
      this.clearPreview();
      this.deleteAnnotation(action.annotationId);
      this.viewManager.clearSelection();
    } else if (action.type === 'annotate' && this.currentSelection) {
      this.clearPreview();
      this.createAnnotation(
        this.currentSelection.text,
        this.currentSelection.cfi,
        action.color || '#FFFF00',
        action.style || 'highlight'
      );
    } else if (action.type === 'go-to-link' && this.currentSelection?.linkUrl) {
      // Handled by component, but we clear state here
      this.clearPreview();
      this.viewManager.clearSelection();
    }

    this.currentSelection = null;
    this.emitState();
  }

  private updatePreview(cfi: string, color: string, style: AnnotationStyle): void {
    if (this.previewCfi) {
      this.viewManager.deleteAnnotation(this.previewCfi)
        .pipe(takeUntil(this.destroy$))
        .subscribe();
    }

    this.previewCfi = cfi;
    this.previewColor = color;
    this.previewStyle = style;

    this.viewManager.addAnnotation({
      value: cfi,
      color: color,
      style: style
    }).pipe(takeUntil(this.destroy$)).subscribe();
  }

  private clearPreview(): void {
    if (this.previewCfi) {
      this.viewManager.deleteAnnotation(this.previewCfi)
        .pipe(takeUntil(this.destroy$))
        .subscribe();
      this.previewCfi = null;
      this.previewColor = null;
      this.previewStyle = null;
    }
  }

  private createAnnotation(text: string, cfi: string, color: string, style: AnnotationStyle): void {
    this.annotationService.createAnnotation(
      this.bookId,
      cfi,
      text,
      color,
      style
    ).pipe(
      switchMap(savedAnnotation => {
        if (!savedAnnotation) return of(null);

        this.annotations = [...this.annotations, savedAnnotation];
        this.annotationsChangedSubject.next(this.annotations);

        return this.viewManager.addAnnotation({
          value: savedAnnotation.cfi,
          color: savedAnnotation.color,
          style: savedAnnotation.style
        }).pipe(
          tap(() => this.viewManager.clearSelection())
        );
      }),
      takeUntil(this.destroy$)
    ).subscribe();
  }

  deleteAnnotation(annotationId: number): void {
    const annotation = this.annotations.find(a => a.id === annotationId);
    if (!annotation) return;

    this.annotationService.deleteAnnotation(annotationId)
      .pipe(takeUntil(this.destroy$))
      .subscribe(success => {
        if (success) {
          this.viewManager.deleteAnnotation(annotation.cfi)
            .pipe(takeUntil(this.destroy$))
            .subscribe();
          this.annotations = this.annotations.filter(a => a.id !== annotationId);
          this.annotationsChangedSubject.next(this.annotations);
        }
      });
  }

  private findOverlappingAnnotation(selectionCfi: string): number | null {
    if (!selectionCfi || !this.annotations.length) {
      return null;
    }

    const selectionRange = this.parseCfiRange(selectionCfi);
    if (!selectionRange) return null;

    for (const annotation of this.annotations) {
      const annotationRange = this.parseCfiRange(annotation.cfi);
      if (!annotationRange) continue;

      // Check if base paths match (same text node/element)
      if (selectionRange.basePath !== annotationRange.basePath) continue;

      // Check if character ranges actually overlap
      // Two ranges [a, b] and [c, d] overlap if a < d AND c < b
      if (selectionRange.startOffset < annotationRange.endOffset &&
          annotationRange.startOffset < selectionRange.endOffset) {
        return annotation.id;
      }
    }

    return null;
  }

  /**
   * Parses a CFI range and extracts the base path and character offsets.
   * CFI range format: epubcfi(parent_path,relative_start,relative_end)
   * Example: epubcfi(/6/4!/4/2/1:0,/4/2/1:15)
   */
  private parseCfiRange(cfi: string): { basePath: string; startOffset: number; endOffset: number } | null {
    // Remove epubcfi() wrapper
    const inner = cfi.replace(/^epubcfi\(/, '').replace(/\)$/, '');

    // Find the comma that separates parent path from relative offsets
    const commaIndex = inner.indexOf(',');
    if (commaIndex <= 0) {
      // Not a range CFI, might be a point CFI - treat as zero-width range
      const offsetMatch = inner.match(/:(\d+)$/);
      if (offsetMatch) {
        const basePath = inner.replace(/:\d+$/, '');
        const offset = parseInt(offsetMatch[1], 10);
        return { basePath, startOffset: offset, endOffset: offset };
      }
      return null;
    }

    // Extract parent path (before first comma)
    const parentPath = inner.substring(0, commaIndex);

    // Extract relative start and end parts (after commas)
    const relativePartsStr = inner.substring(commaIndex + 1);
    const relativeParts = relativePartsStr.split(',');
    if (relativeParts.length !== 2) return null;

    const relativeStart = relativeParts[0];
    const relativeEnd = relativeParts[1];

    // Extract character offsets from relative parts
    const startOffsetMatch = relativeStart.match(/:(\d+)$/);
    const endOffsetMatch = relativeEnd.match(/:(\d+)$/);

    if (!startOffsetMatch || !endOffsetMatch) return null;

    const startOffset = parseInt(startOffsetMatch[1], 10);
    const endOffset = parseInt(endOffsetMatch[1], 10);

    // Build the full base path by combining parent with the common path structure
    // Strip character offsets from the path to get the node path
    const startNodePath = relativeStart.replace(/:\d+$/, '');

    // Use parent + start node path as base (start and end should be in same node for highlights)
    const basePath = `${parentPath}${startNodePath}`;

    return { basePath, startOffset, endOffset };
  }

  private emitState(): void {
    this._state.set({
      visible: this._visible,
      position: this._position,
      showBelow: this._showBelow,
      overlappingAnnotationId: this._overlappingAnnotationId,
      selectedText: this.currentSelection?.text || '',
      linkUrl: this.currentSelection?.linkUrl
    });
  }

  reset(): void {
    this._visible = false;
    this._position = { x: 0, y: 0 };
    this._showBelow = false;
    this._overlappingAnnotationId = null;
    this.currentSelection = null;
    this.annotations = [];
    this.previewCfi = null;
    this.previewColor = null;
    this.previewStyle = null;
    this._state.set(this.defaultState);
  }

  getCurrentSelection(): SelectionDetail | null {
    return this.currentSelection;
  }

  hidePopup(): void {
    this._visible = false;
    this._overlappingAnnotationId = null;
    this.clearPreview();
    this.emitState();
  }
}
