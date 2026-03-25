import {inject, Injectable} from '@angular/core';
import {Observable, of} from 'rxjs';
import {catchError, map, tap} from 'rxjs/operators';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {
  Annotation,
  AnnotationService,
  AnnotationStyle,
  CreateAnnotationRequest
} from '../../../../../shared/service/annotation.service';
import {Annotation as ViewAnnotation, ReaderAnnotationService} from './annotation-renderer.service';

@Injectable()
export class ReaderAnnotationHttpService {
  private annotationService = inject(AnnotationService);
  private messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);
  private readerAnnotationService = inject(ReaderAnnotationService);

  private currentChapterTitle: string | null = null;

  updateCurrentChapter(chapterTitle: string): void {
    this.currentChapterTitle = chapterTitle;
  }

  createAnnotation(
    bookId: number,
    cfi: string,
    text: string,
    color: string = '#FACC15',
    style: AnnotationStyle = 'highlight',
    note?: string
  ): Observable<Annotation | null> {
    const request: CreateAnnotationRequest = {
      bookId,
      cfi,
      text,
      color,
      style,
      note,
      chapterTitle: this.currentChapterTitle || undefined
    };

    return this.annotationService.createAnnotation(request).pipe(
      tap(() => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('readerEbook.toast.highlightAddedSummary'),
          detail: this.t.translate('readerEbook.toast.highlightAddedDetail')
        });
      }),
      catchError(error => {
        const isDuplicate = error?.status === 409;
        this.messageService.add(
          isDuplicate
            ? {
              severity: 'warn',
              summary: this.t.translate('readerEbook.toast.highlightExistsSummary'),
              detail: this.t.translate('readerEbook.toast.highlightExistsDetail')
            }
            : {
              severity: 'error',
              summary: this.t.translate('readerEbook.toast.highlightFailedSummary'),
              detail: this.t.translate('readerEbook.toast.highlightFailedDetail')
            }
        );
        return of(null);
      })
    );
  }

  getAnnotations(bookId: number): Observable<Annotation[]> {
    return this.annotationService.getAnnotationsForBook(bookId).pipe(
      catchError(() => {
        return of([]);
      })
    );
  }

  deleteAnnotation(annotationId: number): Observable<boolean> {
    return this.annotationService.deleteAnnotation(annotationId).pipe(
      map(() => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('readerEbook.toast.highlightRemovedSummary'),
          detail: this.t.translate('readerEbook.toast.highlightRemovedDetail')
        });
        return true;
      }),
      catchError(() => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('readerEbook.toast.highlightRemoveFailedSummary'),
          detail: this.t.translate('readerEbook.toast.highlightRemoveFailedDetail')
        });
        return of(false);
      })
    );
  }

  updateAnnotationNote(annotationId: number, note: string): Observable<Annotation | null> {
    return this.annotationService.updateAnnotation(annotationId, {note}).pipe(
      tap(() => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('readerEbook.toast.noteAnnotationUpdatedSummary'),
          detail: this.t.translate('readerEbook.toast.noteAnnotationUpdatedDetail')
        });
      }),
      catchError(() => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('readerEbook.toast.noteAnnotationUpdateFailedSummary'),
          detail: this.t.translate('readerEbook.toast.noteAnnotationUpdateFailedDetail')
        });
        return of(null);
      })
    );
  }

  toViewAnnotations(annotations: Annotation[]): ViewAnnotation[] {
    return annotations.map(a => ({
      value: a.cfi,
      color: a.color,
      style: a.style
    }));
  }

  reset(): void {
    this.currentChapterTitle = null;
    this.readerAnnotationService.resetAnnotations();
  }
}
