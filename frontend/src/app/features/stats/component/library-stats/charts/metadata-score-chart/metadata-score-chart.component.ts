import {Component, computed, inject} from '@angular/core';
import {BaseChartDirective} from 'ng2-charts';
import {ChartConfiguration, ChartData} from 'chart.js';
import {LibraryFilterService} from '../../service/library-filter.service';
import {BookService} from '../../../../../book/service/book.service';
import {Book} from '../../../../../book/model/book.model';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

interface ScoreStats {
  range: string;
  count: number;
  percentage: number;
  color: string;
}

type ScoreChartData = ChartData<'doughnut', number[], string>;
type ScoreRangeKey = 'excellent' | 'good' | 'fair' | 'poor' | 'veryPoor';

const SCORE_RANGE_DEFS: { key: ScoreRangeKey; min: number; max: number; color: string }[] = [
  {key: 'excellent', min: 90, max: 100, color: '#16A34A'},
  {key: 'good', min: 70, max: 89, color: '#22C55E'},
  {key: 'fair', min: 50, max: 69, color: '#F59E0B'},
  {key: 'poor', min: 25, max: 49, color: '#F97316'},
  {key: 'veryPoor', min: 0, max: 24, color: '#DC2626'}
];

@Component({
  selector: 'app-metadata-score-chart',
  standalone: true,
  imports: [BaseChartDirective, TranslocoDirective],
  templateUrl: './metadata-score-chart.component.html',
  styleUrls: ['./metadata-score-chart.component.scss']
})
export class MetadataScoreChartComponent {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly t = inject(TranslocoService);
  private readonly booksWithScore = computed(() => {
    if (this.bookService.isBooksLoading()) {
      return [];
    }

    const filteredBooks = this.filterBooksByLibrary(this.bookService.books(), this.libraryFilterService.selectedLibrary());
    return filteredBooks.filter(b => b.metadataMatchScore != null && b.metadataMatchScore >= 0);
  });

  public readonly chartType = 'doughnut' as const;
  public readonly scoreStats = computed(() => this.calculateScoreStats(this.booksWithScore()));
  public readonly totalBooks = computed(() => this.booksWithScore().length);
  public readonly averageScore = computed(() => this.calculateAverageScore(this.booksWithScore()));

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
            return this.t.translate('statsLibrary.metadataScore.tooltipLabel', {value, percentage});
          }
        }
      },
      datalabels: {
        display: false
      }
    }
  };

  public readonly chartData = computed<ScoreChartData>(() => {
    const stats = this.scoreStats();
    if (stats.length === 0) {
      return {labels: [], datasets: []};
    }

    const labels = stats.map(s => s.range);
    const data = stats.map(s => s.count);
    const colors = stats.map(s => s.color);

    return {
      labels,
      datasets: [{
        data,
        backgroundColor: colors,
        borderColor: colors.map(() => 'rgba(255, 255, 255, 0.2)'),
        borderWidth: 2,
        hoverBorderColor: '#ffffff',
        hoverBorderWidth: 3
      }]
    };
  });

  private filterBooksByLibrary(books: Book[], selectedLibraryId: number | null): Book[] {
    return selectedLibraryId
      ? books.filter(book => book.libraryId === selectedLibraryId)
      : books;
  }

  private calculateScoreStats(books: Book[]): ScoreStats[] {
    const rangeCounts = new Map<string, { count: number, color: string }>();

    SCORE_RANGE_DEFS.forEach(range => {
      rangeCounts.set(range.key, {count: 0, color: range.color});
    });

    books.forEach(book => {
      const score = book.metadataMatchScore!;
      for (const range of SCORE_RANGE_DEFS) {
        if (score >= range.min && score <= range.max) {
          const data = rangeCounts.get(range.key)!;
          data.count++;
          break;
        }
      }
    });

    const total = books.length;
    return SCORE_RANGE_DEFS
      .map(range => {
        const data = rangeCounts.get(range.key)!;
        return {
          range: this.t.translate(`statsLibrary.metadataScore.${range.key}`),
          count: data.count,
          percentage: (data.count / total) * 100,
          color: data.color
        };
      })
      .filter(stat => stat.count > 0);
  }

  private calculateAverageScore(books: Book[]): number {
    if (books.length === 0) {
      return 0;
    }

    const total = books.reduce((sum, book) => sum + (book.metadataMatchScore || 0), 0);
    return Math.round(total / books.length);
  }
}
