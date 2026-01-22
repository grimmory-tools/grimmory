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

interface TrendInsights {
  peakYear: number;
  peakYearCount: number;
  booksLast10Years: number;
  booksLast10YearsPercent: number;
  averageBooksPerYear: number;
  mostProductiveSpan: string;
  // Additional insights
  timeSpan: number;
  classicBooks: number;
  classicBooksPercent: number;
  century21Books: number;
  century21Percent: number;
  uniqueYears: number;
  oldestDecade: string;
  newestDecade: string;
}

type TrendChartData = ChartData<'line', number[], string>;

@Component({
  selector: 'app-publication-trend-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './publication-trend-chart.component.html',
  styleUrls: ['./publication-trend-chart.component.scss']
})
export class PublicationTrendChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'line' as const;
  public chartOptions: ChartConfiguration<'line'>['options'];
  public insights: TrendInsights | null = null;
  public totalBooks = 0;
  public yearRange = '';

  private readonly chartDataSubject = new BehaviorSubject<TrendChartData>({
    labels: [],
    datasets: []
  });

  public readonly chartData$: Observable<TrendChartData> = this.chartDataSubject.asObservable();

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
          console.error('Error processing publication trend data:', error);
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
      layout: {
        padding: {top: 20, right: 20, bottom: 10, left: 10}
      },
      scales: {
        x: {
          ticks: {
            color: 'rgba(255, 255, 255, 0.8)',
            font: {
              family: "'Inter', sans-serif",
              size: 10
            },
            maxRotation: 45,
            minRotation: 45,
            autoSkip: true,
            maxTicksLimit: 20
          },
          grid: {
            color: 'rgba(255, 255, 255, 0.05)'
          },
          border: {display: false},
          title: {
            display: true,
            text: 'Publication Year',
            color: '#ffffff',
            font: {
              family: "'Inter', sans-serif",
              size: 12,
              weight: 500
            }
          }
        },
        y: {
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
            text: 'Books',
            color: '#ffffff',
            font: {
              family: "'Inter', sans-serif",
              size: 12,
              weight: 500
            }
          }
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
          borderColor: '#06b6d4',
          borderWidth: 2,
          cornerRadius: 8,
          padding: 12,
          titleFont: {size: 13, weight: 'bold'},
          bodyFont: {size: 11},
          callbacks: {
            title: (context) => `Year ${context[0].label}`,
            label: (context) => {
              const value = context.parsed.y;
              const bookText = value === 1 ? 'book' : 'books';
              return `${value} ${bookText} published`;
            }
          }
        },
        datalabels: {
          display: false
        }
      },
      elements: {
        line: {
          tension: 0.3,
          borderWidth: 3
        },
        point: {
          radius: 4,
          hoverRadius: 7,
          borderWidth: 2,
          backgroundColor: '#0e1117'
        }
      },
      interaction: {
        intersect: false,
        mode: 'index'
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
      this.yearRange = '';
      return;
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    const booksWithDate = filteredBooks.filter(b => b.metadata?.publishedDate);

    this.totalBooks = booksWithDate.length;

    if (booksWithDate.length === 0) {
      this.chartDataSubject.next({labels: [], datasets: []});
      this.insights = null;
      this.yearRange = '';
      return;
    }

    const yearCounts = this.calculateYearCounts(booksWithDate);
    this.insights = this.calculateInsights(yearCounts, booksWithDate.length);
    this.updateChartData(yearCounts);
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

  private calculateYearCounts(books: Book[]): Map<number, number> {
    const yearCounts = new Map<number, number>();

    for (const book of books) {
      const year = this.extractYear(book.metadata?.publishedDate);
      if (!year) continue;
      yearCounts.set(year, (yearCounts.get(year) || 0) + 1);
    }

    return yearCounts;
  }

  private extractYear(dateStr: string | undefined): number | null {
    if (!dateStr) return null;

    const yearMatch = dateStr.match(/\d{4}/);
    if (yearMatch) {
      const year = parseInt(yearMatch[0], 10);
      if (year >= 1000 && year <= new Date().getFullYear() + 1) {
        return year;
      }
    }
    return null;
  }

  private calculateInsights(yearCounts: Map<number, number>, totalBooks: number): TrendInsights {
    const currentYear = new Date().getFullYear();
    const years = Array.from(yearCounts.keys()).sort((a, b) => a - b);

    // Peak year
    let peakYear = 0;
    let peakYearCount = 0;
    for (const [year, count] of yearCounts) {
      if (count > peakYearCount) {
        peakYear = year;
        peakYearCount = count;
      }
    }

    // Books in last 10 years
    let booksLast10Years = 0;
    let classicBooks = 0; // Pre-1970
    let century21Books = 0; // 2000+

    for (const [year, count] of yearCounts) {
      if (year >= currentYear - 10) {
        booksLast10Years += count;
      }
      if (year < 1970) {
        classicBooks += count;
      }
      if (year >= 2000) {
        century21Books += count;
      }
    }

    const booksLast10YearsPercent = totalBooks > 0 ? Math.round((booksLast10Years / totalBooks) * 100) : 0;
    const classicBooksPercent = totalBooks > 0 ? Math.round((classicBooks / totalBooks) * 100) : 0;
    const century21Percent = totalBooks > 0 ? Math.round((century21Books / totalBooks) * 100) : 0;

    // Average books per year (only counting years with books)
    const activeYears = years.length;
    const averageBooksPerYear = activeYears > 0 ? +(totalBooks / activeYears).toFixed(1) : 0;

    // Most productive 5-year span
    const mostProductiveSpan = this.findMostProductiveSpan(yearCounts, years);

    // Time span
    const timeSpan = years.length > 1 ? years[years.length - 1] - years[0] : 0;

    // Oldest and newest decades
    const oldestYear = years[0] || currentYear;
    const newestYear = years[years.length - 1] || currentYear;
    const oldestDecade = oldestYear < 1900 ? 'Pre-1900' : `${Math.floor(oldestYear / 10) * 10}s`;
    const newestDecade = `${Math.floor(newestYear / 10) * 10}s`;

    return {
      peakYear,
      peakYearCount,
      booksLast10Years,
      booksLast10YearsPercent,
      averageBooksPerYear,
      mostProductiveSpan,
      timeSpan,
      classicBooks,
      classicBooksPercent,
      century21Books,
      century21Percent,
      uniqueYears: activeYears,
      oldestDecade,
      newestDecade
    };
  }

  private findMostProductiveSpan(yearCounts: Map<number, number>, years: number[]): string {
    if (years.length === 0) return 'N/A';

    let maxCount = 0;
    let bestStartYear = years[0];

    for (const startYear of years) {
      let count = 0;
      for (let y = startYear; y < startYear + 5; y++) {
        count += yearCounts.get(y) || 0;
      }
      if (count > maxCount) {
        maxCount = count;
        bestStartYear = startYear;
      }
    }

    return `${bestStartYear}-${bestStartYear + 4}`;
  }

  private updateChartData(yearCounts: Map<number, number>): void {
    const years = Array.from(yearCounts.keys()).sort((a, b) => a - b);

    if (years.length === 0) {
      this.chartDataSubject.next({labels: [], datasets: []});
      this.yearRange = '';
      return;
    }

    // Fill in gaps for continuous line
    const minYear = years[0];
    const maxYear = years[years.length - 1];
    this.yearRange = `${minYear} - ${maxYear}`;

    const labels: string[] = [];
    const data: number[] = [];

    for (let year = minYear; year <= maxYear; year++) {
      labels.push(year.toString());
      data.push(yearCounts.get(year) || 0);
    }

    this.chartDataSubject.next({
      labels,
      datasets: [{
        data,
        borderColor: '#06b6d4',
        backgroundColor: 'rgba(6, 182, 212, 0.1)',
        pointBackgroundColor: '#06b6d4',
        pointBorderColor: '#ffffff',
        fill: true
      }]
    });
  }
}
