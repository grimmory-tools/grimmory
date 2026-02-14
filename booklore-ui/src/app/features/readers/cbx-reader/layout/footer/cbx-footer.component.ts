import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslocoService, TranslocoPipe} from '@jsverse/transloco';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import {Book} from '../../../../book/model/book.model';
import {ReaderIconComponent} from '../../../ebook-reader/shared/icon.component';
import {CbxFooterService, CbxFooterState} from './cbx-footer.service';

@Component({
  selector: 'app-cbx-footer',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslocoPipe, ReaderIconComponent],
  templateUrl: './cbx-footer.component.html',
  styleUrls: ['./cbx-footer.component.scss']
})
export class CbxFooterComponent implements OnInit, OnDestroy {
  private footerService = inject(CbxFooterService);
  private readonly t = inject(TranslocoService);
  private destroy$ = new Subject<void>();

  isVisible = false;
  state: CbxFooterState = {
    currentPage: 0,
    totalPages: 0,
    isTwoPageView: false,
    previousBookInSeries: null,
    nextBookInSeries: null,
    hasSeries: false
  };

  goToPageInput: number | null = null;

  ngOnInit(): void {
    this.footerService.state$
      .pipe(takeUntil(this.destroy$))
      .subscribe(state => this.state = state);

    this.footerService.forceVisible$
      .pipe(takeUntil(this.destroy$))
      .subscribe(visible => this.isVisible = visible);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get displayPage(): number {
    return this.state.currentPage + 1;
  }

  get displaySecondPage(): number | null {
    if (this.state.isTwoPageView && this.state.currentPage + 1 < this.state.totalPages) {
      return this.state.currentPage + 2;
    }
    return null;
  }

  get sliderValue(): number {
    return this.displayPage;
  }

  get canGoPrevious(): boolean {
    return this.state.currentPage > 0;
  }

  get canGoNext(): boolean {
    return this.state.currentPage < this.state.totalPages - 1;
  }

  get sliderTicks(): number[] {
    const totalPages = this.state.totalPages;
    if (totalPages <= 10) {
      return Array.from({length: totalPages}, (_, i) => i + 1);
    } else if (totalPages <= 20) {
      return Array.from({length: totalPages}, (_, i) => i + 1);
    } else if (totalPages <= 50) {
      const ticks: number[] = [];
      for (let i = 1; i <= totalPages; i += 2) {
        ticks.push(i);
      }
      if (!ticks.includes(totalPages)) {
        ticks.push(totalPages);
      }
      return ticks;
    } else if (totalPages <= 100) {
      const ticks: number[] = [];
      const step = 5;
      for (let i = 1; i <= totalPages; i += step) {
        ticks.push(i);
      }
      if (!ticks.includes(totalPages)) {
        ticks.push(totalPages);
      }
      return ticks;
    } else {
      const ticks: number[] = [];
      const step = Math.ceil(totalPages / 20);
      for (let i = 1; i <= totalPages; i += step) {
        ticks.push(i);
      }
      if (!ticks.includes(totalPages)) {
        ticks.push(totalPages);
      }
      return ticks;
    }
  }

  onPreviousPage(): void {
    this.footerService.emitPreviousPage();
  }

  onNextPage(): void {
    this.footerService.emitNextPage();
  }

  onFirstPage(): void {
    this.footerService.emitFirstPage();
  }

  onLastPage(): void {
    this.footerService.emitLastPage();
  }

  onGoToPage(): void {
    if (this.goToPageInput !== null && this.goToPageInput >= 1 && this.goToPageInput <= this.state.totalPages) {
      this.footerService.emitGoToPage(this.goToPageInput);
      this.goToPageInput = null;
    }
  }

  onSliderChange(event: Event): void {
    const target = event.target as HTMLInputElement;
    const page = parseInt(target.value, 10);
    this.footerService.emitSliderChange(page);
  }

  onPreviousBook(): void {
    this.footerService.emitPreviousBook();
  }

  onNextBook(): void {
    this.footerService.emitNextBook();
  }

  getPreviousBookTooltip(): string {
    if (!this.state.previousBookInSeries) return this.t.translate('readerCbx.footer.noPreviousBook');
    return this.t.translate('readerCbx.footer.previousBookTooltip', { title: this.getBookDisplayTitle(this.state.previousBookInSeries) });
  }

  getNextBookTooltip(): string {
    if (!this.state.nextBookInSeries) return this.t.translate('readerCbx.footer.noNextBook');
    return this.t.translate('readerCbx.footer.nextBookTooltip', { title: this.getBookDisplayTitle(this.state.nextBookInSeries) });
  }

  private getBookDisplayTitle(book: Book): string {
    const parts: string[] = [];
    if (book.metadata?.seriesNumber) {
      parts.push(`#${book.metadata.seriesNumber}`);
    }
    const title = book.metadata?.title || book.fileName;
    if (title) {
      parts.push(title);
    }
    return parts.join(' - ');
  }
}
