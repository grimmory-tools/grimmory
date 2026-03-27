import {Component, computed, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {Tooltip} from 'primeng/tooltip';
import {ChartConfiguration, ChartData, ScatterDataPoint} from 'chart.js';
import {BookService} from '../../../../../book/service/book.service';
import {Book, ReadStatus} from '../../../../../book/model/book.model';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

interface BookScatterPoint extends ScatterDataPoint {
  bookTitle: string;
  readStatus: string;
}

type LengthChartData = ChartData<'scatter', BookScatterPoint[], string>;

interface BookLengthMetrics {
  totalRatedBooks: number;
  sweetSpot: string;
  highestRatedLength: string;
  chartData: LengthChartData;
}

const STATUS_COLORS: Record<string, { bg: string; border: string }> = {
  'read': {bg: 'rgba(76, 175, 80, 0.7)', border: '#4caf50'},
  'reading': {bg: 'rgba(33, 150, 243, 0.7)', border: '#2196f3'},
  'abandoned': {bg: 'rgba(244, 67, 54, 0.7)', border: '#f44336'},
  'other': {bg: 'rgba(158, 158, 158, 0.7)', border: '#9e9e9e'}
};

const PAGE_RANGES = [
  {label: '0-100', min: 0, max: 100},
  {label: '101-200', min: 101, max: 200},
  {label: '201-300', min: 201, max: 300},
  {label: '301-400', min: 301, max: 400},
  {label: '401-500', min: 401, max: 500},
  {label: '501+', min: 501, max: Infinity}
];

@Component({
  selector: 'app-book-length-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, Tooltip, TranslocoDirective],
  templateUrl: './book-length-chart.component.html',
  styleUrls: ['./book-length-chart.component.scss']
})
export class BookLengthChartComponent {
  private readonly bookService = inject(BookService);
  private readonly t = inject(TranslocoService);
  private readonly metrics = computed<BookLengthMetrics>(() => {
    if (this.bookService.isBooksLoading()) {
      return this.emptyMetrics();
    }

    return this.calculateMetrics(this.bookService.books());
  });

  public readonly chartType = 'scatter' as const;
  public readonly sweetSpot = computed(() => this.metrics().sweetSpot);
  public readonly highestRatedLength = computed(() => this.metrics().highestRatedLength);
  public readonly totalRatedBooks = computed(() => this.metrics().totalRatedBooks);

  public readonly chartOptions: ChartConfiguration<'scatter'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    layout: {
      padding: {top: 10, right: 20, bottom: 10, left: 10}
    },
    scales: {
      x: {
        title: {
          display: true,
          text: this.t.translate('statsUser.bookLength.axisPageCount'),
          color: '#ffffff',
          font: {family: "'Inter', sans-serif", size: 12, weight: 'bold'}
        },
        ticks: {
          color: 'rgba(255, 255, 255, 0.8)',
          font: {family: "'Inter', sans-serif", size: 11}
        },
        grid: {color: 'rgba(255, 255, 255, 0.1)'}
      },
      y: {
        min: 0,
        max: 10,
        title: {
          display: true,
          text: this.t.translate('statsUser.bookLength.axisPersonalRating'),
          color: '#ffffff',
          font: {family: "'Inter', sans-serif", size: 12, weight: 'bold'}
        },
        ticks: {
          color: 'rgba(255, 255, 255, 0.8)',
          stepSize: 1,
          font: {family: "'Inter', sans-serif", size: 11}
        },
        grid: {color: 'rgba(255, 255, 255, 0.1)'}
      }
    },
    plugins: {
      legend: {
        display: true,
        position: 'top',
        labels: {
          color: '#ffffff',
          font: {family: "'Inter', sans-serif", size: 11},
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
        borderColor: '#00bcd4',
        borderWidth: 2,
        cornerRadius: 8,
        padding: 12,
        titleFont: {size: 13, weight: 'bold'},
        bodyFont: {size: 11},
        callbacks: {
          title: (context) => {
            const point = context[0].raw as BookScatterPoint;
            return point.bookTitle || this.t.translate('statsUser.bookLength.tooltipUnknownBook');
          },
          label: (context) => {
            const point = context.raw as BookScatterPoint;
            return [
              this.t.translate('statsUser.bookLength.tooltipPages', {count: point.x}),
              this.t.translate('statsUser.bookLength.tooltipRating', {rating: point.y}),
              this.t.translate('statsUser.bookLength.tooltipStatus', {status: point.readStatus})
            ];
          }
        }
      },
      datalabels: {display: false}
    },
    elements: {
      point: {
        radius: 6,
        hoverRadius: 9,
        borderWidth: 2
      }
    }
  };

  public readonly chartData = computed(() => this.metrics().chartData);

  private calculateMetrics(books: Book[]): BookLengthMetrics {
    if (books.length === 0) {
      return this.emptyMetrics();
    }

    const ratedBooks = books.filter(b =>
      b.personalRating != null && b.personalRating > 0 &&
      b.metadata?.pageCount != null && b.metadata.pageCount > 0
    );

    const totalRatedBooks = ratedBooks.length;
    if (totalRatedBooks === 0) {
      return this.emptyMetrics();
    }

    const grouped = new Map<string, { label: string; points: BookScatterPoint[] }>();
    for (const book of ratedBooks) {
      const statusKey = this.getStatusKey(book.readStatus);
      const statusLabel = this.getStatusLabel(book.readStatus);
      if (!grouped.has(statusKey)) grouped.set(statusKey, {label: statusLabel, points: []});
      grouped.get(statusKey)!.points.push({
        x: book.metadata!.pageCount!,
        y: book.personalRating!,
        bookTitle: book.metadata?.title || book.fileName || 'Unknown',
        readStatus: statusLabel
      });
    }

    const datasets = Array.from(grouped.entries()).map(([key, {label, points}]) => {
      const colors = STATUS_COLORS[key] || STATUS_COLORS['other'];
      return {
        label: `${label} (${points.length})`,
        data: points,
        backgroundColor: colors.bg,
        borderColor: colors.border,
        pointRadius: 6,
        pointHoverRadius: 9,
        pointBorderWidth: 2
      };
    });

    // Add trend line
    const allPoints = ratedBooks.map(b => ({x: b.metadata!.pageCount!, y: b.personalRating!}));
    const trend = this.computeTrendLine(allPoints);
    if (trend) {
      datasets.push({
        label: this.t.translate('statsUser.bookLength.trend'),
        data: trend as BookScatterPoint[],
        backgroundColor: 'transparent',
        borderColor: 'rgba(255, 255, 255, 0.4)',
        pointRadius: 0,
        pointHoverRadius: 0,
        pointBorderWidth: 0
      });
    }

    const {sweetSpot, highestRatedLength} = this.computeStats(ratedBooks);
    return {
      totalRatedBooks,
      sweetSpot,
      highestRatedLength,
      chartData: {datasets}
    };
  }

  private getStatusKey(status?: ReadStatus): string {
    if (!status) return 'other';
    switch (status) {
      case ReadStatus.READ:
      case ReadStatus.PARTIALLY_READ:
        return 'read';
      case ReadStatus.READING:
      case ReadStatus.RE_READING:
        return 'reading';
      case ReadStatus.ABANDONED:
      case ReadStatus.WONT_READ:
        return 'abandoned';
      default:
        return 'other';
    }
  }

  private getStatusLabel(status?: ReadStatus): string {
    if (!status) return this.t.translate('statsUser.bookLength.statusOther');
    switch (status) {
      case ReadStatus.READ:
      case ReadStatus.PARTIALLY_READ:
        return this.t.translate('statsUser.bookLength.statusRead');
      case ReadStatus.READING:
      case ReadStatus.RE_READING:
        return this.t.translate('statsUser.bookLength.statusReading');
      case ReadStatus.ABANDONED:
      case ReadStatus.WONT_READ:
        return this.t.translate('statsUser.bookLength.statusAbandoned');
      default:
        return this.t.translate('statsUser.bookLength.statusOther');
    }
  }

  private computeStats(books: Book[]): Pick<BookLengthMetrics, 'sweetSpot' | 'highestRatedLength'> {
    let bestRange = '';
    let bestAvg = 0;

    for (const range of PAGE_RANGES) {
      const rangeBooks = books.filter(b =>
        b.metadata!.pageCount! >= range.min && b.metadata!.pageCount! <= range.max
      );
      if (rangeBooks.length >= 2) {
        const avg = rangeBooks.reduce((s, b) => s + b.personalRating!, 0) / rangeBooks.length;
        if (avg > bestAvg) {
          bestAvg = avg;
          bestRange = range.label;
        }
      }
    }

    const sweetSpot = bestRange ? `${bestRange} pages (avg ${bestAvg.toFixed(1)})` : '-';
    const highestRated = books.reduce((a, b) => (a.personalRating! >= b.personalRating! ? a : b));
    const highestRatedLength = `${highestRated.metadata!.pageCount} pages`;

    return {sweetSpot, highestRatedLength};
  }

  private computeTrendLine(points: { x: number; y: number }[]): { x: number; y: number }[] | null {
    if (points.length < 2) return null;

    const n = points.length;
    let sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
    for (const p of points) {
      sumX += p.x;
      sumY += p.y;
      sumXY += p.x * p.y;
      sumX2 += p.x * p.x;
    }

    const denominator = n * sumX2 - sumX * sumX;
    if (denominator === 0) return null;

    const slope = (n * sumXY - sumX * sumY) / denominator;
    const intercept = (sumY - slope * sumX) / n;

    const minX = Math.min(...points.map(p => p.x));
    const maxX = Math.max(...points.map(p => p.x));

    return [
      {x: minX, y: Math.max(0, Math.min(10, slope * minX + intercept))},
      {x: maxX, y: Math.max(0, Math.min(10, slope * maxX + intercept))}
    ];
  }

  private emptyMetrics(): BookLengthMetrics {
    return {
      totalRatedBooks: 0,
      sweetSpot: '',
      highestRatedLength: '',
      chartData: {datasets: []}
    };
  }
}
