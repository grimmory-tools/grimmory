import {Component, computed, inject} from '@angular/core';
import {BaseChartDirective} from 'ng2-charts';
import {ChartConfiguration, ChartData} from 'chart.js';
import {LibraryFilterService} from '../../service/library-filter.service';
import {BookService} from '../../../../../book/service/book.service';
import {Book} from '../../../../../book/model/book.model';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

interface FormatStats {
  format: string;
  count: number;
  percentage: number;
}

type FormatChartData = ChartData<'pie', number[], string>;

const FORMAT_COLORS: Record<string, string> = {
  'PDF': '#E11D48',    // Rose
  'EPUB': '#0D9488',   // Teal
  'CBX': '#7C3AED',    // Violet
  'FB2': '#F59E0B',    // Amber
  'MOBI': '#2563EB',   // Blue
  'AZW3': '#16A34A'    // Green
};

@Component({
  selector: 'app-book-formats-chart',
  standalone: true,
  imports: [BaseChartDirective, TranslocoDirective],
  templateUrl: './book-formats-chart.component.html',
  styleUrls: ['./book-formats-chart.component.scss']
})
export class BookFormatsChartComponent {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly t = inject(TranslocoService);
  private readonly filteredBooks = computed(() => {
    if (this.bookService.isBooksLoading()) {
      return [];
    }

    return this.filterBooksByLibrary(this.bookService.books(), this.libraryFilterService.selectedLibrary());
  });

  public readonly chartType = 'pie' as const;
  public readonly formatStats = computed(() => this.calculateFormatStats(this.filteredBooks()));
  public readonly totalBooks = computed(() => this.filteredBooks().length);

  public readonly chartOptions: ChartConfiguration<'pie'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
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
            size: 12
          },
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
        borderColor: '#E11D48',
        borderWidth: 2,
        cornerRadius: 8,
        padding: 12,
        titleFont: {size: 14, weight: 'bold'},
        bodyFont: {size: 12},
        callbacks: {
          label: (context) => {
            const value = context.parsed;
            const total = context.dataset.data.reduce((a: number, b: number) => a + b, 0);
            const percentage = ((value / total) * 100).toFixed(1);
            return this.t.translate('statsLibrary.bookFormats.tooltipLabel', {label: context.label, value, percentage});
          }
        }
      },
      datalabels: {
        display: false
      }
    }
  };

  public readonly chartData = computed<FormatChartData>(() => {
    const stats = this.formatStats();
    if (stats.length === 0) {
      return {labels: [], datasets: []};
    }

    const labels = stats.map(s => s.format);
    const data = stats.map(s => s.count);
    const colors = stats.map(s => FORMAT_COLORS[s.format] || '#6B7280');

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

  private calculateFormatStats(books: Book[]): FormatStats[] {
    const formatCounts = new Map<string, number>();

    books.forEach(book => {
      const format = book.primaryFile?.bookType || 'Unknown';
      formatCounts.set(format, (formatCounts.get(format) || 0) + 1);
    });

    const total = books.length;
    return Array.from(formatCounts.entries())
      .map(([format, count]) => ({
        format,
        count,
        percentage: (count / total) * 100
      }))
      .sort((a, b) => b.count - a.count);
  }
}
