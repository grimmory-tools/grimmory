import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {FileMoveRequest, FileOperationsService} from './file-operations.service';

describe('FileOperationsService', () => {
  let service: FileOperationsService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        FileOperationsService,
      ],
    });

    service = TestBed.inject(FileOperationsService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('posts move requests to the file operations endpoint', () => {
    const requestBody: FileMoveRequest = {
      bookIds: [4, 9],
      moves: [
        {bookId: 4, targetLibraryId: 2, targetLibraryPathId: 10},
        {bookId: 9, targetLibraryId: null, targetLibraryPathId: null},
      ],
    };

    let completed = false;
    service.moveFiles(requestBody).subscribe(() => {
      completed = true;
    });

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/files/move'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(requestBody);
    request.flush(null);

    expect(completed).toBe(true);
  });
});
