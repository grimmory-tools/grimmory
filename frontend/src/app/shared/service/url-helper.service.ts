import {inject, Injectable} from '@angular/core';
import {API_CONFIG} from '../../core/config/api-config';
import {AuthService} from './auth.service';
import {BookService} from '../../features/book/service/book.service';
import {CoverGeneratorComponent} from '../components/cover-generator/cover-generator.component';
import {Router} from '@angular/router';
import {Book,BookType} from '../../features/book/model/book.model';

@Injectable({
  providedIn: 'root'
})
export class UrlHelperService {
  private readonly baseUrl = API_CONFIG.BASE_URL;
  private readonly mediaBaseUrl = `${this.baseUrl}/api/v1/media`;
  private authService = inject(AuthService);
  private bookService = inject(BookService);
  private router = inject(Router);

  private getToken(): string | null {
    return this.authService.getInternalAccessToken();
  }

  private appendToken(url: string): string {
    const token = this.getToken();
    return token ? `${url}${url.includes('?') ? '&' : '?'}token=${token}` : url;
  }

  getThumbnailUrl(bookId: number, coverUpdatedOn?: string): string {
    if (!coverUpdatedOn) {
      const book = this.bookService.findBookById(bookId);
      if (book && book.metadata) {
        const coverGenerator = new CoverGeneratorComponent();
        coverGenerator.title = book.metadata.title || '';
        coverGenerator.author = (book.metadata.authors || []).join(', ');
        return coverGenerator.generateCover();
      }
    }
    let url = `${this.mediaBaseUrl}/book/${bookId}/thumbnail`;
    if (coverUpdatedOn) {
      url += `?${coverUpdatedOn}`;
    }
    return this.appendToken(url);
  }

  getDirectThumbnailUrl(bookId: number, coverUpdatedOn?: string): string {
    let url = `${this.mediaBaseUrl}/book/${bookId}/thumbnail`;
    if (coverUpdatedOn) {
      url += `?${coverUpdatedOn}`;
    }
    return this.appendToken(url);
  }

  getCoverUrl(bookId: number, coverUpdatedOn?: string): string {
    if (!coverUpdatedOn) {
      const book = this.bookService.findBookById(bookId);
      if (book && book.metadata) {
        const coverGenerator = new CoverGeneratorComponent();
        coverGenerator.title = book.metadata.title || '';
        coverGenerator.author = (book.metadata.authors || []).join(', ');
        return coverGenerator.generateCover();
      }
    }
    let url = `${this.mediaBaseUrl}/book/${bookId}/cover`;
    if (coverUpdatedOn) {
      url += `?${coverUpdatedOn}`;
    }
    return this.appendToken(url);
  }

  getBackupCoverUrl(bookId: number): string {
    const url = `${this.mediaBaseUrl}/book/${bookId}/backup-cover`;
    return this.appendToken(url);
  }

  getAudiobookCoverUrl(bookId: number, audiobookCoverUpdatedOn?: string): string {
    if (!audiobookCoverUpdatedOn) {
      const book = this.bookService.findBookById(bookId);
      if (book && book.metadata) {
        const coverGenerator = new CoverGeneratorComponent();
        coverGenerator.title = book.metadata.title || '';
        coverGenerator.author = (book.metadata.authors || []).join(', ');
        coverGenerator.isSquare = true;
        return coverGenerator.generateCover();
      }
    }
    let url = `${this.mediaBaseUrl}/book/${bookId}/audiobook-cover`;
    if (audiobookCoverUpdatedOn) {
      url += `?${audiobookCoverUpdatedOn}`;
    }
    return this.appendToken(url);
  }

  getAudiobookThumbnailUrl(bookId: number, audiobookCoverUpdatedOn?: string): string {
    if (!audiobookCoverUpdatedOn) {
      const book = this.bookService.findBookById(bookId);
      if (book && book.metadata) {
        const coverGenerator = new CoverGeneratorComponent();
        coverGenerator.title = book.metadata.title || '';
        coverGenerator.author = (book.metadata.authors || []).join(', ');
        coverGenerator.isSquare = true;
        return coverGenerator.generateCover();
      }
    }
    let url = `${this.mediaBaseUrl}/book/${bookId}/audiobook-thumbnail`;
    if (audiobookCoverUpdatedOn) {
      url += `?${audiobookCoverUpdatedOn}`;
    }
    return this.appendToken(url);
  }

  getBookdropCoverUrl(bookdropId: number): string {
    const url = `${this.mediaBaseUrl}/bookdrop/${bookdropId}/cover`;
    return this.appendToken(url);
  }

  getBookUrl(book: Book) {
    return this.router.createUrlTree(['/book', book.id], {
      queryParams: {tab: 'view'}
    });
  }

  getBookPrimaryReadingUrl(book: Book) {
    const bookType: BookType | undefined = book.primaryFile?.bookType;

    let baseUrl: string | null = null;

    switch (bookType) {
      case 'PDF':
        baseUrl = 'pdf-reader';
        break;

      case 'EPUB':
      case 'FB2':
      case 'MOBI':
      case 'AZW3':
        baseUrl = 'ebook-reader';
        break;

      case 'CBX':
        baseUrl = 'cbx-reader';
        break;

      case 'AUDIOBOOK':
        baseUrl = 'audiobook-player';
        break;
    }

    if (!baseUrl) {
      console.error('Unsupported book type:', bookType);
      //Go to book URL as a fall-back
      return this.getBookUrl(book);
    }

    return this.router.createUrlTree([`/${baseUrl}/book/${book.id}`], undefined);
  }

  filterBooksBy(filterKey: string, filterValue: string) {
    if (filterKey === 'series') {
      return this.router.createUrlTree(['/series', encodeURIComponent(filterValue)])
    }

    return this.router.createUrlTree(['/all-books'], {
      queryParams: {
        view: 'grid',
        sort: 'title',
        direction: 'asc',
        sidebar: true,
        filter: `${filterKey}:${encodeURIComponent(filterValue)}`
      }
    });
  }
}
