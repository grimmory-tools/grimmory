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

interface DecadeStats {
  decade: string;
  label: string;
  count: number;
  color: string;
}

interface TimelineInsights {
  oldestBook: { title: string; year: number } | null;
  newestBook: { title: string; year: number } | null;
  averageYear: number;
  medianYear: number;
  totalWithDate: number;
  timeSpan: number;
  peakDecade: string;
  peakDecadeCount: number;
  centuryBreakdown: { c21: number; c20: number; older: number };
  decadesCovered: number;
}

type TimelineChartData = ChartData<'bar', number[], string>;

// Color gradient from warm (old) to cool (new)
const DECADE_COLORS: Record<string, string> = {
  'pre1900': '#92400e',
  '1900s': '#b45309',
  '1910s': '#c2410c',
  '1920s': '#d97706',
  '1930s': '#e5932d',
  '1940s': '#eab308',
  '1950s': '#a3e635',
  '1960s': '#4ade80',
  '1970s': '#22d3ee',
  '1980s': '#38bdf8',
  '1990s': '#60a5fa',
  '2000s': '#818cf8',
  '2010s': '#a78bfa',
  '2020s': '#c084fc'
};

@Component({
  selector: 'app-publication-timeline-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './publication-timeline-chart.component.html',
  styleUrls: ['./publication-timeline-chart.component.scss']
})
export class PublicationTimelineChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'bar' as const;
  public chartOptions: ChartConfiguration<'bar'>['options'];
  public insights: TimelineInsights | null = null;
  public totalBooks = 0;

  private readonly chartDataSubject = new BehaviorSubject<TimelineChartData>({
    labels: [],
    datasets: []
  });

  public readonly chartData$: Observable<TimelineChartData> = this.chartDataSubject.asObservable();

  constructor() {
    this.initChartOptions();
  }

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
          console.error('Error processing publication timeline data:', error);
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

  private initChartOptions(): void {
    this.chartOptions = {
      responsive: true,
      maintainAspectRatio: false,
      indexAxis: 'y',
      layout: {
        padding: {top: 10, right: 20, bottom: 10, left: 10}
      },
      scales: {
        x: {
          beginAtZero: true,
          ticks: {
            color: 'rgba(255, 255, 255, 0.8)',
            font: {
              family: "'Inter', sans-serif",
              size: 11
            },
            precision: 0,
            stepSize: 1
          },
          grid: {
            color: 'rgba(255, 255, 255, 0.08)'
          },
          border: {display: false},
          title: {
            display: true,
            text: 'Number of Books',
            color: '#ffffff',
            font: {
              family: "'Inter', sans-serif",
              size: 12,
              weight: 500
            }
          }
        },
        y: {
          ticks: {
            color: 'rgba(255, 255, 255, 0.9)',
            font: {
              family: "'Inter', sans-serif",
              size: 11
            }
          },
          grid: {
            display: false
          },
          border: {display: false}
        }
      },
      plugins: {
        legend: {
          display: false
        },
        tooltip: {
          enabled: true,
          backgroundColor: 'rgba(0, 0, 0, 0.95)',
          titleColor: '#ffffff',
          bodyColor: '#ffffff',
          borderColor: '#a78bfa',
          borderWidth: 2,
          cornerRadius: 8,
          padding: 12,
          titleFont: {size: 13, weight: 'bold'},
          bodyFont: {size: 11},
          callbacks: {
            label: (context) => {
              const value = context.parsed.x;
              const bookText = value === 1 ? 'book' : 'books';
              return `${value} ${bookText}`;
            }
          }
        },
        datalabels: {
          display: false
        }
      }
    };
  }

  private calculateAndUpdateChart(): void {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      this.chartDataSubject.next({labels: [], datasets: []});
      this.insights = null;
      this.totalBooks = 0;
      return;
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    const booksWithDate = filteredBooks.filter(b => b.metadata?.publishedDate);

    this.totalBooks = booksWithDate.length;

    if (booksWithDate.length === 0) {
      this.chartDataSubject.next({labels: [], datasets: []});
      this.insights = null;
      return;
    }

    const decadeStats = this.calculateDecadeStats(booksWithDate);
    this.insights = this.calculateInsights(booksWithDate);
    this.updateChartData(decadeStats);
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

  private calculateDecadeStats(books: Book[]): DecadeStats[] {
    const decadeCounts = new Map<string, number>();

    for (const book of books) {
      const year = this.extractYear(book.metadata?.publishedDate);
      if (!year) continue;

      const decadeKey = this.getDecadeKey(year);
      decadeCounts.set(decadeKey, (decadeCounts.get(decadeKey) || 0) + 1);
    }

    // Define decade order
    const decadeOrder = [
      'pre1900', '1900s', '1910s', '1920s', '1930s', '1940s',
      '1950s', '1960s', '1970s', '1980s', '1990s', '2000s', '2010s', '2020s'
    ];

    const decadeLabels: Record<string, string> = {
      'pre1900': 'Pre-1900',
      '1900s': '1900s',
      '1910s': '1910s',
      '1920s': '1920s',
      '1930s': '1930s',
      '1940s': '1940s',
      '1950s': '1950s',
      '1960s': '1960s',
      '1970s': '1970s',
      '1980s': '1980s',
      '1990s': '1990s',
      '2000s': '2000s',
      '2010s': '2010s',
      '2020s': '2020s'
    };

    return decadeOrder
      .filter(decade => decadeCounts.has(decade))
      .map(decade => ({
        decade,
        label: decadeLabels[decade],
        count: decadeCounts.get(decade) || 0,
        color: DECADE_COLORS[decade]
      }));
  }

  private extractYear(dateStr: string | undefined): number | null {
    if (!dateStr) return null;

    // Try parsing as full date or just year
    const yearMatch = dateStr.match(/\d{4}/);
    if (yearMatch) {
      const year = parseInt(yearMatch[0], 10);
      if (year >= 1000 && year <= new Date().getFullYear() + 1) {
        return year;
      }
    }
    return null;
  }

  private getDecadeKey(year: number): string {
    if (year < 1900) return 'pre1900';
    if (year >= 2020) return '2020s';
    const decade = Math.floor(year / 10) * 10;
    return `${decade}s`;
  }

  private calculateInsights(books: Book[]): TimelineInsights {
    const years: number[] = [];
    const decadeCounts = new Map<string, number>();
    let oldest: { title: string; year: number } | null = null;
    let newest: { title: string; year: number } | null = null;
    let c21 = 0, c20 = 0, older = 0;

    for (const book of books) {
      const year = this.extractYear(book.metadata?.publishedDate);
      if (!year) continue;

      years.push(year);
      const title = book.metadata?.title || 'Unknown';

      if (!oldest || year < oldest.year) {
        oldest = {title, year};
      }
      if (!newest || year > newest.year) {
        newest = {title, year};
      }

      // Count by century
      if (year >= 2000) c21++;
      else if (year >= 1900) c20++;
      else older++;

      // Count by decade for peak decade
      const decadeKey = this.getDecadeKey(year);
      decadeCounts.set(decadeKey, (decadeCounts.get(decadeKey) || 0) + 1);
    }

    years.sort((a, b) => a - b);
    const averageYear = years.length > 0
      ? Math.round(years.reduce((a, b) => a + b, 0) / years.length)
      : 0;
    const medianYear = years.length > 0
      ? years[Math.floor(years.length / 2)]
      : 0;

    // Find peak decade
    let peakDecade = '';
    let peakDecadeCount = 0;
    for (const [decade, count] of decadeCounts) {
      if (count > peakDecadeCount) {
        peakDecade = decade === 'pre1900' ? 'Pre-1900' : decade.toUpperCase();
        peakDecadeCount = count;
      }
    }

    const timeSpan = oldest && newest ? newest.year - oldest.year : 0;

    return {
      oldestBook: oldest,
      newestBook: newest,
      averageYear,
      medianYear,
      totalWithDate: years.length,
      timeSpan,
      peakDecade,
      peakDecadeCount,
      centuryBreakdown: {c21, c20, older},
      decadesCovered: decadeCounts.size
    };
  }

  private updateChartData(stats: DecadeStats[]): void {
    const labels = stats.map(s => s.label);
    const data = stats.map(s => s.count);
    const colors = stats.map(s => s.color);

    this.chartDataSubject.next({
      labels,
      datasets: [{
        data,
        backgroundColor: colors,
        borderColor: colors,
        borderWidth: 1,
        borderRadius: 4,
        barPercentage: 0.8,
        categoryPercentage: 0.85
      }]
    });
  }
}
