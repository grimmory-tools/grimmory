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

interface PageRange {
  label: string;
  min: number;
  max: number;
  color: string;
}

interface PageStats {
  range: string;
  count: number;
  color: string;
}

type PageChartData = ChartData<'bar', number[], string>;

const PAGE_RANGES: PageRange[] = [
  {label: '0-100', min: 0, max: 100, color: '#06B6D4'},
  {label: '101-200', min: 101, max: 200, color: '#0EA5E9'},
  {label: '201-300', min: 201, max: 300, color: '#3B82F6'},
  {label: '301-500', min: 301, max: 500, color: '#6366F1'},
  {label: '501-750', min: 501, max: 750, color: '#8B5CF6'},
  {label: '751-1000', min: 751, max: 1000, color: '#A855F7'},
  {label: '1000+', min: 1001, max: Infinity, color: '#D946EF'}
];

@Component({
  selector: 'app-page-count-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './page-count-chart.component.html',
  styleUrls: ['./page-count-chart.component.scss']
})
export class PageCountChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'bar' as const;
  public totalBooks = 0;

  public readonly chartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    layout: {
      padding: {top: 10, bottom: 10}
    },
    plugins: {
      legend: {display: false},
      tooltip: {
        enabled: true,
        backgroundColor: 'rgba(0, 0, 0, 0.95)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: '#8B5CF6',
        borderWidth: 2,
        cornerRadius: 8,
        padding: 12,
        titleFont: {size: 13, weight: 'bold'},
        bodyFont: {size: 11},
        callbacks: {
          title: (context) => `${context[0].label} pages`,
          label: (context) => {
            const value = context.parsed.y;
            return `${value} book${value === 1 ? '' : 's'}`;
          }
        }
      },
      datalabels: {display: false}
    },
    scales: {
      x: {
        title: {
          display: true,
          text: 'Page Count',
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11
          }
        },
        ticks: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 10
          }
        },
        grid: {display: false},
        border: {display: false}
      },
      y: {
        title: {
          display: true,
          text: 'Books',
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11
          }
        },
        beginAtZero: true,
        ticks: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 10
          },
          stepSize: 1,
          maxTicksLimit: 6
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.1)'
        },
        border: {display: false}
      }
    }
  };

  private readonly chartDataSubject = new BehaviorSubject<PageChartData>({
    labels: [],
    datasets: []
  });

  public readonly chartData$: Observable<PageChartData> = this.chartDataSubject.asObservable();

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
          console.error('Error processing page count data:', error);
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
      this.totalBooks = 0;
      return;
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    const booksWithPageCount = filteredBooks.filter(b => b.metadata?.pageCount != null && b.metadata.pageCount > 0);

    this.totalBooks = booksWithPageCount.length;

    if (booksWithPageCount.length === 0) {
      this.chartDataSubject.next({labels: [], datasets: []});
      return;
    }

    const stats = this.calculatePageStats(booksWithPageCount);
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

  private calculatePageStats(books: Book[]): PageStats[] {
    const rangeCounts = new Map<string, { count: number, color: string }>();

    PAGE_RANGES.forEach(range => {
      rangeCounts.set(range.label, {count: 0, color: range.color});
    });

    books.forEach(book => {
      const pageCount = book.metadata!.pageCount!;
      for (const range of PAGE_RANGES) {
        if (pageCount >= range.min && pageCount <= range.max) {
          const data = rangeCounts.get(range.label)!;
          data.count++;
          break;
        }
      }
    });

    return PAGE_RANGES.map(range => {
      const data = rangeCounts.get(range.label)!;
      return {
        range: range.label,
        count: data.count,
        color: data.color
      };
    });
  }

  private updateChartData(stats: PageStats[]): void {
    const labels = stats.map(s => s.range);
    const data = stats.map(s => s.count);
    const colors = stats.map(s => s.color);

    this.chartDataSubject.next({
      labels,
      datasets: [{
        data,
        backgroundColor: colors,
        borderColor: colors.map(() => 'rgba(255, 255, 255, 0.2)'),
        borderWidth: 1,
        borderRadius: 4,
        barPercentage: 0.8,
        categoryPercentage: 0.7
      }]
    });
  }
}
