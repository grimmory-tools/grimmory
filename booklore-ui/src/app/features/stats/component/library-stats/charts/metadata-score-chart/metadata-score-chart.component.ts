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

interface ScoreRange {
  label: string;
  min: number;
  max: number;
  color: string;
}

interface ScoreStats {
  range: string;
  count: number;
  percentage: number;
  color: string;
}

type ScoreChartData = ChartData<'doughnut', number[], string>;

const SCORE_RANGES: ScoreRange[] = [
  {label: 'Excellent (90-100%)', min: 90, max: 100, color: '#16A34A'},
  {label: 'Good (70-89%)', min: 70, max: 89, color: '#22C55E'},
  {label: 'Fair (50-69%)', min: 50, max: 69, color: '#F59E0B'},
  {label: 'Poor (25-49%)', min: 25, max: 49, color: '#F97316'},
  {label: 'Very Poor (0-24%)', min: 0, max: 24, color: '#DC2626'}
];

@Component({
  selector: 'app-metadata-score-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './metadata-score-chart.component.html',
  styleUrls: ['./metadata-score-chart.component.scss']
})
export class MetadataScoreChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'doughnut' as const;
  public scoreStats: ScoreStats[] = [];
  public totalBooks = 0;
  public averageScore = 0;

  public readonly chartOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '60%',
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
            size: 11
          },
          usePointStyle: true,
          pointStyle: 'circle',
          padding: 12,
          boxWidth: 8
        }
      },
      tooltip: {
        enabled: true,
        backgroundColor: 'rgba(0, 0, 0, 0.95)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: '#16A34A',
        borderWidth: 2,
        cornerRadius: 8,
        padding: 12,
        titleFont: {size: 13, weight: 'bold'},
        bodyFont: {size: 11},
        callbacks: {
          label: (context) => {
            const value = context.parsed;
            const total = context.dataset.data.reduce((a: number, b: number) => a + b, 0);
            const percentage = ((value / total) * 100).toFixed(1);
            return `${value} books (${percentage}%)`;
          }
        }
      },
      datalabels: {
        display: false
      }
    }
  };

  private readonly chartDataSubject = new BehaviorSubject<ScoreChartData>({
    labels: [],
    datasets: []
  });

  public readonly chartData$: Observable<ScoreChartData> = this.chartDataSubject.asObservable();

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
          console.error('Error processing metadata score data:', error);
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
      this.scoreStats = [];
      this.totalBooks = 0;
      this.averageScore = 0;
      return;
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    const booksWithScore = filteredBooks.filter(b => b.metadataMatchScore != null && b.metadataMatchScore >= 0);

    this.totalBooks = booksWithScore.length;

    if (booksWithScore.length === 0) {
      this.chartDataSubject.next({labels: [], datasets: []});
      this.scoreStats = [];
      this.averageScore = 0;
      return;
    }

    const stats = this.calculateScoreStats(booksWithScore);
    this.scoreStats = stats;
    this.averageScore = this.calculateAverageScore(booksWithScore);
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

  private calculateScoreStats(books: Book[]): ScoreStats[] {
    const rangeCounts = new Map<string, { count: number, color: string }>();

    SCORE_RANGES.forEach(range => {
      rangeCounts.set(range.label, {count: 0, color: range.color});
    });

    books.forEach(book => {
      const score = book.metadataMatchScore!;
      for (const range of SCORE_RANGES) {
        if (score >= range.min && score <= range.max) {
          const data = rangeCounts.get(range.label)!;
          data.count++;
          break;
        }
      }
    });

    const total = books.length;
    return SCORE_RANGES
      .map(range => {
        const data = rangeCounts.get(range.label)!;
        return {
          range: range.label,
          count: data.count,
          percentage: (data.count / total) * 100,
          color: data.color
        };
      })
      .filter(stat => stat.count > 0);
  }

  private calculateAverageScore(books: Book[]): number {
    const total = books.reduce((sum, book) => sum + (book.metadataMatchScore || 0), 0);
    return Math.round(total / books.length);
  }

  private updateChartData(stats: ScoreStats[]): void {
    const labels = stats.map(s => s.range);
    const data = stats.map(s => s.count);
    const colors = stats.map(s => s.color);

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
