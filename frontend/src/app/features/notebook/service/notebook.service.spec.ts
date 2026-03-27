import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {API_CONFIG} from '../../../core/config/api-config';
import {NotebookService} from './notebook.service';

describe('NotebookService', () => {
  let service: NotebookService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        NotebookService,
      ],
    });

    service = TestBed.inject(NotebookService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('loads notebook entries with repeated types, book filter, and trimmed search', () => {
    service.getNotebookEntries(2, 25, ['HIGHLIGHT', 'NOTE'], 42, '  query  ', 'createdAt,desc')
      .subscribe(entries => expect(entries).toEqual([]));

    const request = httpTestingController.expectOne(req => req.urlWithParams.startsWith(`${API_CONFIG.BASE_URL}/api/v1/notebook?`));
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('25');
    expect(request.request.params.getAll('types')).toEqual(['HIGHLIGHT', 'NOTE']);
    expect(request.request.params.get('bookId')).toBe('42');
    expect(request.request.params.get('search')).toBe('query');
    expect(request.request.params.get('sort')).toBe('createdAt,desc');
    request.flush([]);
  });

  it('loads export entries without optional filters when they are blank', () => {
    service.getExportEntries(['BOOKMARK'], null, '   ', 'updatedAt,asc')
      .subscribe(entries => expect(entries).toEqual([]));

    const request = httpTestingController.expectOne(req => req.urlWithParams.startsWith(`${API_CONFIG.BASE_URL}/api/v1/notebook/export?`));
    expect(request.request.method).toBe('GET');
    expect(request.request.params.getAll('types')).toEqual(['BOOKMARK']);
    expect(request.request.params.has('bookId')).toBe(false);
    expect(request.request.params.has('search')).toBe(false);
    expect(request.request.params.get('sort')).toBe('updatedAt,asc');
    request.flush([]);
  });

  it('queries notebook books only when a non-empty search term is provided', () => {
    service.getBooksWithAnnotations('  title  ').subscribe(options => expect(options).toEqual([]));

    const request = httpTestingController.expectOne(req => req.urlWithParams.startsWith(`${API_CONFIG.BASE_URL}/api/v1/notebook/books`));
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('search')).toBe('title');
    request.flush([]);
  });
});
