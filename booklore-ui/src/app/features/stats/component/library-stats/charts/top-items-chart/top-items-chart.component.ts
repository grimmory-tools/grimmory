import {Component, inject, Input, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {BaseChartDirective} from 'ng2-charts';
import {ChartConfiguration, ChartData, TooltipItem} from 'chart.js';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, switchMap, takeUntil} from 'rxjs/operators';
import {Select} from 'primeng/select';
import {LibraryFilterService} from '../../service/library-filter.service';
import {BookService} from '../../../../../book/service/book.service';
import {Book, ReadStatus} from '../../../../../book/model/book.model';
import {BookState} from '../../../../../book/model/state/book-state.model';

interface ItemStats {
  name: string;
  count: number;
  statusBreakdown: Record<ReadStatus, number>;
}

interface DataTypeOption {
  label: string;
  value: DataType;
  icon: string;
  color: string;
}

type DataType = 'authors' | 'categories' | 'publishers' | 'tags' | 'moods' | 'series';
type ItemChartData = ChartData<'bar', number[], string>;

const DATA_TYPE_OPTIONS: DataTypeOption[] = [
  {label: 'Authors', value: 'authors', icon: 'pi-th-large', color: '#2563EB'},
  {label: 'Categories', value: 'categories', icon: 'pi-user', color: '#0D9488'},
  {label: 'Series', value: 'series', icon: 'pi-tag', color: '#DB2777'},
  {label: 'Publishers', value: 'publishers', icon: 'pi-building', color: '#7C3AED'},
  {label: 'Tags', value: 'tags', icon: 'pi-bookmark', color: '#EAB308'},
  {label: 'Moods', value: 'moods', icon: 'pi-heart', color: '#EA580C'}
];

const READ_STATUS_CONFIG: Record<ReadStatus, { label: string; color: string }> = {
  [ReadStatus.READ]: {label: 'Read', color: '#22c55e'},
  [ReadStatus.READING]: {label: 'Reading', color: '#3b82f6'},
  [ReadStatus.RE_READING]: {label: 'Re-reading', color: '#8b5cf6'},
  [ReadStatus.UNREAD]: {label: 'Unread', color: '#6b7280'},
  [ReadStatus.PARTIALLY_READ]: {label: 'Partially Read', color: '#f59e0b'},
  [ReadStatus.PAUSED]: {label: 'Paused', color: '#eab308'},
  [ReadStatus.WONT_READ]: {label: "Won't Read", color: '#ef4444'},
  [ReadStatus.ABANDONED]: {label: 'Abandoned', color: '#dc2626'},
  [ReadStatus.UNSET]: {label: 'Not Set', color: '#9ca3af'}
};

const READ_STATUS_ORDER: ReadStatus[] = [
  ReadStatus.READ,
  ReadStatus.READING,
  ReadStatus.RE_READING,
  ReadStatus.PARTIALLY_READ,
  ReadStatus.PAUSED,
  ReadStatus.UNREAD,
  ReadStatus.WONT_READ,
  ReadStatus.ABANDONED,
  ReadStatus.UNSET
];

@Component({
  selector: 'app-top-items-chart',
  standalone: true,
  imports: [CommonModule, FormsModule, BaseChartDirective, Select],
  templateUrl: './top-items-chart.component.html',
  styleUrls: ['./top-items-chart.component.scss']
})
export class TopItemsChartComponent implements OnInit, OnDestroy {
  @Input() initialDataType: DataType | null = null;

  public readonly chartType = 'bar' as const;
  public readonly chartData$: Observable<ItemChartData>;
  public chartOptions: ChartConfiguration<'bar'>['options'];
  public dataTypeOptions = DATA_TYPE_OPTIONS;
  public selectedDataType: DataTypeOption = DATA_TYPE_OPTIONS[0];

  public totalItems = 0;
  public totalBooks = 0;
  public insights: { icon: string; label: string; value: string }[] = [];

  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();
  private readonly chartDataSubject: BehaviorSubject<ItemChartData>;
  private lastCalculatedStats: ItemStats[] = [];
  private allBooks: Book[] = [];

  constructor() {
    this.chartDataSubject = new BehaviorSubject<ItemChartData>({
      labels: [],
      datasets: []
    });
    this.chartData$ = this.chartDataSubject.asObservable();
    this.initChartOptions();
  }

  ngOnInit(): void {
    if (this.initialDataType) {
      const initialOption = DATA_TYPE_OPTIONS.find(opt => opt.value === this.initialDataType);
      if (initialOption) {
        this.selectedDataType = initialOption;
        this.initChartOptions();
      }
    }

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
          console.error('Error processing top items stats:', error);
          return EMPTY;
        })
      )
      .subscribe(() => {
        this.loadAndProcessData();
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onDataTypeChange(): void {
    this.initChartOptions();
    this.processData();
  }

  private initChartOptions(): void {
    const currentColor = this.selectedDataType.color;
    this.chartOptions = {
      responsive: true,
      maintainAspectRatio: false,
      indexAxis: 'y',
      layout: {
        padding: {top: 10, right: 20, bottom: 10, left: 10}
      },
      scales: {
        x: {
          stacked: true,
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
          stacked: true,
          ticks: {
            color: 'rgba(255, 255, 255, 0.9)',
            font: {
              family: "'Inter', sans-serif",
              size: 11
            },
            maxTicksLimit: 25
          },
          grid: {
            display: false
          },
          border: {display: false}
        }
      },
      plugins: {
        legend: {
          display: true,
          position: 'bottom',
          labels: {
            color: 'rgba(255, 255, 255, 0.9)',
            font: {
              family: "'Inter', sans-serif",
              size: 11
            },
            padding: 15,
            usePointStyle: true,
            pointStyle: 'rectRounded'
          }
        },
        tooltip: {
          enabled: true,
          backgroundColor: 'rgba(0, 0, 0, 0.95)',
          titleColor: '#ffffff',
          bodyColor: '#ffffff',
          borderColor: currentColor,
          borderWidth: 2,
          cornerRadius: 8,
          displayColors: true,
          padding: 12,
          titleFont: {size: 14, weight: 'bold'},
          bodyFont: {size: 12},
          callbacks: {
            title: (context) => {
              const dataIndex = context[0].dataIndex;
              return this.lastCalculatedStats[dataIndex]?.name || 'Unknown';
            },
            label: this.formatTooltipLabel.bind(this)
          }
        },
        datalabels: {
          display: false
        }
      },
      interaction: {
        intersect: true,
        mode: 'nearest',
        axis: 'y'
      }
    };
  }

  private loadAndProcessData(): void {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      this.allBooks = [];
      this.updateChartData([]);
      return;
    }

    this.allBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    this.processData();
  }

  private processData(): void {
    const stats = this.calculateStats(this.allBooks);
    this.updateChartData(stats);
  }

  private updateChartData(stats: ItemStats[]): void {
    try {
      this.lastCalculatedStats = stats;
      this.totalItems = stats.length;
      this.totalBooks = stats.reduce((sum, s) => sum + s.count, 0);

      const labels = stats.map(s => this.truncateTitle(s.name, 30));

      const datasets = READ_STATUS_ORDER
        .filter(status => stats.some(s => s.statusBreakdown[status] > 0))
        .map(status => {
          const config = READ_STATUS_CONFIG[status];
          return {
            label: config.label,
            data: stats.map(s => s.statusBreakdown[status]),
            backgroundColor: config.color,
            borderColor: config.color,
            borderWidth: 1,
            borderRadius: 4,
            barPercentage: 0.85,
            categoryPercentage: 0.8,
            hoverBorderWidth: 2,
            hoverBorderColor: '#ffffff'
          };
        });

      this.chartDataSubject.next({
        labels,
        datasets
      });

      this.generateInsights(stats);
    } catch (error) {
      console.error('Error updating items chart data:', error);
    }
  }

  private generateInsights(stats: ItemStats[]): void {
    this.insights = [];
    if (stats.length === 0) return;

    const typeName = this.selectedDataType.label.toLowerCase().slice(0, -1); // Remove 's' for singular
    const typeNamePlural = this.selectedDataType.label.toLowerCase();

    // 1. Top item
    const top = stats[0];
    this.insights.push({
      icon: 'pi-trophy',
      label: `Top ${typeName}`,
      value: `${top.name} (${top.count} books)`
    });

    // 2. Most completed - highest read percentage
    const withReads = stats.filter(s => s.count >= 2);
    if (withReads.length > 0) {
      const mostRead = withReads.reduce((best, curr) => {
        const bestPct = best.statusBreakdown[ReadStatus.READ] / best.count;
        const currPct = curr.statusBreakdown[ReadStatus.READ] / curr.count;
        return currPct > bestPct ? curr : best;
      });
      const readPct = Math.round((mostRead.statusBreakdown[ReadStatus.READ] / mostRead.count) * 100);
      if (readPct > 0) {
        this.insights.push({
          icon: 'pi-check-circle',
          label: 'Most completed',
          value: `${mostRead.name} (${readPct}% read)`
        });
      }
    }

    // 3. Top 5 concentration
    if (stats.length >= 5) {
      const top5Books = stats.slice(0, 5).reduce((sum, s) => sum + s.count, 0);
      const totalAllBooks = this.allBooks.length;
      if (totalAllBooks > 0) {
        const concentration = Math.round((top5Books / totalAllBooks) * 100);
        this.insights.push({
          icon: 'pi-chart-pie',
          label: 'Top 5 coverage',
          value: `${concentration}% of library`
        });
      }
    }

    // 4. Average books per item
    if (stats.length > 0) {
      const avgBooks = (this.totalBooks / stats.length).toFixed(1);
      this.insights.push({
        icon: 'pi-book',
        label: `Avg per ${typeName}`,
        value: `${avgBooks} books`
      });
    }
  }

  private calculateStats(books: Book[]): ItemStats[] {
    if (books.length === 0) {
      return [];
    }

    const itemMap = new Map<string, { count: number; statusBreakdown: Record<ReadStatus, number> }>();
    const dataType = this.selectedDataType.value;

    for (const book of books) {
      const items = this.getItemsFromBook(book, dataType);
      const bookStatus = book.readStatus || ReadStatus.UNSET;

      for (const item of items) {
        if (item && item.trim()) {
          const normalizedName = item.trim();
          let entry = itemMap.get(normalizedName);

          if (!entry) {
            entry = {
              count: 0,
              statusBreakdown: this.createEmptyStatusBreakdown()
            };
            itemMap.set(normalizedName, entry);
          }

          entry.count++;
          entry.statusBreakdown[bookStatus]++;
        }
      }
    }

    return Array.from(itemMap.entries())
      .map(([name, data]) => ({name, count: data.count, statusBreakdown: data.statusBreakdown}))
      .sort((a, b) => b.count - a.count)
      .slice(0, 15);
  }

  private createEmptyStatusBreakdown(): Record<ReadStatus, number> {
    return {
      [ReadStatus.READ]: 0,
      [ReadStatus.READING]: 0,
      [ReadStatus.RE_READING]: 0,
      [ReadStatus.UNREAD]: 0,
      [ReadStatus.PARTIALLY_READ]: 0,
      [ReadStatus.PAUSED]: 0,
      [ReadStatus.WONT_READ]: 0,
      [ReadStatus.ABANDONED]: 0,
      [ReadStatus.UNSET]: 0
    };
  }

  private getItemsFromBook(book: Book, dataType: DataType): string[] {
    const metadata = book.metadata;
    if (!metadata) return [];

    switch (dataType) {
      case 'authors':
        return metadata.authors || [];
      case 'categories':
        return metadata.categories || [];
      case 'publishers':
        return metadata.publisher ? [metadata.publisher] : [];
      case 'tags':
        return metadata.tags || [];
      case 'moods':
        return metadata.moods || [];
      case 'series':
        return metadata.seriesName ? [metadata.seriesName] : [];
      default:
        return [];
    }
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

  private formatTooltipLabel(context: TooltipItem<'bar'>): string {
    const value = context.parsed.x;
    if (value === 0) {
      return '';
    }

    const statusLabel = context.dataset.label || 'Books';
    const bookText = value === 1 ? 'book' : 'books';

    return `${statusLabel}: ${value} ${bookText}`;
  }

  private truncateTitle(title: string, maxLength: number): string {
    return title.length > maxLength ? title.substring(0, maxLength) + '...' : title;
  }
}
