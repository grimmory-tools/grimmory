import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BookService} from '../../../../../book/service/book.service';
import {BookState} from '../../../../../book/model/state/book-state.model';
import {Book} from '../../../../../book/model/book.model';

interface MatrixDataPoint {
  x: number; // month (0-11)
  y: number; // year index
  v: number; // book count
}

interface YearMonthData {
  year: number;
  month: number;
  count: number;
}

const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

type HeatmapChartData = ChartData<'matrix', MatrixDataPoint[], string>;

@Component({
  selector: 'app-reading-heatmap-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './reading-heatmap-chart.component.html',
  styleUrls: ['./reading-heatmap-chart.component.scss']
})
export class ReadingHeatmapChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'matrix' as const;

  private yearLabels: string[] = [];
  private maxBookCount = 1;

  public readonly chartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    layout: {
      padding: {
        top: 20
      }
    },
    plugins: {
      legend: {display: false},
      tooltip: {
        enabled: true,
        backgroundColor: 'rgba(0, 0, 0, 0.95)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: '#ef476f',
        borderWidth: 2,
        cornerRadius: 8,
        displayColors: false,
        padding: 16,
        titleFont: {size: 14, weight: 'bold'},
        bodyFont: {size: 13},
        callbacks: {
          title: (context) => {
            const point = context[0].raw as MatrixDataPoint;
            const year = this.yearLabels[point.y];
            const month = MONTH_NAMES[point.x];
            return `${month} ${year}`;
          },
          label: (context) => {
            const point = context.raw as MatrixDataPoint;
            return `${point.v} book${point.v === 1 ? '' : 's'} read`;
          }
        }
      },
      datalabels: {
        display: true,
        color: '#ffffff',
        font: {
          family: "'Inter', sans-serif",
          size: 10,
          weight: 'bold'
        },
        formatter: (value: MatrixDataPoint) => value.v > 0 ? value.v.toString() : ''
      }
    },
    scales: {
      x: {
        type: 'linear',
        position: 'bottom',
        ticks: {
          stepSize: 1,
          callback: (value) => MONTH_NAMES[value as number] || '',
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11
          }
        },
        grid: {display: false},
      },
      y: {
        type: 'linear',
        offset: true,
        ticks: {
          stepSize: 1,
          callback: (value) => this.yearLabels[value as number] || '',
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11
          }
        },
        grid: {display: false},
      }
    }
  };

  private readonly chartDataSubject = new BehaviorSubject<HeatmapChartData>({
    labels: [],
    datasets: [{
      label: 'Books Read',
      data: []
    }]
  });

  public readonly chartData$: Observable<HeatmapChartData> = this.chartDataSubject.asObservable();

  ngOnInit(): void {
    this.bookService.bookState$
      .pipe(
        filter(state => state.loaded),
        first(),
        catchError((error) => {
          console.error('Error processing reading heatmap data:', error);
          return EMPTY;
        }),
        takeUntil(this.destroy$)
      )
      .subscribe(() => {
        const stats = this.calculateHeatmapData();
        this.updateChartData(stats);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateChartData(yearMonthData: YearMonthData[]): void {
    const currentYear = new Date().getFullYear();
    const years = Array.from({length: 10}, (_, i) => currentYear - 9 + i);

    this.yearLabels = years.map(String);
    this.maxBookCount = Math.max(1, ...yearMonthData.map(d => d.count));

    const heatmapData: MatrixDataPoint[] = [];

    years.forEach((year, yearIndex) => {
      for (let month = 0; month <= 11; month++) {
        const dataPoint = yearMonthData.find(d => d.year === year && d.month === month + 1);
        heatmapData.push({
          x: month,
          y: yearIndex,
          v: dataPoint?.count || 0
        });
      }
    });

    if (this.chartOptions?.scales?.['y']) {
      (this.chartOptions.scales['y'] as any).max = years.length - 1;
    }

    this.chartDataSubject.next({
      labels: [],
      datasets: [{
        label: 'Books Read',
        data: heatmapData,
        backgroundColor: (context) => {
          const point = context.raw as MatrixDataPoint;
          if (!point?.v) return 'rgba(255, 255, 255, 0.05)';

          const intensity = point.v / this.maxBookCount;
          const alpha = Math.max(0.2, Math.min(1.0, intensity * 0.8 + 0.2));
          return `rgba(239, 71, 111, ${alpha})`;
        },
        borderColor: 'rgba(255, 255, 255, 0.2)',
        borderWidth: 1,
        width: ({chart}) => (chart.chartArea?.width || 0) / 12 - 1,
        height: ({chart}) => (chart.chartArea?.height || 0) / years.length - 1
      }]
    });
  }

  private calculateHeatmapData(): YearMonthData[] {
    const currentState = this.bookService.getCurrentBookState();

    if (!this.isValidBookState(currentState)) {
      return [];
    }

    return this.processHeatmapData(currentState.books!);
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

  private processHeatmapData(books: Book[]): YearMonthData[] {
    const yearMonthMap = new Map<string, number>();
    const currentYear = new Date().getFullYear();
    const startYear = currentYear - 9;

    books
      .filter(book => book.dateFinished)
      .forEach(book => {
        const finishedDate = new Date(book.dateFinished!);
        const year = finishedDate.getFullYear();

        if (year >= startYear && year <= currentYear) {
          const month = finishedDate.getMonth() + 1;
          const key = `${year}-${month}`;
          yearMonthMap.set(key, (yearMonthMap.get(key) || 0) + 1);
        }
      });

    return Array.from(yearMonthMap.entries())
      .map(([key, count]) => {
        const [year, month] = key.split('-').map(Number);
        return {year, month, count};
      })
      .sort((a, b) => a.year - b.year || a.month - b.month);
  }
}

