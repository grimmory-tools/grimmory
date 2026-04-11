import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {Annotation} from '../../../../../shared/service/annotation.service';
import {ReaderLeftSidebarService} from '../../layout/panel/panel.service';
import {ReaderAnnotationHttpService} from '../annotations/annotation.service';
import {ReaderViewManagerService} from '../../core/view-manager.service';
import {ReaderSelectionService} from './selection.service';

describe('ReaderSelectionService', () => {

  const viewManager = {
    addAnnotation: vi.fn(() => of({index: 1, label: 'Annotation 1'})),
    deleteAnnotation: vi.fn(() => of(void 0)),
    clearSelection: vi.fn(),
  };

  const annotationService = {
    createAnnotation: vi.fn(),
    deleteAnnotation: vi.fn(),
  };

  const leftSidebarService = {
    openWithSearch: vi.fn(),
  };

  let service: ReaderSelectionService;

  beforeEach(() => {
    viewManager.addAnnotation.mockReset();
    viewManager.addAnnotation.mockReturnValue(of({index: 1, label: 'Annotation 1'}));
    viewManager.deleteAnnotation.mockReset();
    viewManager.deleteAnnotation.mockReturnValue(of(void 0));
    viewManager.clearSelection.mockReset();

    annotationService.createAnnotation.mockReset();
    annotationService.deleteAnnotation.mockReset();
    leftSidebarService.openWithSearch.mockReset();

    TestBed.configureTestingModule({
      providers: [
        ReaderSelectionService,
        {provide: ReaderViewManagerService, useValue: viewManager},
        {provide: ReaderAnnotationHttpService, useValue: annotationService},
        {provide: ReaderLeftSidebarService, useValue: leftSidebarService},
      ]
    });

    service = TestBed.inject(ReaderSelectionService);
    service.initialize(41);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('tracks text selection state and identifies overlapping annotations', () => {
    const overlappingAnnotation: Annotation = {
      id: 8,
      bookId: 41,
      cfi: 'epubcfi(/6/2!/4/2,/4/2:4,/4/2:12)',
      text: 'existing highlight',
      color: '#f59e0b',
      style: 'highlight',
      createdAt: '2026-03-26T00:00:00Z',
    };
    service.setAnnotations([overlappingAnnotation]);

    service.handleTextSelected({
      text: 'selected text',
      cfi: 'epubcfi(/6/2!/4/2,/4/2:6,/4/2:10)',
      range: document.createRange(),
      index: 3
    }, {x: 120, y: 80, showBelow: true});

    expect(service.state()).toEqual({
      visible: true,
      position: {x: 120, y: 80, showBelow: true},
      showBelow: true,
      overlappingAnnotationId: 8,
      selectedText: 'selected text'
    });
  });

  it('previews, selects, searches, and dismisses the active selection', () => {
    const clipboardWrite = vi.fn();
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: {writeText: clipboardWrite},
    });

    service.handleTextSelected({
      text: 'searchable snippet',
      cfi: 'epubcfi(/6/2!/4/2:1,/4/2/4:9)',
      range: document.createRange(),
      index: 1
    });

    service.handleAction({type: 'preview', color: '#16a34a', style: 'underline'});
    expect(viewManager.addAnnotation).toHaveBeenCalledWith({
      value: 'epubcfi(/6/2!/4/2:1,/4/2/4:9)',
      color: '#16a34a',
      style: 'underline'
    });

    service.handleAction({type: 'select'});
    expect(clipboardWrite).toHaveBeenCalledWith('searchable snippet');
    expect(viewManager.clearSelection).toHaveBeenCalled();

    service.handleTextSelected({
      text: 'searchable snippet',
      cfi: 'epubcfi(/6/2!/4/2:1,/4/2/4:9)',
      range: document.createRange(),
      index: 1
    });

    service.handleAction({type: 'search', searchText: 'searchable snippet'});
    expect(leftSidebarService.openWithSearch).toHaveBeenCalledWith('searchable snippet');

    service.handleAction({type: 'dismiss'});

    expect(service.state()).toEqual({
      visible: false,
      position: {x: 0, y: 0},
      showBelow: false,
      overlappingAnnotationId: null,
      selectedText: ''
    });
  });

  it('creates annotations, ignores missing deletions, and clears preview state on hide', () => {
    const annotationsChanged: Annotation[][] = [];
    service.annotationsChanged$.subscribe(value => annotationsChanged.push(value));

    annotationService.createAnnotation.mockReturnValue(of({
      id: 99,
      bookId: 41,
      cfi: 'epubcfi(/6/2!/4/2:0,/4/2/4:4)',
      text: 'note text',
      color: '#f97316',
      style: 'highlight',
      createdAt: '2026-03-26T00:00:00Z',
    } satisfies Annotation));

    service.handleTextSelected({
      text: 'note text',
      cfi: 'epubcfi(/6/2!/4/2:0,/4/2/4:4)',
      range: document.createRange(),
      index: 1
    });

    service.handleAction({type: 'annotate', color: '#f97316', style: 'highlight'});

    expect(annotationService.createAnnotation).toHaveBeenCalledWith(
      41,
      'epubcfi(/6/2!/4/2:0,/4/2/4:4)',
      'note text',
      '#f97316',
      'highlight'
    );
    expect(annotationsChanged).toHaveLength(1);
    expect(annotationsChanged[0]).toEqual([
      {
        id: 99,
        bookId: 41,
        cfi: 'epubcfi(/6/2!/4/2:0,/4/2/4:4)',
        text: 'note text',
        color: '#f97316',
        style: 'highlight',
        createdAt: '2026-03-26T00:00:00Z',
      }
    ]);
    expect(viewManager.clearSelection).toHaveBeenCalled();

    annotationService.deleteAnnotation.mockReturnValue(of(false));
    service.deleteAnnotation(99);

    expect(annotationService.deleteAnnotation).toHaveBeenCalledWith(99);
    expect(viewManager.deleteAnnotation).not.toHaveBeenCalled();

    service.handleTextSelected({
      text: 'preview text',
      cfi: 'epubcfi(/6/2!/4/2,/4/2:12,/4/2:20)',
      range: document.createRange(),
      index: 1
    });
    service.handleAction({type: 'preview', color: '#60a5fa', style: 'squiggly'});
    service.hidePopup();

    expect(viewManager.deleteAnnotation).toHaveBeenCalledWith('epubcfi(/6/2!/4/2,/4/2:12,/4/2:20)');
    expect(service.state()).toEqual({
      visible: false,
      position: {x: 0, y: 0},
      showBelow: false,
      overlappingAnnotationId: null,
      selectedText: 'preview text'
    });
  });
});
