import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {API_CONFIG} from '../../core/config/api-config';
import {BookNoteV2Service} from './book-note-v2.service';

describe('BookNoteV2Service', () => {
  const baseUrl = `${API_CONFIG.BASE_URL}/api/v2/book-notes`;

  let service: BookNoteV2Service;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        BookNoteV2Service,
      ]
    });

    service = TestBed.inject(BookNoteV2Service);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('loads v2 notes for a book', () => {
    service.getNotesForBook(22).subscribe();

    const request = httpTestingController.expectOne(`${baseUrl}/book/22`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('creates and updates v2 notes', () => {
    const createRequest = {
      bookId: 22,
      cfi: 'epubcfi(/6/2[chapter1])',
      noteContent: 'Remember this passage',
    };

    service.createNote(createRequest).subscribe();
    const createHttpRequest = httpTestingController.expectOne(baseUrl);
    expect(createHttpRequest.request.method).toBe('POST');
    expect(createHttpRequest.request.body).toEqual(createRequest);
    createHttpRequest.flush({...createRequest, id: 4, createdAt: '2024-01-01'});

    const updateRequest = {
      noteContent: 'Updated note',
      color: '#ff0',
    };

    service.updateNote(4, updateRequest).subscribe();
    const updateHttpRequest = httpTestingController.expectOne(`${baseUrl}/4`);
    expect(updateHttpRequest.request.method).toBe('PUT');
    expect(updateHttpRequest.request.body).toEqual(updateRequest);
    updateHttpRequest.flush({...createRequest, ...updateRequest, id: 4, createdAt: '2024-01-01'});
  });

  it('deletes v2 notes by id', () => {
    service.deleteNote(4).subscribe();

    const request = httpTestingController.expectOne(`${baseUrl}/4`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
});
