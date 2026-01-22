import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, switchMap, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BookService} from '../../../../../book/service/book.service';
import {BookState} from '../../../../../book/model/state/book-state.model';
import {Book, ReadStatus} from '../../../../../book/model/book.model';
import {LibraryFilterService} from '../../../library-stats/service/library-filter.service';

interface SeriesInfo {
  name: string;
  booksOwned: number;
  booksRead: number;
  booksReading: number;
  booksPartiallyRead: number;
  booksPaused: number;
  booksAbandoned: number;
  booksWontRead: number;
  booksUnread: number;
  totalInSeries: number | null;
  completionPercentage: number;
  avgPersonalRating: number | null;
  avgExternalRating: number | null;
  nextUnread: string | null;
  status: 'completed' | 'in-progress' | 'not-started' | 'abandoned' | 'paused';
}

interface SeriesStats {
  totalSeries: number;
  completedSeries: number;
  inProgressSeries: number;
  notStartedSeries: number;
  avgSeriesCompletion: number;
  mostReadSeries: SeriesInfo | null;
  highestRatedSeries: SeriesInfo | null;
}

type SeriesChartData = ChartData<'bar', number[], string>;

@Component({
  selector: 'app-series-progress-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './series-progress-chart.component.html',
  styleUrls: ['./series-progress-chart.component.scss']
})
export class SeriesProgressChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'bar' as const;
  public seriesList: SeriesInfo[] = [];
  public filteredSeriesList: SeriesInfo[] = [];
  public stats: SeriesStats | null = null;
  public displayedSeries: SeriesInfo[] = [];
  public chartSeries: SeriesInfo[] = [];

  // Pagination & filtering
  public searchTerm = '';
  public currentPage = 0;
  public readonly PAGE_SIZE = 10;
  public readonly CHART_DISPLAY_COUNT = 8;
  public sortBy: 'progress' | 'rating' | 'books' | 'name' = 'progress';
  public filterStatus: 'all' | 'completed' | 'in-progress' | 'not-started' | 'paused' | 'abandoned' = 'all';

  public readonly chartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    indexAxis: 'y',
    layout: {
      padding: {top: 10, right: 20, bottom: 10, left: 10}
    },
    scales: {
      x: {
        stacked: true,
        max: 100,
        title: {
          display: true,
          text: 'Completion %',
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 11,
            weight: 500
          }
        },
        ticks: {
          color: 'rgba(255, 255, 255, 0.8)',
          font: {
            family: "'Inter', sans-serif",
            size: 10
          },
          callback: (value) => `${value}%`
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.1)'
        }
      },
      y: {
        stacked: true,
        ticks: {
          color: 'rgba(255, 255, 255, 0.8)',
          font: {
            family: "'Inter', sans-serif",
            size: 10
          }
        },
        grid: {
          display: false
        }
      }
    },
    plugins: {
      legend: {
        display: true,
        position: 'top',
        labels: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 10
          },
          usePointStyle: true,
          pointStyle: 'rect',
          padding: 12
        }
      },
      tooltip: {
        enabled: true,
        backgroundColor: 'rgba(0, 0, 0, 0.95)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: '#673ab7',
        borderWidth: 2,
        cornerRadius: 8,
        padding: 12,
        titleFont: {size: 12, weight: 'bold'},
        bodyFont: {size: 10},
        callbacks: {
          title: (context) => {
            const series = this.chartSeries[context[0].dataIndex];
            return series ? series.name : '';
          },
          afterBody: (context) => {
            const series = this.chartSeries[context[0].dataIndex];
            if (!series) return [];
            const lines = [
              `Read: ${series.booksRead}/${series.booksOwned} owned`
            ];
            if (series.totalInSeries) {
              lines.push(`Series total: ${series.totalInSeries} books`);
            }
            if (series.avgPersonalRating) {
              lines.push(`Your rating: ${series.avgPersonalRating.toFixed(1)}/10`);
            }
            return lines;
          }
        }
      }
    }
  };

  private readonly chartDataSubject = new BehaviorSubject<SeriesChartData>({
    labels: [],
    datasets: []
  });

  public readonly chartData$: Observable<SeriesChartData> = this.chartDataSubject.asObservable();

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
          console.error('Error processing series data:', error);
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

  onSearchChange(term: string): void {
    this.searchTerm = term.toLowerCase().trim();
    this.currentPage = 0;
    this.applyFiltersAndSort();
  }

  onSortChange(sortBy: 'progress' | 'rating' | 'books' | 'name'): void {
    this.sortBy = sortBy;
    this.currentPage = 0;
    this.applyFiltersAndSort();
  }

  onFilterChange(status: 'all' | 'completed' | 'in-progress' | 'not-started' | 'paused' | 'abandoned'): void {
    this.filterStatus = status;
    this.currentPage = 0;
    this.applyFiltersAndSort();
  }

  nextPage(): void {
    if (this.hasNextPage()) {
      this.currentPage++;
      this.updateDisplayedSeries();
    }
  }

  prevPage(): void {
    if (this.hasPrevPage()) {
      this.currentPage--;
      this.updateDisplayedSeries();
    }
  }

  hasNextPage(): boolean {
    return (this.currentPage + 1) * this.PAGE_SIZE < this.filteredSeriesList.length;
  }

  hasPrevPage(): boolean {
    return this.currentPage > 0;
  }

  getTotalPages(): number {
    return Math.ceil(this.filteredSeriesList.length / this.PAGE_SIZE);
  }

  private applyFiltersAndSort(): void {
    let filtered = [...this.seriesList];

    // Apply search filter
    if (this.searchTerm) {
      filtered = filtered.filter(s =>
        s.name.toLowerCase().includes(this.searchTerm)
      );
    }

    // Apply status filter
    if (this.filterStatus !== 'all') {
      filtered = filtered.filter(s => s.status === this.filterStatus);
    }

    // Apply sorting
    filtered.sort((a, b) => {
      switch (this.sortBy) {
        case 'progress':
          return b.completionPercentage - a.completionPercentage;
        case 'rating':
          return (b.avgPersonalRating || 0) - (a.avgPersonalRating || 0);
        case 'books':
          return b.booksOwned - a.booksOwned;
        case 'name':
          return a.name.localeCompare(b.name);
        default:
          return 0;
      }
    });

    this.filteredSeriesList = filtered;
    this.updateDisplayedSeries();
    this.updateChartSeries();
    this.updateChartData();
  }

  private calculateAndUpdateChart(): void {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      this.chartDataSubject.next({labels: [], datasets: []});
      this.seriesList = [];
      this.displayedSeries = [];
      this.stats = null;
      return;
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    const seriesBooks = this.getBooksInSeries(filteredBooks);

    if (seriesBooks.length === 0) {
      this.chartDataSubject.next({labels: [], datasets: []});
      this.seriesList = [];
      this.filteredSeriesList = [];
      this.displayedSeries = [];
      this.chartSeries = [];
      this.stats = null;
      return;
    }

    this.seriesList = this.calculateSeriesInfo(seriesBooks);
    this.stats = this.calculateSeriesStats(this.seriesList);
    this.currentPage = 0;
    this.applyFiltersAndSort();
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

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | number | null): Book[] {
    return selectedLibraryId
      ? books.filter(book => book.libraryId === selectedLibraryId)
      : books;
  }

  private getBooksInSeries(books: Book[]): Book[] {
    return books.filter(book => book.metadata?.seriesName);
  }

  private calculateSeriesInfo(books: Book[]): SeriesInfo[] {
    const seriesMap = new Map<string, Book[]>();

    books.forEach(book => {
      const seriesName = book.metadata!.seriesName!;
      if (!seriesMap.has(seriesName)) {
        seriesMap.set(seriesName, []);
      }
      seriesMap.get(seriesName)!.push(book);
    });

    const seriesInfoList: SeriesInfo[] = [];

    seriesMap.forEach((seriesBooks, seriesName) => {
      const booksOwned = seriesBooks.length;
      let booksRead = 0;
      let booksReading = 0;
      let booksPartiallyRead = 0;
      let booksPaused = 0;
      let booksAbandoned = 0;
      let booksWontRead = 0;
      let booksUnread = 0;
      let totalRating = 0;
      let ratedCount = 0;
      let totalExternalRating = 0;
      let externalRatedCount = 0;
      let totalInSeries: number | null = null;
      let nextUnread: string | null = null;

      // Sort by series number for finding next unread
      const sortedBooks = [...seriesBooks].sort((a, b) => {
        const numA = a.metadata?.seriesNumber || 999;
        const numB = b.metadata?.seriesNumber || 999;
        return numA - numB;
      });

      sortedBooks.forEach(book => {
        // Track series total
        if (book.metadata?.seriesTotal && (!totalInSeries || book.metadata.seriesTotal > totalInSeries)) {
          totalInSeries = book.metadata.seriesTotal;
        }

        // Count by status - handle ALL ReadStatus values
        switch (book.readStatus) {
          case ReadStatus.READ:
            booksRead++;
            break;
          case ReadStatus.READING:
          case ReadStatus.RE_READING:
            booksReading++;
            break;
          case ReadStatus.PARTIALLY_READ:
            booksPartiallyRead++;
            if (!nextUnread) {
              nextUnread = book.metadata?.title || book.fileName || null;
            }
            break;
          case ReadStatus.PAUSED:
            booksPaused++;
            if (!nextUnread) {
              nextUnread = book.metadata?.title || book.fileName || null;
            }
            break;
          case ReadStatus.ABANDONED:
            booksAbandoned++;
            break;
          case ReadStatus.WONT_READ:
            booksWontRead++;
            break;
          case ReadStatus.UNREAD:
          case ReadStatus.UNSET:
          default:
            booksUnread++;
            if (!nextUnread) {
              nextUnread = book.metadata?.title || book.fileName || null;
            }
            break;
        }

        // Personal rating
        if (book.personalRating && book.personalRating > 0) {
          totalRating += book.personalRating;
          ratedCount++;
        }

        // External rating
        const extRating = this.getExternalRating(book);
        if (extRating > 0) {
          totalExternalRating += extRating;
          externalRatedCount++;
        }
      });

      // Calculate completion percentage based on what we own (excluding won't read/abandoned)
      const relevantBooks = booksOwned - booksWontRead - booksAbandoned;
      const completionPercentage = relevantBooks > 0 ? Math.round((booksRead / relevantBooks) * 100) : 0;

      // Determine series status based on comprehensive analysis
      let status: 'completed' | 'in-progress' | 'not-started' | 'abandoned' | 'paused';
      if (booksRead === relevantBooks && relevantBooks > 0) {
        status = 'completed';
      } else if (booksReading > 0) {
        status = 'in-progress';
      } else if (booksPaused > 0 && booksRead > 0) {
        status = 'paused';
      } else if (booksRead > 0 || booksPartiallyRead > 0) {
        status = 'in-progress';
      } else if ((booksAbandoned > 0 || booksWontRead > 0) && booksRead === 0 && booksReading === 0) {
        status = 'abandoned';
      } else {
        status = 'not-started';
      }

      seriesInfoList.push({
        name: seriesName,
        booksOwned,
        booksRead,
        booksReading,
        booksPartiallyRead,
        booksPaused,
        booksAbandoned,
        booksWontRead,
        booksUnread,
        totalInSeries,
        completionPercentage,
        avgPersonalRating: ratedCount > 0 ? totalRating / ratedCount : null,
        avgExternalRating: externalRatedCount > 0 ? totalExternalRating / externalRatedCount : null,
        nextUnread,
        status
      });
    });

    // Sort by: in-progress first, then by completion %, then by books owned
    return seriesInfoList.sort((a, b) => {
      // Priority: in-progress > not-started > completed > abandoned
      const statusOrder = {'in-progress': 0, 'paused': 1, 'not-started': 2, 'completed': 3, 'abandoned': 4};
      const statusDiff = statusOrder[a.status] - statusOrder[b.status];
      if (statusDiff !== 0) return statusDiff;

      // Then by books owned (more books = more interesting)
      return b.booksOwned - a.booksOwned;
    });
  }

  private getExternalRating(book: Book): number {
    const ratings: number[] = [];
    if (book.metadata?.goodreadsRating) ratings.push(book.metadata.goodreadsRating);
    if (book.metadata?.amazonRating) ratings.push(book.metadata.amazonRating);
    if (book.metadata?.hardcoverRating) ratings.push(book.metadata.hardcoverRating);

    if (ratings.length > 0) {
      return ratings.reduce((sum, r) => sum + r, 0) / ratings.length;
    }
    return 0;
  }

  private calculateSeriesStats(seriesList: SeriesInfo[]): SeriesStats {
    const completedSeries = seriesList.filter(s => s.status === 'completed').length;
    const inProgressSeries = seriesList.filter(s => s.status === 'in-progress').length;
    const notStartedSeries = seriesList.filter(s => s.status === 'not-started').length;

    const totalCompletion = seriesList.reduce((sum, s) => sum + s.completionPercentage, 0);
    const avgSeriesCompletion = seriesList.length > 0 ? Math.round(totalCompletion / seriesList.length) : 0;

    const mostReadSeries = [...seriesList].sort((a, b) => b.booksRead - a.booksRead)[0] || null;

    const ratedSeries = seriesList.filter(s => s.avgPersonalRating !== null);
    const highestRatedSeries = ratedSeries.length > 0
      ? [...ratedSeries].sort((a, b) => (b.avgPersonalRating || 0) - (a.avgPersonalRating || 0))[0]
      : null;

    return {
      totalSeries: seriesList.length,
      completedSeries,
      inProgressSeries,
      notStartedSeries,
      avgSeriesCompletion,
      mostReadSeries,
      highestRatedSeries
    };
  }

  private updateDisplayedSeries(): void {
    const start = this.currentPage * this.PAGE_SIZE;
    const end = start + this.PAGE_SIZE;
    this.displayedSeries = this.filteredSeriesList.slice(start, end);
  }

  private updateChartSeries(): void {
    // For chart, show top series by in-progress first, then by books owned
    const chartData = [...this.seriesList]
      .sort((a, b) => {
        const statusOrder = {'in-progress': 0, 'paused': 1, 'not-started': 2, 'completed': 3, 'abandoned': 4};
        const statusDiff = statusOrder[a.status] - statusOrder[b.status];
        if (statusDiff !== 0) return statusDiff;
        return b.booksOwned - a.booksOwned;
      })
      .slice(0, this.CHART_DISPLAY_COUNT);

    this.chartSeries = chartData;
  }

  private updateChartData(): void {
    const labels = this.chartSeries.map(s =>
      s.name.length > 25 ? s.name.substring(0, 22) + '...' : s.name
    );

    const readPercentages = this.chartSeries.map(s =>
      s.booksOwned > 0 ? Math.round((s.booksRead / s.booksOwned) * 100) : 0
    );
    const readingPercentages = this.chartSeries.map(s =>
      s.booksOwned > 0 ? Math.round((s.booksReading / s.booksOwned) * 100) : 0
    );
    const partiallyReadPercentages = this.chartSeries.map(s =>
      s.booksOwned > 0 ? Math.round((s.booksPartiallyRead / s.booksOwned) * 100) : 0
    );
    const pausedPercentages = this.chartSeries.map(s =>
      s.booksOwned > 0 ? Math.round((s.booksPaused / s.booksOwned) * 100) : 0
    );
    const abandonedPercentages = this.chartSeries.map(s =>
      s.booksOwned > 0 ? Math.round((s.booksAbandoned / s.booksOwned) * 100) : 0
    );
    const wontReadPercentages = this.chartSeries.map(s =>
      s.booksOwned > 0 ? Math.round((s.booksWontRead / s.booksOwned) * 100) : 0
    );
    const unreadPercentages = this.chartSeries.map(s =>
      s.booksOwned > 0 ? Math.round((s.booksUnread / s.booksOwned) * 100) : 0
    );

    this.chartDataSubject.next({
      labels,
      datasets: [
        {
          label: 'Read',
          data: readPercentages,
          backgroundColor: 'rgba(76, 175, 80, 0.85)',
          borderColor: '#4caf50',
          borderWidth: 1,
          borderRadius: 2
        },
        {
          label: 'Reading',
          data: readingPercentages,
          backgroundColor: 'rgba(255, 193, 7, 0.85)',
          borderColor: '#ffc107',
          borderWidth: 1,
          borderRadius: 2
        },
        {
          label: 'Partially Read',
          data: partiallyReadPercentages,
          backgroundColor: 'rgba(255, 152, 0, 0.85)',
          borderColor: '#ff9800',
          borderWidth: 1,
          borderRadius: 2
        },
        {
          label: 'Paused',
          data: pausedPercentages,
          backgroundColor: 'rgba(33, 150, 243, 0.85)',
          borderColor: '#2196f3',
          borderWidth: 1,
          borderRadius: 2
        },
        {
          label: 'Abandoned',
          data: abandonedPercentages,
          backgroundColor: 'rgba(239, 83, 80, 0.85)',
          borderColor: '#ef5350',
          borderWidth: 1,
          borderRadius: 2
        },
        {
          label: 'Won\'t Read',
          data: wontReadPercentages,
          backgroundColor: 'rgba(158, 158, 158, 0.6)',
          borderColor: '#9e9e9e',
          borderWidth: 1,
          borderRadius: 2
        },
        {
          label: 'Unread',
          data: unreadPercentages,
          backgroundColor: 'rgba(158, 158, 158, 0.3)',
          borderColor: '#9e9e9e',
          borderWidth: 1,
          borderRadius: 2
        }
      ]
    });
  }

  getStatusIcon(status: string): string {
    switch (status) {
      case 'completed': return 'pi-check-circle';
      case 'in-progress': return 'pi-spinner';
      case 'not-started': return 'pi-clock';
      case 'paused': return 'pi-pause';
      case 'abandoned': return 'pi-times-circle';
      default: return 'pi-question-circle';
    }
  }

  getStatusColor(status: string): string {
    switch (status) {
      case 'completed': return '#4caf50';
      case 'in-progress': return '#ffc107';
      case 'not-started': return '#9e9e9e';
      case 'paused': return '#2196f3';
      case 'abandoned': return '#ef5350';
      default: return '#9e9e9e';
    }
  }
}
