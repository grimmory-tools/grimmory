import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {TranslocoService} from '@jsverse/transloco';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {firstValueFrom} from 'rxjs';
import {MessageService} from 'primeng/api';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import type {AdditionalFile, Book, BookMetadata, DetachBookFileResponse} from '../model/book.model';
import {AdditionalFileType} from '../model/book.model';
import {BOOKS_QUERY_KEY, bookDetailQueryKey} from './book-query-keys';
import {BookFileService} from './book-file.service';
import {FileDownloadService} from '../../../shared/service/file-download.service';

type BuildBookOverrides = Omit<Partial<Book>, 'metadata'> & {
  metadata?: Partial<BookMetadata>;
};

function buildAdditionalFile(id: number, overrides: Partial<AdditionalFile> = {}): AdditionalFile {
  const bookId = overrides.bookId ?? 1;

  return {
    id,
    bookId,
    fileName: `file-${id}.epub`,
    additionalFileType: AdditionalFileType.ALTERNATIVE_FORMAT,
    ...overrides,
  };
}

function buildBook(id: number, overrides: BuildBookOverrides = {}): Book {
  const {metadata, ...bookOverrides} = overrides;

  return {
    id,
    libraryId: 1,
    libraryName: 'Library',
    metadata: {
      bookId: id,
      title: `Book ${id}`,
      ...metadata,
    },
    ...bookOverrides,
  };
}

describe('BookFileService', () => {
  let service: BookFileService;
  let httpTestingController: HttpTestingController;
  let queryClient: QueryClient;
  let messageService: {add: ReturnType<typeof vi.fn>};
  let fileDownloadService: {downloadFile: ReturnType<typeof vi.fn>};
  let translate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    queryClient = new QueryClient();
    vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue();
    messageService = {
      add: vi.fn(),
    };
    fileDownloadService = {
      downloadFile: vi.fn(),
    };
    translate = vi.fn((key: string, params?: Record<string, unknown>) => (
      params ? `${key}:${JSON.stringify(params)}` : key
    ));

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        BookFileService,
        {provide: QueryClient, useValue: queryClient},
        {provide: MessageService, useValue: messageService},
        {provide: FileDownloadService, useValue: fileDownloadService},
        {provide: TranslocoService, useValue: {translate}},
      ],
    });

    service = TestBed.inject(BookFileService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    queryClient.clear();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('requests blob content with the optional bookType query param', async () => {
    const responseBlob = new Blob(['book-content']);
    const requestPromise = firstValueFrom(service.getFileContent(7, 'EPUB'));

    const request = httpTestingController.expectOne(req => req.urlWithParams.endsWith('/api/v1/books/7/content?bookType=EPUB'));
    expect(request.request.method).toBe('GET');
    expect(request.request.urlWithParams).toBe('http://localhost:6060/api/v1/books/7/content?bookType=EPUB');
    expect(request.request.responseType).toBe('blob');

    request.flush(responseBlob);

    await expect(requestPromise).resolves.toEqual(responseBlob);
  });

  it('delegates direct, aggregate, and additional file downloads with derived filenames', () => {
    const book = buildBook(5, {
      primaryFile: buildAdditionalFile(51, {bookId: 5, fileName: 'main.epub'}),
      metadata: {title: 'Title: Volume 1'},
      alternativeFormats: [
        buildAdditionalFile(52, {bookId: 5, fileName: 'alt.epub'}),
      ],
      supplementaryFiles: [
        buildAdditionalFile(53, {
          bookId: 5,
          fileName: 'notes.pdf',
          additionalFileType: AdditionalFileType.SUPPLEMENTARY,
        }),
      ],
    });

    service.downloadFile(book);
    service.downloadAllFiles(book);
    service.downloadAdditionalFile(book, 53);

    expect(fileDownloadService.downloadFile).toHaveBeenNthCalledWith(
      1,
      'http://localhost:6060/api/v1/books/5/download',
      'main.epub',
    );
    expect(fileDownloadService.downloadFile).toHaveBeenNthCalledWith(
      2,
      'http://localhost:6060/api/v1/books/5/download-all',
      'Title__Volume_1.zip',
    );
    expect(fileDownloadService.downloadFile).toHaveBeenNthCalledWith(
      3,
      'http://localhost:6060/api/v1/books/5/files/53/download',
      'notes.pdf',
    );
  });

  it('deletes an additional file, patches the cached lists, and shows a success toast', () => {
    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [
      buildBook(7, {
        alternativeFormats: [buildAdditionalFile(70, {bookId: 7, fileName: 'alt.epub'})],
        supplementaryFiles: [
          buildAdditionalFile(71, {
            bookId: 7,
            fileName: 'notes.pdf',
            additionalFileType: AdditionalFileType.SUPPLEMENTARY,
          }),
        ],
      }),
    ]);

    service.deleteAdditionalFile(7, 71).subscribe();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books/7/files/71'));
    expect(request.request.method).toBe('DELETE');
    request.flush(null);

    expect(queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual([
      buildBook(7, {
        alternativeFormats: [buildAdditionalFile(70, {bookId: 7, fileName: 'alt.epub'})],
        supplementaryFiles: [],
      }),
    ]);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'book.bookService.toast.fileDeletedSummary',
      detail: 'book.bookService.toast.additionalFileDeletedDetail',
    });
  });

  it('promotes the next alternative format when deleting the primary book file', () => {
    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [
      buildBook(9, {
        primaryFile: buildAdditionalFile(90, {bookId: 9, fileName: 'main.epub'}),
        alternativeFormats: [
          buildAdditionalFile(91, {bookId: 9, fileName: 'alt-one.pdf', bookType: 'PDF'}),
          buildAdditionalFile(92, {bookId: 9, fileName: 'alt-two.fb2', bookType: 'FB2'}),
        ],
      }),
    ]);

    service.deleteBookFile(9, 90, true).subscribe();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books/9/files/90'));
    expect(request.request.method).toBe('DELETE');
    request.flush(null);

    expect(queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual([
      buildBook(9, {
        primaryFile: buildAdditionalFile(91, {bookId: 9, fileName: 'alt-one.pdf', bookType: 'PDF'}),
        alternativeFormats: [
          buildAdditionalFile(92, {bookId: 9, fileName: 'alt-two.fb2', bookType: 'FB2'}),
        ],
      }),
    ]);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'book.bookService.toast.fileDeletedSummary',
      detail: 'book.bookService.toast.bookFileDeletedDetail',
    });
  });

  it('surfaces delete failures through an error toast and preserves cached files', () => {
    const cachedBook = buildBook(11, {
      supplementaryFiles: [
        buildAdditionalFile(111, {
          bookId: 11,
          fileName: 'appendix.pdf',
          additionalFileType: AdditionalFileType.SUPPLEMENTARY,
        }),
      ],
    });
    let thrown: unknown;

    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [cachedBook]);

    service.deleteAdditionalFile(11, 111).subscribe({
      error: error => {
        thrown = error;
      },
    });

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books/11/files/111'));
    request.flush({message: 'cannot delete'}, {status: 500, statusText: 'Server Error'});

    expect(thrown).toBeTruthy();
    expect(queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual([cachedBook]);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'book.bookService.toast.fileDeleteFailedSummary',
      detail: 'cannot delete',
    });
  });

  it('uploads alternative-format files with inferred bookType and patches cached formats', () => {
    const upload = new File(['epub'], 'edition.epub', {type: 'application/epub+zip'});
    const uploadedFile = buildAdditionalFile(121, {
      bookId: 12,
      fileName: 'edition.epub',
      bookType: 'EPUB',
      additionalFileType: AdditionalFileType.ALTERNATIVE_FORMAT,
    });

    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [buildBook(12)]);

    service.uploadAdditionalFile(12, upload, AdditionalFileType.ALTERNATIVE_FORMAT).subscribe(result => {
      expect(result).toEqual(uploadedFile);
    });

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books/12/files'));
    expect(request.request.method).toBe('POST');

    const formData = request.request.body as FormData;
    expect(formData.get('file')).toBe(upload);
    expect(formData.get('isBook')).toBe('true');
    expect(formData.get('bookType')).toBe('EPUB');

    request.flush(uploadedFile);

    expect(queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual([
      buildBook(12, {
        alternativeFormats: [uploadedFile],
      }),
    ]);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'book.bookService.toast.fileUploadedSummary',
      detail: 'book.bookService.toast.fileUploadedDetail',
    });
  });

  it('detaches a book file with copyMetadata and patches both returned books in cache', () => {
    const sourceBook = buildBook(20, {
      alternativeFormats: [buildAdditionalFile(201, {bookId: 20, fileName: 'source.epub'})],
    });
    const placeholderNewBook = buildBook(21, {
      metadata: {title: 'Placeholder'},
    });
    const response: DetachBookFileResponse = {
      sourceBook: buildBook(20, {alternativeFormats: []}),
      newBook: buildBook(21, {
        primaryFile: buildAdditionalFile(201, {bookId: 21, fileName: 'source.epub'}),
        metadata: {title: 'Detached Copy'},
      }),
    };

    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [sourceBook, placeholderNewBook]);

    service.detachBookFile(20, 201, true).subscribe(result => {
      expect(result).toEqual(response);
    });

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books/20/files/201/detach'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({copyMetadata: true});
    request.flush(response);

    expect(queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual([response.sourceBook, response.newBook]);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'metadata.viewer.toast.detachFileSuccessSummary',
      detail: 'metadata.viewer.toast.detachFileSuccessDetail',
    });
  });

  it('attaches source book files, updates the target cache entry, and evicts removed books', () => {
    const targetBook = buildBook(30, {
      primaryFile: buildAdditionalFile(301, {bookId: 30, fileName: 'target.epub'}),
    });
    const sourceBook = buildBook(31, {
      primaryFile: buildAdditionalFile(311, {bookId: 31, fileName: 'source-one.epub'}),
    });
    const secondSourceBook = buildBook(32, {
      primaryFile: buildAdditionalFile(321, {bookId: 32, fileName: 'source-two.epub'}),
    });
    const updatedTargetBook = buildBook(30, {
      primaryFile: buildAdditionalFile(301, {bookId: 30, fileName: 'target.epub'}),
      alternativeFormats: [
        buildAdditionalFile(311, {bookId: 30, fileName: 'source-one.epub'}),
        buildAdditionalFile(321, {bookId: 30, fileName: 'source-two.epub'}),
      ],
    });

    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [targetBook, sourceBook, secondSourceBook]);
    queryClient.setQueryData(bookDetailQueryKey(31, false), sourceBook);
    queryClient.setQueryData(bookDetailQueryKey(32, false), secondSourceBook);

    service.attachBookFiles(30, [31, 32], false).subscribe(result => {
      expect(result).toEqual({
        updatedBook: updatedTargetBook,
        deletedSourceBookIds: [31, 32],
      });
    });

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books/30/attach-file'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      sourceBookIds: [31, 32],
      moveFiles: false,
    });
    request.flush({
      updatedBook: updatedTargetBook,
      deletedSourceBookIds: [31, 32],
    });

    expect(queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual([updatedTargetBook]);
    expect(queryClient.getQueryData(bookDetailQueryKey(31, false))).toBeUndefined();
    expect(queryClient.getQueryData(bookDetailQueryKey(32, false))).toBeUndefined();
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'book.bookService.toast.filesAttachedSummary',
      detail: 'book.bookService.toast.filesAttachedDetail:{"count":2}',
    });
  });
});
