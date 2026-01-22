import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BookService} from '../../../../../book/service/book.service';
import {BookState} from '../../../../../book/model/state/book-state.model';
import {Book} from '../../../../../book/model/book.model';

interface ReadingProgressStats {
  progressRange: string;
  count: number;
  description: string;
}

const CHART_COLORS = [
  '#6c757d', // Gray - Not Started (0%)
  '#ffc107', // Yellow - Just Started (1-25%)
  '#fd7e14', // Orange - Getting Into It (26-50%)
  '#17a2b8', // Cyan - Halfway Through (51-75%)
  '#6f42c1', // Purple - Almost Finished (76-99%)
  '#28a745'  // Green - Completed (100%)
] as const;

const CHART_DEFAULTS = {
  borderColor: '#ffffff',
  borderWidth: 1,
  hoverBorderWidth: 2,
  hoverBorderColor: '#ffffff'
} as const;

const PROGRESS_RANGES = [
  {range: '0%', min: 0, max: 0, desc: 'Not Started'},
  {range: '1-25%', min: 0.1, max: 25, desc: 'Just Started'},
  {range: '26-50%', min: 26, max: 50, desc: 'Getting Into It'},
  {range: '51-75%', min: 51, max: 75, desc: 'Halfway Through'},
  {range: '76-99%', min: 76, max: 99, desc: 'Almost Finished'},
  {range: '100%', min: 100, max: 100, desc: 'Completed'}
] as const;

type ProgressChartData = ChartData<'doughnut', number[], string>;

@Component({
  selector: 'app-reading-progress-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './reading-progress-chart.component.html',
  styleUrls: ['./reading-progress-chart.component.scss']
})
export class ReadingProgressChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'doughnut' as const;

  public readonly chartOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    layout: {
      padding: {top: 15}
    },
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
        labels: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 12
          },
          padding: 15,
          usePointStyle: true,
          pointStyle: 'circle',
          generateLabels: (chart) => {
            const data = chart.data;
            if (data.labels?.length && data.datasets?.length) {
              return data.labels.map((label, i) => {
                const value = data.datasets[0].data[i] as number;
                const isVisible = typeof chart.getDataVisibility === 'function'
                  ? chart.getDataVisibility(i)
                  : true;

                return {
                  text: `${label}: ${value}`,
                  fillStyle: (data.datasets[0].backgroundColor as string[])[i],
                  strokeStyle: '#ffffff',
                  lineWidth: 1,
                  hidden: !isVisible,
                  index: i,
                  fontColor: '#ffffff'
                };
              });
            }
            return [];
          }
        }
      },
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
          title: (context) => context[0].label,
          label: (context) => {
            const value = context.parsed;
            const total = (context.dataset.data as number[]).reduce((a, b) => a + b, 0);
            const percentage = ((value / total) * 100).toFixed(1);
            const label = context.label;
            const rangeInfo = PROGRESS_RANGES.find(r => r.range === label);
            const description = rangeInfo ? ` (${rangeInfo.desc})` : '';
            return `${value} book${value === 1 ? '' : 's'}${description} - ${percentage}%`;
          }
        }
      },
      datalabels: {display: false}
    },
    interaction: {
      intersect: false,
      mode: 'point'
    }
  };

  private readonly chartDataSubject = new BehaviorSubject<ProgressChartData>({
    labels: [],
    datasets: [{
      label: 'Books by Progress',
      data: [],
      backgroundColor: [...CHART_COLORS],
      ...CHART_DEFAULTS
    }]
  });

  public readonly chartData$: Observable<ProgressChartData> = this.chartDataSubject.asObservable();

  ngOnInit(): void {
    this.bookService.bookState$
      .pipe(
        filter(state => state.loaded),
        first(),
        catchError((error) => {
          console.error('Error processing reading progress data:', error);
          return EMPTY;
        }),
        takeUntil(this.destroy$)
      )
      .subscribe(() => {
        const stats = this.calculateReadingProgressStats();
        this.updateChartData(stats);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateChartData(stats: ReadingProgressStats[]): void {
    try {
      const labels = stats.map(s => s.progressRange);
      const dataValues = stats.map(s => s.count);

      this.chartDataSubject.next({
        labels,
        datasets: [{
          label: 'Books by Progress',
          data: dataValues,
          backgroundColor: [...CHART_COLORS],
          borderColor: '#ffffff',
          borderWidth: 2,
          hoverBorderWidth: 3,
          hoverBorderColor: '#ffffff'
        }]
      });
    } catch (error) {
      console.error('Error updating reading progress chart data:', error);
    }
  }

  private calculateReadingProgressStats(): ReadingProgressStats[] {
    const currentState = this.bookService.getCurrentBookState();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    return this.processReadingProgressStats(currentState.books!);
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

  private processReadingProgressStats(books: Book[]): ReadingProgressStats[] {
    const rangeCounts = new Map<string, number>();
    PROGRESS_RANGES.forEach(range => rangeCounts.set(range.range, 0));

    for (const book of books) {
      const progress = this.getBookProgress(book);

      for (const range of PROGRESS_RANGES) {
        if (progress >= range.min && progress <= range.max) {
          rangeCounts.set(range.range, (rangeCounts.get(range.range) || 0) + 1);
          break;
        }
      }
    }

    return PROGRESS_RANGES.map(range => ({
      progressRange: range.range,
      count: rangeCounts.get(range.range) || 0,
      description: range.desc
    }));
  }

  private getBookProgress(book: Book): number {
    if (book.pdfProgress?.percentage) return book.pdfProgress.percentage;
    if (book.epubProgress?.percentage) return book.epubProgress.percentage;
    if (book.cbxProgress?.percentage) return book.cbxProgress.percentage;
    if (book.koreaderProgress?.percentage) return book.koreaderProgress.percentage;
    if (book.koboProgress?.percentage) return book.koboProgress.percentage;
    return 0;
  }
}
