import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BookService} from '../../../../../book/service/book.service';
import {BookState} from '../../../../../book/model/state/book-state.model';
import {Book} from '../../../../../book/model/book.model';

interface RatingStats {
  ratingRange: string;
  count: number;
  averageRating: number;
}

const CHART_COLORS = [
  '#DC2626', // Red (rating 1)
  '#EA580C', // Red-orange (rating 2)
  '#F59E0B', // Orange (rating 3)
  '#EAB308', // Yellow-orange (rating 4)
  '#FACC15', // Yellow (rating 5)
  '#BEF264', // Yellow-green (rating 6)
  '#65A30D', // Green (rating 7)
  '#16A34A', // Green (rating 8)
  '#059669', // Teal-green (rating 9)
  '#2563EB'  // Blue (rating 10)
] as const;

const CHART_DEFAULTS = {
  borderColor: '#ffffff',
  borderWidth: 1,
  hoverBorderWidth: 2,
  hoverBorderColor: '#ffffff'
} as const;

const RATING_RANGES = [
  {range: '1', min: 1.0, max: 1.0},
  {range: '2', min: 2.0, max: 2.0},
  {range: '3', min: 3.0, max: 3.0},
  {range: '4', min: 4.0, max: 4.0},
  {range: '5', min: 5.0, max: 5.0},
  {range: '6', min: 6.0, max: 6.0},
  {range: '7', min: 7.0, max: 7.0},
  {range: '8', min: 8.0, max: 8.0},
  {range: '9', min: 9.0, max: 9.0},
  {range: '10', min: 10.0, max: 10.0}
] as const;

type RatingChartData = ChartData<'bar', number[], string>;

@Component({
  selector: 'app-personal-rating-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './personal-rating-chart.component.html',
  styleUrls: ['./personal-rating-chart.component.scss']
})
export class PersonalRatingChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'bar' as const;

  public readonly chartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    layout: {
      padding: {top: 25}
    },
    plugins: {
      legend: {display: false},
      tooltip: {
        enabled: true,
        backgroundColor: 'rgba(0, 0, 0, 0.9)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: '#ffffff',
        borderWidth: 1,
        cornerRadius: 6,
        displayColors: true,
        padding: 12,
        titleFont: {size: 14, weight: 'bold'},
        bodyFont: {size: 13},
        callbacks: {
          title: (context) => `Personal Rating: ${context[0].label}`,
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
          text: 'Personal Rating',
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 12
          }
        },
        ticks: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11
          }
        },
        grid: {display: false},
        border: {display: false}
      },
      y: {
        title: {
          display: true,
          text: 'Number of Books',
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 12
          }
        },
        beginAtZero: true,
        ticks: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11
          },
          stepSize: 1,
          maxTicksLimit: 8
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.1)'
        },
        border: {display: false}
      }
    }
  };

  private readonly chartDataSubject = new BehaviorSubject<RatingChartData>({
    labels: [],
    datasets: [{
      label: 'Books by Personal Rating',
      data: [],
      backgroundColor: [...CHART_COLORS],
      ...CHART_DEFAULTS
    }]
  });

  public readonly chartData$: Observable<RatingChartData> = this.chartDataSubject.asObservable();

  ngOnInit(): void {
    this.bookService.bookState$
      .pipe(
        filter(state => state.loaded),
        first(),
        catchError((error) => {
          console.error('Error processing personal rating data:', error);
          return EMPTY;
        }),
        takeUntil(this.destroy$)
      )
      .subscribe(() => {
        const stats = this.calculatePersonalRatingStats();
        this.updateChartData(stats);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateChartData(stats: RatingStats[]): void {
    try {
      // Always show all rating labels from 1-10, even if there's no data
      const allLabels = RATING_RANGES.map(r => r.range);
      const dataValues = allLabels.map(label => {
        const stat = stats.find(s => s.ratingRange === label);
        return stat ? stat.count : 0;
      });
      const colors = allLabels.map((_, index) => CHART_COLORS[index % CHART_COLORS.length]);

      this.chartDataSubject.next({
        labels: allLabels,
        datasets: [{
          label: 'Books by Personal Rating',
          data: dataValues,
          backgroundColor: colors,
          borderColor: colors.map(color => color),
          borderWidth: 1,
          borderRadius: 4,
          barPercentage: 0.8,
          categoryPercentage: 0.6
        }]
      });
    } catch (error) {
      console.error('Error updating personal rating chart data:', error);
    }
  }

  private calculatePersonalRatingStats(): RatingStats[] {
    const currentState = this.bookService.getCurrentBookState();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    return this.processPersonalRatingStats(currentState.books!);
  }

  private isValidBookState(state: unknown): state is BookState {
    return (
      typeof state === 'object' &&
      state !== null &&
      'loaded' in state &&
      typeof (state as {loaded: boolean}).loaded === 'boolean' &&
      'books' in state &&
      Array.isArray((state as {books: unknown}).books) &&
      (state as {books: Book[]}).books.length > 0
    );
  }

  private processPersonalRatingStats(books: Book[]): RatingStats[] {
    const rangeCounts = new Map<string, { count: number, totalRating: number }>();
    RATING_RANGES.forEach(range => rangeCounts.set(range.range, {count: 0, totalRating: 0}));

    books.forEach(book => {
      const personalRating = book.personalRating;

      if (personalRating && personalRating > 0) {
        for (const range of RATING_RANGES) {
          if (personalRating >= range.min && personalRating <= range.max) {
            const rangeData = rangeCounts.get(range.range)!;
            rangeData.count++;
            rangeData.totalRating += personalRating;
            break;
          }
        }
      }
    });

    // Return all ratings, including those with 0 count
    return RATING_RANGES.map(range => {
      const data = rangeCounts.get(range.range)!;
      return {
        ratingRange: range.range,
        count: data.count,
        averageRating: data.count > 0 ? data.totalRating / data.count : 0
      };
    });
  }
}
