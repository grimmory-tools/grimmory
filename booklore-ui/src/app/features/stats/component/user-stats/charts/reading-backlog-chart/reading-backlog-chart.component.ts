import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, switchMap, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {Book, ReadStatus} from '../../../../../book/model/book.model';
import {BookService} from '../../../../../book/service/book.service';
import {LibraryFilterService} from '../../../library-stats/service/library-filter.service';
import {BookState} from '../../../../../book/model/state/book-state.model';

interface BacklogBucket {
  label: string;
  range: string;
  unread: number;
  reading: number;
  completed: number;
  avgDaysToRead: number | null;
  books: BookBacklogInfo[];
}

interface BookBacklogInfo {
  title: string;
  addedOn: Date;
  daysInLibrary: number;
  daysToComplete: number | null;
  status: ReadStatus;
}

interface BacklogStats {
  totalBooks: number;
  unreadBooks: number;
  avgBacklogAge: number;
  avgDaysToRead: number;
  oldestUnread: BookBacklogInfo | null;
  quickestRead: BookBacklogInfo | null;
  longestWait: BookBacklogInfo | null;
  readingVelocity: number;
  backlogHealthScore: number;
}

type BacklogChartData = ChartData<'bar', number[], string>;

@Component({
  selector: 'app-reading-backlog-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './reading-backlog-chart.component.html',
  styleUrls: ['./reading-backlog-chart.component.scss']
})
export class ReadingBacklogChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'bar' as const;
  public buckets: BacklogBucket[] = [];
  public stats: BacklogStats | null = null;

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
        title: {
          display: true,
          text: 'Number of Books',
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 12,
            weight: 500
          }
        },
        ticks: {
          color: 'rgba(255, 255, 255, 0.8)',
          font: {
            family: "'Inter', sans-serif",
            size: 11
          },
          stepSize: 1
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.1)'
        }
      },
      y: {
        stacked: true,
        title: {
          display: true,
          text: 'Time in Library',
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 12,
            weight: 500
          }
        },
        ticks: {
          color: 'rgba(255, 255, 255, 0.8)',
          font: {
            family: "'Inter', sans-serif",
            size: 11
          }
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.1)'
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
            size: 11
          },
          usePointStyle: true,
          pointStyle: 'rect',
          padding: 15
        }
      },
      tooltip: {
        enabled: true,
        backgroundColor: 'rgba(0, 0, 0, 0.95)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: '#2196f3',
        borderWidth: 2,
        cornerRadius: 8,
        padding: 12,
        titleFont: {size: 13, weight: 'bold'},
        bodyFont: {size: 11},
        callbacks: {
          title: (context) => {
            const bucket = this.buckets[context[0].dataIndex];
            return bucket ? `${bucket.label} (${bucket.range})` : '';
          },
          afterBody: (context) => {
            const bucket = this.buckets[context[0].dataIndex];
            if (!bucket) return [];
            const lines = [];
            if (bucket.avgDaysToRead !== null) {
              lines.push(`Avg. days to read: ${bucket.avgDaysToRead.toFixed(0)}`);
            }
            return lines;
          }
        }
      }
    }
  };

  private readonly chartDataSubject = new BehaviorSubject<BacklogChartData>({
    labels: [],
    datasets: []
  });

  public readonly chartData$: Observable<BacklogChartData> = this.chartDataSubject.asObservable();

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
          console.error('Error processing backlog data:', error);
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
      this.buckets = [];
      this.stats = null;
      return;
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    const booksWithDates = this.getBooksWithAddedDate(filteredBooks);

    if (booksWithDates.length === 0) {
      this.chartDataSubject.next({labels: [], datasets: []});
      this.buckets = [];
      this.stats = null;
      return;
    }

    this.buckets = this.calculateBacklogBuckets(booksWithDates);
    this.stats = this.calculateBacklogStats(booksWithDates);
    this.updateChartData();
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

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | number | null): Book[] {
    return selectedLibraryId
      ? books.filter(book => book.libraryId === selectedLibraryId)
      : books;
  }

  private getBooksWithAddedDate(books: Book[]): Book[] {
    return books.filter(book => book.addedOn);
  }

  private calculateBacklogBuckets(books: Book[]): BacklogBucket[] {
    const now = new Date();
    const bucketDefs = [
      {label: 'Fresh', range: '< 1 week', minDays: 0, maxDays: 7},
      {label: 'Recent', range: '1-4 weeks', minDays: 7, maxDays: 30},
      {label: 'Settling', range: '1-3 months', minDays: 30, maxDays: 90},
      {label: 'Established', range: '3-6 months', minDays: 90, maxDays: 180},
      {label: 'Seasoned', range: '6-12 months', minDays: 180, maxDays: 365},
      {label: 'Vintage', range: '1-2 years', minDays: 365, maxDays: 730},
      {label: 'Archive', range: '2+ years', minDays: 730, maxDays: Infinity}
    ];

    return bucketDefs.map(def => {
      const bucketBooks: BookBacklogInfo[] = [];
      let unread = 0;
      let reading = 0;
      let completed = 0;
      let totalDaysToRead = 0;
      let completedCount = 0;

      books.forEach(book => {
        const addedDate = new Date(book.addedOn!);
        const daysInLibrary = Math.floor((now.getTime() - addedDate.getTime()) / (1000 * 60 * 60 * 24));

        if (daysInLibrary >= def.minDays && daysInLibrary < def.maxDays) {
          let daysToComplete: number | null = null;

          if (book.dateFinished) {
            const finishedDate = new Date(book.dateFinished);
            daysToComplete = Math.floor((finishedDate.getTime() - addedDate.getTime()) / (1000 * 60 * 60 * 24));
            if (daysToComplete < 0) daysToComplete = 0;
          }

          const bookInfo: BookBacklogInfo = {
            title: book.metadata?.title || book.fileName || 'Unknown',
            addedOn: addedDate,
            daysInLibrary,
            daysToComplete,
            status: book.readStatus || ReadStatus.UNSET
          };

          bucketBooks.push(bookInfo);

          switch (book.readStatus) {
            case ReadStatus.READING:
            case ReadStatus.RE_READING:
              reading++;
              break;
            case ReadStatus.READ:
              completed++;
              if (daysToComplete !== null) {
                totalDaysToRead += daysToComplete;
                completedCount++;
              }
              break;
            case ReadStatus.UNREAD:
            case ReadStatus.UNSET:
            case ReadStatus.PAUSED:
            case ReadStatus.WONT_READ:
            case ReadStatus.ABANDONED:
            case ReadStatus.PARTIALLY_READ:
            default:
              unread++;
              break;
          }
        }
      });

      return {
        label: def.label,
        range: def.range,
        unread,
        reading,
        completed,
        avgDaysToRead: completedCount > 0 ? totalDaysToRead / completedCount : null,
        books: bucketBooks
      };
    });
  }

  private calculateBacklogStats(books: Book[]): BacklogStats {
    const now = new Date();
    let totalDaysInLibrary = 0;
    let totalDaysToRead = 0;
    let completedWithDates = 0;
    let unreadBooks = 0;
    let oldestUnread: BookBacklogInfo | null = null;
    let quickestRead: BookBacklogInfo | null = null;
    let longestWait: BookBacklogInfo | null = null;
    let recentlyCompleted = 0;

    const sixMonthsAgo = new Date();
    sixMonthsAgo.setMonth(sixMonthsAgo.getMonth() - 6);

    books.forEach(book => {
      const addedDate = new Date(book.addedOn!);
      const daysInLibrary = Math.floor((now.getTime() - addedDate.getTime()) / (1000 * 60 * 60 * 24));
      totalDaysInLibrary += daysInLibrary;

      let daysToComplete: number | null = null;
      if (book.dateFinished) {
        const finishedDate = new Date(book.dateFinished);
        daysToComplete = Math.floor((finishedDate.getTime() - addedDate.getTime()) / (1000 * 60 * 60 * 24));
        if (daysToComplete < 0) daysToComplete = 0;

        if (finishedDate > sixMonthsAgo) {
          recentlyCompleted++;
        }
      }

      const bookInfo: BookBacklogInfo = {
        title: book.metadata?.title || book.fileName || 'Unknown',
        addedOn: addedDate,
        daysInLibrary,
        daysToComplete,
        status: book.readStatus || ReadStatus.UNSET
      };

      const isUnread = book.readStatus === ReadStatus.UNREAD ||
        book.readStatus === ReadStatus.UNSET ||
        book.readStatus === ReadStatus.PAUSED ||
        !book.readStatus;

      if (isUnread) {
        unreadBooks++;
        if (!oldestUnread || daysInLibrary > oldestUnread.daysInLibrary) {
          oldestUnread = bookInfo;
        }
      }

      if (book.readStatus === ReadStatus.READ && daysToComplete !== null) {
        totalDaysToRead += daysToComplete;
        completedWithDates++;

        if (!quickestRead || daysToComplete < (quickestRead.daysToComplete || Infinity)) {
          quickestRead = bookInfo;
        }
        if (!longestWait || daysToComplete > (longestWait.daysToComplete || 0)) {
          longestWait = bookInfo;
        }
      }
    });

    const avgBacklogAge = books.length > 0 ? totalDaysInLibrary / books.length : 0;
    const avgDaysToRead = completedWithDates > 0 ? totalDaysToRead / completedWithDates : 0;
    const readingVelocity = recentlyCompleted; // Books completed in last 6 months

    // Backlog health score (0-100)
    // Lower unread percentage = better
    // Lower avg backlog age = better
    // Higher reading velocity = better
    const unreadPercentage = books.length > 0 ? (unreadBooks / books.length) * 100 : 0;
    const ageScore = Math.max(0, 100 - (avgBacklogAge / 365) * 50); // Penalize old backlogs
    const velocityScore = Math.min(100, readingVelocity * 10); // Reward active reading
    const completionScore = 100 - unreadPercentage;

    const backlogHealthScore = Math.round((ageScore * 0.3 + velocityScore * 0.3 + completionScore * 0.4));

    return {
      totalBooks: books.length,
      unreadBooks,
      avgBacklogAge,
      avgDaysToRead,
      oldestUnread,
      quickestRead,
      longestWait,
      readingVelocity,
      backlogHealthScore: Math.min(100, Math.max(0, backlogHealthScore))
    };
  }

  private updateChartData(): void {
    const labels = this.buckets.map(b => b.label);

    this.chartDataSubject.next({
      labels,
      datasets: [
        {
          label: 'Unread',
          data: this.buckets.map(b => b.unread),
          backgroundColor: 'rgba(239, 83, 80, 0.8)',
          borderColor: '#ef5350',
          borderWidth: 1,
          borderRadius: 4
        },
        {
          label: 'Reading',
          data: this.buckets.map(b => b.reading),
          backgroundColor: 'rgba(255, 193, 7, 0.8)',
          borderColor: '#ffc107',
          borderWidth: 1,
          borderRadius: 4
        },
        {
          label: 'Completed',
          data: this.buckets.map(b => b.completed),
          backgroundColor: 'rgba(76, 175, 80, 0.8)',
          borderColor: '#4caf50',
          borderWidth: 1,
          borderRadius: 4
        }
      ]
    });
  }

  getHealthScoreClass(): string {
    if (!this.stats) return '';
    if (this.stats.backlogHealthScore >= 70) return 'healthy';
    if (this.stats.backlogHealthScore >= 40) return 'moderate';
    return 'unhealthy';
  }

  getHealthScoreLabel(): string {
    if (!this.stats) return '';
    if (this.stats.backlogHealthScore >= 70) return 'Healthy';
    if (this.stats.backlogHealthScore >= 40) return 'Moderate';
    return 'Needs Attention';
  }

  formatDays(days: number): string {
    const roundedDays = Math.round(days);
    if (roundedDays < 7) return `${roundedDays} day${roundedDays !== 1 ? 's' : ''}`;
    const weeks = Math.round(roundedDays / 7);
    if (roundedDays < 30) return `${weeks} week${weeks !== 1 ? 's' : ''}`;
    const months = Math.round(roundedDays / 30);
    if (roundedDays < 365) return `${months} month${months !== 1 ? 's' : ''}`;
    const years = Math.round((roundedDays / 365) * 10) / 10; // Round to 1 decimal
    return `${years} year${years >= 2 ? 's' : ''}`;
  }
}
