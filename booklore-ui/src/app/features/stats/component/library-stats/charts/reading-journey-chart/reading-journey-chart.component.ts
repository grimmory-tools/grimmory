import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, switchMap, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {LibraryFilterService} from '../../service/library-filter.service';
import {BookService} from '../../../../../book/service/book.service';
import {BookState} from '../../../../../book/model/state/book-state.model';
import {Book, ReadStatus} from '../../../../../book/model/book.model';

interface MonthlyData {
  month: string;
  label: string;
  added: number;
  finished: number;
  cumulativeAdded: number;
  cumulativeFinished: number;
}

interface JourneyInsights {
  totalAdded: number;
  totalFinished: number;
  currentBacklog: number;
  backlogPercent: number;
  avgTimeToFinishDays: number;
  mostProductiveMonth: string;
  mostProductiveCount: number;
  busiestAcquisitionMonth: string;
  busiestAcquisitionCount: number;
  finishRate: number;
  recentActivity: string;
  longestStreak: number;
}

type JourneyChartData = ChartData<'line', number[], string>;

@Component({
  selector: 'app-reading-journey-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './reading-journey-chart.component.html',
  styleUrls: ['./reading-journey-chart.component.scss']
})
export class ReadingJourneyChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'line' as const;
  public chartOptions: ChartConfiguration<'line'>['options'];
  public insights: JourneyInsights | null = null;
  public totalBooks = 0;
  public dateRange = '';

  private readonly chartDataSubject = new BehaviorSubject<JourneyChartData>({
    labels: [],
    datasets: []
  });

  public readonly chartData$: Observable<JourneyChartData> = this.chartDataSubject.asObservable();

  constructor() {
    this.initChartOptions();
  }

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
          console.error('Error processing reading journey data:', error);
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

  private initChartOptions(): void {
    this.chartOptions = {
      responsive: true,
      maintainAspectRatio: false,
      layout: {
        padding: {top: 20, right: 20, bottom: 10, left: 10}
      },
      scales: {
        x: {
          ticks: {
            color: 'rgba(255, 255, 255, 0.8)',
            font: {
              family: "'Inter', sans-serif",
              size: 10
            },
            maxRotation: 45,
            minRotation: 45,
            autoSkip: true,
            maxTicksLimit: 24
          },
          grid: {
            color: 'rgba(255, 255, 255, 0.05)'
          },
          border: {display: false},
          title: {
            display: true,
            text: 'Month',
            color: '#ffffff',
            font: {
              family: "'Inter', sans-serif",
              size: 12,
              weight: 500
            }
          }
        },
        y: {
          beginAtZero: true,
          ticks: {
            color: 'rgba(255, 255, 255, 0.8)',
            font: {
              family: "'Inter', sans-serif",
              size: 11
            },
            precision: 0
          },
          grid: {
            color: 'rgba(255, 255, 255, 0.08)'
          },
          border: {display: false},
          title: {
            display: true,
            text: 'Cumulative Books',
            color: '#ffffff',
            font: {
              family: "'Inter', sans-serif",
              size: 12,
              weight: 500
            }
          }
        }
      },
      plugins: {
        legend: {
          display: true,
          position: 'top',
          labels: {
            color: 'rgba(255, 255, 255, 0.9)',
            font: {
              family: "'Inter', sans-serif",
              size: 12
            },
            padding: 20,
            usePointStyle: true,
            pointStyle: 'circle'
          }
        },
        tooltip: {
          enabled: true,
          backgroundColor: 'rgba(0, 0, 0, 0.95)',
          titleColor: '#ffffff',
          bodyColor: '#ffffff',
          borderColor: '#10b981',
          borderWidth: 2,
          cornerRadius: 8,
          padding: 12,
          titleFont: {size: 13, weight: 'bold'},
          bodyFont: {size: 11},
          callbacks: {
            title: (context) => context[0].label,
            afterBody: (context) => {
              const dataIndex = context[0].dataIndex;
              const addedValue = context[0].chart.data.datasets[0].data[dataIndex] as number;
              const finishedValue = context[0].chart.data.datasets[1].data[dataIndex] as number;
              const backlog = addedValue - finishedValue;
              return [`\nBacklog: ${backlog} books`];
            }
          }
        },
        datalabels: {
          display: false
        }
      },
      elements: {
        line: {
          tension: 0.3,
          borderWidth: 3
        },
        point: {
          radius: 3,
          hoverRadius: 6,
          borderWidth: 2
        }
      },
      interaction: {
        intersect: false,
        mode: 'index'
      }
    };
  }

  private calculateAndUpdateChart(): void {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      this.chartDataSubject.next({labels: [], datasets: []});
      this.insights = null;
      this.totalBooks = 0;
      this.dateRange = '';
      return;
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    this.totalBooks = filteredBooks.length;

    if (filteredBooks.length === 0) {
      this.chartDataSubject.next({labels: [], datasets: []});
      this.insights = null;
      this.dateRange = '';
      return;
    }

    const monthlyData = this.calculateMonthlyData(filteredBooks);
    this.insights = this.calculateInsights(filteredBooks, monthlyData);
    this.updateChartData(monthlyData);
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

  private calculateMonthlyData(books: Book[]): MonthlyData[] {
    const monthlyAdded = new Map<string, number>();
    const monthlyFinished = new Map<string, number>();

    for (const book of books) {
      // Track added dates
      if (book.addedOn) {
        const monthKey = this.getMonthKey(book.addedOn);
        if (monthKey) {
          monthlyAdded.set(monthKey, (monthlyAdded.get(monthKey) || 0) + 1);
        }
      }

      // Track finished dates
      if (book.dateFinished && book.readStatus === ReadStatus.READ) {
        const monthKey = this.getMonthKey(book.dateFinished);
        if (monthKey) {
          monthlyFinished.set(monthKey, (monthlyFinished.get(monthKey) || 0) + 1);
        }
      }
    }

    // Get all unique months and sort them
    const allMonths = new Set([...monthlyAdded.keys(), ...monthlyFinished.keys()]);
    const sortedMonths = Array.from(allMonths).sort();

    if (sortedMonths.length === 0) {
      return [];
    }

    // Fill in gaps and calculate cumulative values
    const firstMonth = sortedMonths[0];
    const lastMonth = sortedMonths[sortedMonths.length - 1];
    const allMonthsRange = this.getMonthRange(firstMonth, lastMonth);

    this.dateRange = `${this.formatMonthLabel(firstMonth)} - ${this.formatMonthLabel(lastMonth)}`;

    let cumulativeAdded = 0;
    let cumulativeFinished = 0;

    return allMonthsRange.map(month => {
      const added = monthlyAdded.get(month) || 0;
      const finished = monthlyFinished.get(month) || 0;
      cumulativeAdded += added;
      cumulativeFinished += finished;

      return {
        month,
        label: this.formatMonthLabel(month),
        added,
        finished,
        cumulativeAdded,
        cumulativeFinished
      };
    });
  }

  private getMonthKey(dateStr: string): string | null {
    if (!dateStr) return null;
    try {
      const date = new Date(dateStr);
      if (isNaN(date.getTime())) return null;
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, '0');
      return `${year}-${month}`;
    } catch {
      return null;
    }
  }

  private getMonthRange(start: string, end: string): string[] {
    const months: string[] = [];
    const [startYear, startMonth] = start.split('-').map(Number);
    const [endYear, endMonth] = end.split('-').map(Number);

    let year = startYear;
    let month = startMonth;

    while (year < endYear || (year === endYear && month <= endMonth)) {
      months.push(`${year}-${String(month).padStart(2, '0')}`);
      month++;
      if (month > 12) {
        month = 1;
        year++;
      }
    }

    return months;
  }

  private formatMonthLabel(monthKey: string): string {
    const [year, month] = monthKey.split('-');
    const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    return `${monthNames[parseInt(month, 10) - 1]} ${year}`;
  }

  private calculateInsights(books: Book[], monthlyData: MonthlyData[]): JourneyInsights {
    const booksWithAddedDate = books.filter(b => b.addedOn);
    const booksFinished = books.filter(b => b.dateFinished && b.readStatus === ReadStatus.READ);

    const totalAdded = booksWithAddedDate.length;
    const totalFinished = booksFinished.length;
    const currentBacklog = totalAdded - totalFinished;
    const backlogPercent = totalAdded > 0 ? Math.round((currentBacklog / totalAdded) * 100) : 0;

    // Calculate average time to finish
    let totalDaysToFinish = 0;
    let finishedWithBothDates = 0;

    for (const book of booksFinished) {
      if (book.addedOn && book.dateFinished) {
        const addedDate = new Date(book.addedOn);
        const finishedDate = new Date(book.dateFinished);
        if (!isNaN(addedDate.getTime()) && !isNaN(finishedDate.getTime())) {
          const days = Math.floor((finishedDate.getTime() - addedDate.getTime()) / (1000 * 60 * 60 * 24));
          if (days >= 0) {
            totalDaysToFinish += days;
            finishedWithBothDates++;
          }
        }
      }
    }

    const avgTimeToFinishDays = finishedWithBothDates > 0
      ? Math.round(totalDaysToFinish / finishedWithBothDates)
      : 0;

    // Find most productive reading month
    let mostProductiveMonth = 'N/A';
    let mostProductiveCount = 0;
    let busiestAcquisitionMonth = 'N/A';
    let busiestAcquisitionCount = 0;

    for (const data of monthlyData) {
      if (data.finished > mostProductiveCount) {
        mostProductiveCount = data.finished;
        mostProductiveMonth = data.label;
      }
      if (data.added > busiestAcquisitionCount) {
        busiestAcquisitionCount = data.added;
        busiestAcquisitionMonth = data.label;
      }
    }

    // Finish rate (books finished per month on average)
    const finishRate = monthlyData.length > 0
      ? +(totalFinished / monthlyData.length).toFixed(1)
      : 0;

    // Recent activity
    const now = new Date();
    const threeMonthsAgo = new Date(now.getFullYear(), now.getMonth() - 3, 1);
    let recentFinished = 0;
    for (const book of booksFinished) {
      if (book.dateFinished) {
        const finishDate = new Date(book.dateFinished);
        if (finishDate >= threeMonthsAgo) {
          recentFinished++;
        }
      }
    }
    const recentActivity = recentFinished > 0 ? `${recentFinished} books` : 'No activity';

    // Longest reading streak (consecutive months with finished books)
    let longestStreak = 0;
    let currentStreak = 0;
    for (const data of monthlyData) {
      if (data.finished > 0) {
        currentStreak++;
        longestStreak = Math.max(longestStreak, currentStreak);
      } else {
        currentStreak = 0;
      }
    }

    return {
      totalAdded,
      totalFinished,
      currentBacklog,
      backlogPercent,
      avgTimeToFinishDays,
      mostProductiveMonth,
      mostProductiveCount,
      busiestAcquisitionMonth,
      busiestAcquisitionCount,
      finishRate,
      recentActivity,
      longestStreak
    };
  }

  private updateChartData(monthlyData: MonthlyData[]): void {
    if (monthlyData.length === 0) {
      this.chartDataSubject.next({labels: [], datasets: []});
      return;
    }

    const labels = monthlyData.map(d => d.label);
    const addedData = monthlyData.map(d => d.cumulativeAdded);
    const finishedData = monthlyData.map(d => d.cumulativeFinished);

    this.chartDataSubject.next({
      labels,
      datasets: [
        {
          label: 'Books Added',
          data: addedData,
          borderColor: '#3b82f6',
          backgroundColor: 'rgba(59, 130, 246, 0.1)',
          pointBackgroundColor: '#3b82f6',
          pointBorderColor: '#ffffff',
          fill: true,
          order: 2
        },
        {
          label: 'Books Finished',
          data: finishedData,
          borderColor: '#10b981',
          backgroundColor: 'rgba(16, 185, 129, 0.2)',
          pointBackgroundColor: '#10b981',
          pointBorderColor: '#ffffff',
          fill: true,
          order: 1
        }
      ]
    });
  }
}
