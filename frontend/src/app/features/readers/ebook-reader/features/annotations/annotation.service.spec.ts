import {TestBed} from '@angular/core/testing';
import {firstValueFrom, of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from 'primeng/api';

import {AnnotationService} from '../../../../../shared/service/annotation.service';
import {ReaderAnnotationService} from './annotation-renderer.service';
import {ReaderAnnotationHttpService} from './annotation.service';

describe('ReaderAnnotationHttpService', () => {
  let service: ReaderAnnotationHttpService;
  let annotationService: {
    createAnnotation: ReturnType<typeof vi.fn>;
    getAnnotationsForBook: ReturnType<typeof vi.fn>;
    deleteAnnotation: ReturnType<typeof vi.fn>;
    updateAnnotation: ReturnType<typeof vi.fn>;
  };
  let messageService: {add: ReturnType<typeof vi.fn>};
  let readerAnnotationService: {resetAnnotations: ReturnType<typeof vi.fn>};

  beforeEach(() => {
    annotationService = {
      createAnnotation: vi.fn(),
      getAnnotationsForBook: vi.fn(),
      deleteAnnotation: vi.fn(),
      updateAnnotation: vi.fn(),
    };
    messageService = {add: vi.fn()};
    readerAnnotationService = {resetAnnotations: vi.fn()};

    TestBed.configureTestingModule({
      providers: [
        ReaderAnnotationHttpService,
        {provide: AnnotationService, useValue: annotationService},
        {provide: MessageService, useValue: messageService},
        {provide: ReaderAnnotationService, useValue: readerAnnotationService},
        {provide: TranslocoService, useValue: {translate: (key: string) => key}},
      ],
    });

    service = TestBed.inject(ReaderAnnotationHttpService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('creates annotations with the current chapter title', async () => {
    const createdAnnotation = {
      id: 4,
      bookId: 8,
      cfi: 'epubcfi(/6/2)',
      text: 'Highlighted text',
      color: '#FACC15',
      style: 'highlight' as const,
      createdAt: '2026-03-26T00:00:00Z',
    };
    annotationService.createAnnotation.mockReturnValue(of(createdAnnotation));
    service.updateCurrentChapter('Chapter One');

    const result = await firstValueFrom(service.createAnnotation(8, 'epubcfi(/6/2)', 'Highlighted text'));

    expect(result).toEqual(createdAnnotation);
    expect(annotationService.createAnnotation).toHaveBeenCalledWith(
      expect.objectContaining({
        bookId: 8,
        cfi: 'epubcfi(/6/2)',
        text: 'Highlighted text',
        chapterTitle: 'Chapter One',
      })
    );
    expect(messageService.add).toHaveBeenCalledWith(
      expect.objectContaining({severity: 'success'})
    );
  });

  it('returns null and warns when the backend reports a duplicate annotation', async () => {
    annotationService.createAnnotation.mockReturnValue(
      throwError(() => ({status: 409}))
    );

    const result = await firstValueFrom(service.createAnnotation(3, 'cfi', 'text'));

    expect(result).toBeNull();
    expect(messageService.add).toHaveBeenCalledWith(
      expect.objectContaining({severity: 'warn'})
    );
  });

  it('falls back to an empty annotation list on load errors', async () => {
    annotationService.getAnnotationsForBook.mockReturnValue(
      throwError(() => new Error('load failed'))
    );

    const result = await firstValueFrom(service.getAnnotations(42));

    expect(result).toEqual([]);
  });

  it('returns true and shows a success toast after deleting an annotation', async () => {
    annotationService.deleteAnnotation.mockReturnValue(of(undefined));

    const result = await firstValueFrom(service.deleteAnnotation(11));

    expect(result).toBe(true);
    expect(messageService.add).toHaveBeenCalledWith(
      expect.objectContaining({severity: 'success'})
    );
  });

  it('returns null when note updates fail', async () => {
    annotationService.updateAnnotation.mockReturnValue(
      throwError(() => new Error('update failed'))
    );

    const result = await firstValueFrom(service.updateAnnotationNote(9, 'new note'));

    expect(result).toBeNull();
    expect(messageService.add).toHaveBeenCalledWith(
      expect.objectContaining({severity: 'error'})
    );
  });

  it('clears the tracked chapter when reset is called', async () => {
    const createdAnnotation = {
      id: 5,
      bookId: 12,
      cfi: 'epubcfi(/6/4)',
      text: 'Reset text',
      color: '#FACC15',
      style: 'highlight' as const,
      createdAt: '2026-03-26T00:00:00Z',
    };
    annotationService.createAnnotation.mockReturnValue(of(createdAnnotation));
    service.updateCurrentChapter('Old Chapter');
    service.reset();

    await firstValueFrom(service.createAnnotation(12, 'epubcfi(/6/4)', 'Reset text'));

    expect(readerAnnotationService.resetAnnotations).toHaveBeenCalled();
    expect(annotationService.createAnnotation).toHaveBeenCalledWith(
      expect.not.objectContaining({chapterTitle: 'Old Chapter'})
    );
  });
});
