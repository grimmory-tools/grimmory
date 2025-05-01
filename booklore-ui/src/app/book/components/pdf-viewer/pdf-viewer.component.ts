import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {NgxExtendedPdfViewerModule} from 'ngx-extended-pdf-viewer';
import {BookService} from '../../service/book.service';
import {forkJoin, Subscription} from 'rxjs';
import {BookSetting} from '../../model/book.model';
import {UserService} from '../../../settings/user-management/user.service';
import {NgIf} from '@angular/common';
import {ProgressSpinner} from 'primeng/progressspinner';
import {MessageService} from 'primeng/api';

@Component({
  selector: 'app-pdf-viewer',
  standalone: true,
  imports: [NgxExtendedPdfViewerModule, NgIf, ProgressSpinner],
  templateUrl: './pdf-viewer.component.html',
})
export class PdfViewerComponent implements OnInit, OnDestroy {
  isLoading = true;

  handTool = true;
  rotation: 0 | 90 | 180 | 270 = 0;

  page!: number;
  spread!: 'off' | 'even' | 'odd';
  zoom!: number | string;

  bookData!: string | Blob;
  bookId!: number;
  private appSettingsSubscription!: Subscription;

  private bookService = inject(BookService);
  private userService = inject(UserService);
  private messageService = inject(MessageService);
  private route = inject(ActivatedRoute);

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      this.isLoading = true;
      this.bookId = +params.get('bookId')!;

      const myself$ = this.userService.getMyself();
      const book$ = this.bookService.getBookByIdFromAPI(this.bookId, false);
      const bookSetting$ = this.bookService.getBookSetting(this.bookId);
      const pdfData$ = this.bookService.getFileContent(this.bookId);

      forkJoin([book$, bookSetting$, pdfData$, myself$]).subscribe({
        next: (results) => {
          const pdfMeta = results[0];
          const pdfPrefs = results[1];
          const pdfData = results[2];
          const myself = results[3];

          let globalOrIndividual = myself.bookPreferences.perBookSetting.pdf;
          if (globalOrIndividual === 'Global') {
            this.zoom = myself.bookPreferences.pdfReaderSetting.pageZoom || 'page-fit';
            this.spread = myself.bookPreferences.pdfReaderSetting.pageSpread || 'odd';
          } else {
            this.zoom = pdfPrefs.pdfSettings?.zoom || myself.bookPreferences.pdfReaderSetting.pageZoom || 'page-fit';
            this.spread = pdfPrefs.pdfSettings?.spread || myself.bookPreferences.pdfReaderSetting.pageSpread || 'odd';
          }
          this.page = pdfMeta.pdfProgress || 1;
          this.bookData = pdfData;
          this.isLoading = false;
        },
        error: () => {
          this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to load the book'});
          this.isLoading = false;
        }
      });
    });
  }

  onPageChange(page: number): void {
    if (page !== this.page) {
      this.page = page;
      this.updateProgress();
    }
  }

  onZoomChange(zoom: string | number): void {
    if (zoom !== this.zoom) {
      this.zoom = zoom;
      this.updateViewerSetting();
    }
  }

  onSpreadChange(spread: 'off' | 'even' | 'odd'): void {
    if (spread !== this.spread) {
      this.spread = spread;
      this.updateViewerSetting();
    }
  }

  private updateViewerSetting(): void {
    const bookSetting: BookSetting = {
      pdfSettings: {
        spread: this.spread,
        zoom: this.zoom,
      }
    }
    this.bookService.updateViewerSetting(bookSetting, this.bookId).subscribe();
  }

  updateProgress() {
    this.bookService.savePdfProgress(this.bookId, this.page).subscribe();
  }

  ngOnDestroy(): void {
    if (this.appSettingsSubscription) {
      this.appSettingsSubscription.unsubscribe();
    }
    this.updateProgress();
  }
}
