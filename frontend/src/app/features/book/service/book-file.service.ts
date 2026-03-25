import {inject, Injectable} from '@angular/core';
import {Observable, throwError} from 'rxjs';
import {HttpClient} from '@angular/common/http';
import {catchError, tap} from 'rxjs/operators';
import {AdditionalFile, AdditionalFileType, Book, DetachBookFileResponse, DuplicateDetectionRequest, DuplicateGroup} from '../model/book.model';
import {API_CONFIG} from '../../../core/config/api-config';
import {MessageService} from 'primeng/api';
import {FileDownloadService} from '../../../shared/service/file-download.service';
import {TranslocoService} from '@jsverse/transloco';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {patchBookInCacheWith, patchBooksInCache, removeBooksFromCache} from './book-query-cache';

@Injectable({
  providedIn: 'root',
})
export class BookFileService {

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/books`;

  private http = inject(HttpClient);
  private messageService = inject(MessageService);
  private fileDownloadService = inject(FileDownloadService);
  private queryClient = inject(QueryClient);
  private readonly t = inject(TranslocoService);

  getFileContent(bookId: number, bookType?: string): Observable<Blob> {
    let url = `${this.url}/${bookId}/content`;
    if (bookType) {
      url += `?bookType=${bookType}`;
    }
    return this.http.get<Blob>(url, {responseType: 'blob' as 'json'});
  }

  downloadFile(book: Book): void {
    const downloadUrl = `${this.url}/${book.id}/download`;
    this.fileDownloadService.downloadFile(downloadUrl, book.primaryFile?.fileName ?? 'book');
  }

  downloadAllFiles(book: Book): void {
    const downloadUrl = `${this.url}/${book.id}/download-all`;
    const filename = book.metadata?.title
      ? `${book.metadata.title.replace(/[^a-zA-Z0-9\-_]/g, '_')}.zip`
      : `book-${book.id}.zip`;
    this.fileDownloadService.downloadFile(downloadUrl, filename);
  }

  deleteAdditionalFile(bookId: number, fileId: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${bookId}/files/${fileId}`).pipe(
      tap(() => {
        patchBookInCacheWith(this.queryClient, bookId, book => ({
          ...book,
          alternativeFormats: book.alternativeFormats?.filter(f => f.id !== fileId),
          supplementaryFiles: book.supplementaryFiles?.filter(f => f.id !== fileId),
        }));
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('book.bookService.toast.fileDeletedSummary'),
          detail: this.t.translate('book.bookService.toast.additionalFileDeletedDetail')
        });
      }),
      catchError(error => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('book.bookService.toast.fileDeleteFailedSummary'),
          detail: error?.error?.message || error?.message || this.t.translate('book.bookService.toast.fileDeleteFailedDetail')
        });
        return throwError(() => error);
      })
    );
  }

  deleteBookFile(bookId: number, fileId: number, isPrimary: boolean): Observable<void> {
    return this.http.delete<void>(`${this.url}/${bookId}/files/${fileId}`).pipe(
      tap(() => {
        patchBookInCacheWith(this.queryClient, bookId, book => {
          if (isPrimary) {
            const remaining = book.alternativeFormats?.filter(f => f.id !== fileId) || [];
            const [newPrimary, ...rest] = remaining;
            return {...book, primaryFile: newPrimary, alternativeFormats: rest};
          }
          return {
            ...book,
            alternativeFormats: book.alternativeFormats?.filter(f => f.id !== fileId),
          };
        });
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('book.bookService.toast.fileDeletedSummary'),
          detail: this.t.translate('book.bookService.toast.bookFileDeletedDetail')
        });
      }),
      catchError(error => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('book.bookService.toast.fileDeleteFailedSummary'),
          detail: error?.error?.message || error?.message || this.t.translate('book.bookService.toast.fileDeleteFailedDetail')
        });
        return throwError(() => error);
      })
    );
  }

  uploadAdditionalFile(bookId: number, file: File, fileType: AdditionalFileType): Observable<AdditionalFile> {
    const formData = new FormData();
    formData.append('file', file);

    const isBook = fileType === AdditionalFileType.ALTERNATIVE_FORMAT;
    formData.append('isBook', String(isBook));

    if (isBook) {
      const lower = (file?.name || '').toLowerCase();
      const ext = lower.includes('.') ? lower.substring(lower.lastIndexOf('.') + 1) : '';
      const EXTENSION_TO_BOOK_TYPE: Record<string, string> = {
        pdf: 'PDF',
        epub: 'EPUB',
        cbz: 'CBX', cbr: 'CBX', cb7: 'CBX',
        mobi: 'MOBI',
        azw3: 'AZW3', azw: 'AZW3',
        fb2: 'FB2',
        m4b: 'AUDIOBOOK', m4a: 'AUDIOBOOK', mp3: 'AUDIOBOOK', opus: 'AUDIOBOOK',
      };
      const bookType = EXTENSION_TO_BOOK_TYPE[ext] ?? null;

      if (bookType) {
        formData.append('bookType', bookType);
      }
    }
    return this.http.post<AdditionalFile>(`${this.url}/${bookId}/files`, formData).pipe(
      tap(newFile => {
        patchBookInCacheWith(this.queryClient, bookId, book => {
          if (fileType === AdditionalFileType.ALTERNATIVE_FORMAT) {
            return {...book, alternativeFormats: [...(book.alternativeFormats || []), newFile]};
          }
          return {...book, supplementaryFiles: [...(book.supplementaryFiles || []), newFile]};
        });
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('book.bookService.toast.fileUploadedSummary'),
          detail: this.t.translate('book.bookService.toast.fileUploadedDetail')
        });
      }),
      catchError(error => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('book.bookService.toast.uploadFailedSummary'),
          detail: error?.error?.message || error?.message || this.t.translate('book.bookService.toast.uploadFailedDetail')
        });
        return throwError(() => error);
      })
    );
  }

  downloadAdditionalFile(book: Book, fileId: number): void {
    const additionalFile = [
      ...(book.alternativeFormats || []),
      ...(book.supplementaryFiles || [])
    ].find((f: AdditionalFile) => f.id === fileId);
    const downloadUrl = `${this.url}/${book.id}/files/${fileId}/download`;
    this.fileDownloadService.downloadFile(downloadUrl, additionalFile?.fileName ?? 'file');
  }

  detachBookFile(bookId: number, fileId: number, copyMetadata: boolean): Observable<DetachBookFileResponse> {
    return this.http.post<DetachBookFileResponse>(`${this.url}/${bookId}/files/${fileId}/detach`, {copyMetadata}).pipe(
      tap(response => {
        patchBooksInCache(this.queryClient, [response.sourceBook, response.newBook]);
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('metadata.viewer.toast.detachFileSuccessSummary'),
          detail: this.t.translate('metadata.viewer.toast.detachFileSuccessDetail')
        });
      }),
      catchError(error => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('metadata.viewer.toast.detachFileErrorSummary'),
          detail: error?.error?.message || error?.message || this.t.translate('metadata.viewer.toast.detachFileErrorDetail')
        });
        return throwError(() => error);
      })
    );
  }

  findDuplicates(request: DuplicateDetectionRequest): Observable<DuplicateGroup[]> {
    return this.http.post<DuplicateGroup[]>(`${this.url}/duplicates`, request);
  }

  attachBookFiles(targetBookId: number, sourceBookIds: number[], moveFiles: boolean): Observable<{updatedBook: Book, deletedSourceBookIds: number[]}> {
    return this.http.post<{updatedBook: Book, deletedSourceBookIds: number[]}>(`${this.url}/${targetBookId}/attach-file`, {
      sourceBookIds,
      moveFiles
    }).pipe(
      tap(response => {
        patchBooksInCache(this.queryClient, [response.updatedBook]);
        removeBooksFromCache(this.queryClient, response.deletedSourceBookIds);
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('book.bookService.toast.filesAttachedSummary'),
          detail: this.t.translate('book.bookService.toast.filesAttachedDetail', {count: sourceBookIds.length})
        });
      }),
      catchError(error => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('book.bookService.toast.attachmentFailedSummary'),
          detail: error?.error?.message || error?.message || this.t.translate('book.bookService.toast.attachmentFailedDetail')
        });
        return throwError(() => error);
      })
    );
  }
}
