import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData, Chart, TooltipItem} from 'chart.js';
import {BookService} from '../../../../../book/service/book.service';
import {BookState} from '../../../../../book/model/state/book-state.model';
import {Book, ReadStatus} from '../../../../../book/model/book.model';

interface ReadingStatusStats {
  status: string;
  count: number;
  percentage: number;
}

const STATUS_COLOR_MAP: Record<string, string> = {
  'Unread': '#6c757d',           // Gray
  'Currently Reading': '#17a2b8', // Cyan
  'Re-reading': '#6f42c1',        // Purple
  'Read': '#28a745',              // Green
  'Partially Read': '#ffc107',    // Yellow
  'Paused': '#fd7e14',            // Orange
  "Won't Read": '#dc3545',        // Red
  'Abandoned': '#e74c3c',         // Light Red
  'No Status': '#343a40'          // Dark Gray
} as const;

const CHART_DEFAULTS = {
  borderColor: '#ffffff',
  borderWidth: 2,
  hoverBorderWidth: 3,
  hoverBorderColor: '#ffffff'
} as const;

type StatusChartData = ChartData<'doughnut', number[], string>;

@Component({
  selector: 'app-read-status-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './read-status-chart.component.html',
  styleUrls: ['./read-status-chart.component.scss']
})
export class ReadStatusChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'doughnut' as const;

  public readonly chartOptions: ChartConfiguration['options'] = {
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
          padding: 15,
          usePointStyle: true,
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 12
          },
          generateLabels: this.generateLegendLabels.bind(this)
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
          title: (context) => context[0]?.label || '',
          label: this.formatTooltipLabel
        }
      }
    },
    interaction: {
      intersect: false,
      mode: 'point'
    }
  };

  private readonly chartDataSubject = new BehaviorSubject<StatusChartData>({
    labels: [],
    datasets: [{
      data: [],
      backgroundColor: [...Object.values(STATUS_COLOR_MAP)],
      ...CHART_DEFAULTS
    }]
  });

  public readonly chartData$: Observable<StatusChartData> = this.chartDataSubject.asObservable();

  ngOnInit(): void {
    this.bookService.bookState$
      .pipe(
        filter(state => state.loaded),
        first(),
        catchError((error) => {
          console.error('Error processing reading status stats:', error);
          return EMPTY;
        }),
        takeUntil(this.destroy$)
      )
      .subscribe(() => {
        const stats = this.calculateReadingStatusStats();
        this.updateChartData(stats);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateChartData(stats: ReadingStatusStats[]): void {
    try {
      const labels = stats.map(s => s.status);
      const dataValues = stats.map(s => s.count);
      const colors = stats.map(s => STATUS_COLOR_MAP[s.status] || '#6c757d');

      this.chartDataSubject.next({
        labels,
        datasets: [{
          data: dataValues,
          backgroundColor: colors,
          ...CHART_DEFAULTS
        }]
      });
    } catch (error) {
      console.error('Error updating chart data:', error);
    }
  }

  private calculateReadingStatusStats(): ReadingStatusStats[] {
    const currentState = this.bookService.getCurrentBookState();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    return this.processReadingStatusStats(currentState.books!);
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

  private processReadingStatusStats(books: Book[]): ReadingStatusStats[] {
    if (books.length === 0) {
      return [];
    }

    const statusMap = this.buildStatusMap(books);
    return this.convertMapToStats(statusMap, books.length);
  }

  private buildStatusMap(books: Book[]): Map<ReadStatus, number> {
    const statusMap = new Map<ReadStatus, number>();

    for (const book of books) {
      const rawStatus = book.readStatus;
      const status: ReadStatus = Object.values(ReadStatus).includes(rawStatus as ReadStatus)
        ? (rawStatus as ReadStatus)
        : ReadStatus.UNSET;

      statusMap.set(status, (statusMap.get(status) || 0) + 1);
    }

    return statusMap;
  }

  private convertMapToStats(statusMap: Map<ReadStatus, number>, totalBooks: number): ReadingStatusStats[] {
    return Array.from(statusMap.entries())
      .map(([status, count]) => ({
        status: this.formatReadStatus(status),
        count,
        percentage: Number(((count / totalBooks) * 100).toFixed(1))
      }))
      .sort((a, b) => b.count - a.count);
  }

  private formatReadStatus(status: ReadStatus | null | undefined): string {
    const STATUS_MAPPING: Record<string, string> = {
      [ReadStatus.UNREAD]: 'Unread',
      [ReadStatus.READING]: 'Currently Reading',
      [ReadStatus.RE_READING]: 'Re-reading',
      [ReadStatus.READ]: 'Read',
      [ReadStatus.PARTIALLY_READ]: 'Partially Read',
      [ReadStatus.PAUSED]: 'Paused',
      [ReadStatus.WONT_READ]: "Won't Read",
      [ReadStatus.ABANDONED]: 'Abandoned',
      [ReadStatus.UNSET]: 'No Status'
    };

    if (!status) return 'No Status';
    return STATUS_MAPPING[status] ?? 'No Status';
  }

  private generateLegendLabels(chart: Chart) {
    const data = chart.data;
    if (!data.labels?.length || !data.datasets?.[0]?.data?.length) {
      return [];
    }

    const dataset = data.datasets[0];
    const dataValues = dataset.data as number[];

    return data.labels.map((label: unknown, index: number) => {
      const isVisible = typeof chart.getDataVisibility === 'function'
        ? chart.getDataVisibility(index)
        : !((chart.getDatasetMeta && (chart.getDatasetMeta(0)?.data?.[index] as any)?.hidden) || false);

      return {
        text: `${String(label)} (${dataValues[index]})`,
        fillStyle: (dataset.backgroundColor as string[])[index],
        strokeStyle: '#ffffff',
        lineWidth: 1,
        hidden: !isVisible,
        index,
        fontColor: '#ffffff'
      };
    });
  }

  private formatTooltipLabel(context: TooltipItem<any>): string {
    const dataIndex = context.dataIndex;
    const dataset = context.dataset;
    const value = dataset.data[dataIndex] as number;
    const label = context.chart.data.labels?.[dataIndex] || 'Unknown';
    const total = (dataset.data as number[]).reduce((a: number, b: number) => a + b, 0);
    const percentage = ((value / total) * 100).toFixed(1);
    return `${label}: ${value} books (${percentage}%)`;
  }
}
