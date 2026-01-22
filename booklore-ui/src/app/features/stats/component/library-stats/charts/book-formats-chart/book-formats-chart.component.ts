import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, switchMap, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {LibraryFilterService} from '../../service/library-filter.service';
import {BookService} from '../../../../../book/service/book.service';
import {BookState} from '../../../../../book/model/state/book-state.model';
import {Book} from '../../../../../book/model/book.model';

interface FormatStats {
  format: string;
  count: number;
  percentage: number;
}

type FormatChartData = ChartData<'pie', number[], string>;

const FORMAT_COLORS: Record<string, string> = {
  'PDF': '#E11D48',    // Rose
  'EPUB': '#0D9488',   // Teal
  'CBX': '#7C3AED',    // Violet
  'FB2': '#F59E0B',    // Amber
  'MOBI': '#2563EB',   // Blue
  'AZW3': '#16A34A'    // Green
};

@Component({
  selector: 'app-book-formats-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './book-formats-chart.component.html',
  styleUrls: ['./book-formats-chart.component.scss']
})
export class BookFormatsChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'pie' as const;
  public formatStats: FormatStats[] = [];
  public totalBooks = 0;

  public readonly chartOptions: ChartConfiguration<'pie'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    layout: {
      padding: {top: 10, bottom: 10}
    },
    plugins: {
      legend: {
        display: true,
        position: 'right',
        labels: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 12
          },
          usePointStyle: true,
          pointStyle: 'circle',
          padding: 15
        }
      },
      tooltip: {
        enabled: true,
        backgroundColor: 'rgba(0, 0, 0, 0.95)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: '#E11D48',
        borderWidth: 2,
        cornerRadius: 8,
        padding: 12,
        titleFont: {size: 14, weight: 'bold'},
        bodyFont: {size: 12},
        callbacks: {
          label: (context) => {
            const value = context.parsed;
            const total = context.dataset.data.reduce((a: number, b: number) => a + b, 0);
            const percentage = ((value / total) * 100).toFixed(1);
            return `${context.label}: ${value} books (${percentage}%)`;
          }
        }
      },
      datalabels: {
        display: false
      }
    }
  };

  private readonly chartDataSubject = new BehaviorSubject<FormatChartData>({
    labels: [],
    datasets: []
  });

  public readonly chartData$: Observable<FormatChartData> = this.chartDataSubject.asObservable();

  ngOnInit(): void {
    this.bookService.bookState$
      .pipe(
        filter(state => state.loaded),
        first(),
        switchMap(() =>
          this.libraryFilterService.selectedLibrary$.pipe(
            takeUntil(this.destroy$)
          )
        ),
        catchError((error) => {
          console.error('Error processing book formats data:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        this.calculateAndUpdateChart();
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private calculateAndUpdateChart(): void {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      this.chartDataSubject.next({labels: [], datasets: []});
      this.formatStats = [];
      this.totalBooks = 0;
      return;
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    this.totalBooks = filteredBooks.length;

    if (filteredBooks.length === 0) {
      this.chartDataSubject.next({labels: [], datasets: []});
      this.formatStats = [];
      return;
    }

    const stats = this.calculateFormatStats(filteredBooks);
    this.formatStats = stats;
    this.updateChartData(stats);
  }

  private isValidBookState(state: unknown): state is BookState {
    return (
      typeof state === 'object' &&
      state !== null &&
      'loaded' in state &&
      typeof (state as { loaded: boolean }).loaded === 'boolean' &&
      'books' in state &&
      Array.isArray((state as { books: unknown }).books) &&
      (state as { books: Book[] }).books.length > 0
    );
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: number | null): Book[] {
    return selectedLibraryId
      ? books.filter(book => book.libraryId === selectedLibraryId)
      : books;
  }

  private calculateFormatStats(books: Book[]): FormatStats[] {
    const formatCounts = new Map<string, number>();

    books.forEach(book => {
      const format = book.bookType || 'Unknown';
      formatCounts.set(format, (formatCounts.get(format) || 0) + 1);
    });

    const total = books.length;
    return Array.from(formatCounts.entries())
      .map(([format, count]) => ({
        format,
        count,
        percentage: (count / total) * 100
      }))
      .sort((a, b) => b.count - a.count);
  }

  private updateChartData(stats: FormatStats[]): void {
    const labels = stats.map(s => s.format);
    const data = stats.map(s => s.count);
    const colors = stats.map(s => FORMAT_COLORS[s.format] || '#6B7280');

    this.chartDataSubject.next({
      labels,
      datasets: [{
        data,
        backgroundColor: colors,
        borderColor: colors.map(() => 'rgba(255, 255, 255, 0.2)'),
        borderWidth: 2,
        hoverBorderColor: '#ffffff',
        hoverBorderWidth: 3
      }]
    });
  }
}
