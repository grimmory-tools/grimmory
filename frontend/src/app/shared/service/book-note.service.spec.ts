import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {API_CONFIG} from '../../core/config/api-config';
import {BookNoteService} from './book-note.service';

describe('BookNoteService', () => {
  const baseUrl = `${API_CONFIG.BASE_URL}/api/v1/book-notes`;

  let service: BookNoteService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        BookNoteService,
      ]
    });

    service = TestBed.inject(BookNoteService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('loads notes for a book', () => {
    service.getNotesForBook(15).subscribe();

    const request = httpTestingController.expectOne(`${baseUrl}/book/15`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('creates or updates a note via POST', () => {
    const requestBody = {
      bookId: 15,
      title: 'Chapter 1',
      content: 'Important note',
    };

    service.createOrUpdateNote(requestBody).subscribe();

    const request = httpTestingController.expectOne(baseUrl);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(requestBody);
    request.flush({...requestBody, id: 3, userId: 9, createdAt: '2024-01-01', updatedAt: '2024-01-02'});
  });

  it('deletes a note by id', () => {
    service.deleteNote(3).subscribe();

    const request = httpTestingController.expectOne(`${baseUrl}/3`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
});
