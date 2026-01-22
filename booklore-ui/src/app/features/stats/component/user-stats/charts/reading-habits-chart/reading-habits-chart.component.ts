import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BookService} from '../../../../../book/service/book.service';
import {BookState} from '../../../../../book/model/state/book-state.model';
import {Book, ReadStatus} from '../../../../../book/model/book.model';

interface ReadingHabitsProfile {
  consistency: number;
  multitasking: number;
  completionism: number;
  exploration: number;
  organization: number;
  intensity: number;
  methodology: number;
  momentum: number;
}

interface HabitInsight {
  habit: string;
  score: number;
  description: string;
  color: string;
}

type ReadingHabitsChartData = ChartData<'radar', number[], string>;

@Component({
  selector: 'app-reading-habits-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './reading-habits-chart.component.html',
  styleUrls: ['./reading-habits-chart.component.scss']
})
export class ReadingHabitsChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'radar' as const;

  public readonly chartOptions: ChartConfiguration<'radar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    layout: {
      padding: {top: 15}
    },
    scales: {
      r: {
        beginAtZero: true,
        min: 0,
        max: 100,
        ticks: {
          stepSize: 20,
          color: 'rgba(255, 255, 255, 0.6)',
          font: {
            family: "'Inter', sans-serif",
            size: 12
          },
          backdropColor: 'transparent',
          showLabelBackdrop: false
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.2)',
          circular: true
        },
        angleLines: {
          color: 'rgba(255, 255, 255, 0.3)'
        },
        pointLabels: {
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 12
          },
          padding: 25,
          callback: function (label: string) {
            const icons: Record<string, string> = {
              'Consistency': '📅',
              'Multitasking': '📚',
              'Completionism': '✅',
              'Exploration': '🔍',
              'Organization': '📋',
              'Intensity': '⚡',
              'Methodology': '🎯',
              'Momentum': '🔥'
            };
            return [icons[label] || '', label];
          }
        }
      }
    },
    plugins: {
      legend: {
        display: false
      },
      tooltip: {
        enabled: true,
        backgroundColor: 'rgba(0, 0, 0, 0.95)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: '#9c27b0',
        borderWidth: 2,
        cornerRadius: 8,
        padding: 16,
        titleFont: {size: 14, weight: 'bold'},
        bodyFont: {size: 12},
        callbacks: {
          title: (context) => {
            const label = context[0]?.label || '';
            return `${label} Habit`;
          },
          label: (context) => {
            const score = context.parsed.r;
            const insight = this.habitInsights.find(i => i.habit === context.label);

            return [
              `Score: ${score.toFixed(1)}/100`,
              '',
              insight ? insight.description : 'Your reading habit pattern'
            ];
          }
        }
      }
    },
    interaction: {
      intersect: false,
      mode: 'point'
    },
    elements: {
      line: {
        borderWidth: 3,
        tension: 0.1
      },
      point: {
        radius: 5,
        hoverRadius: 8,
        borderWidth: 3,
        backgroundColor: 'rgba(255, 255, 255, 0.8)'
      }
    }
  };

  private readonly chartDataSubject = new BehaviorSubject<ReadingHabitsChartData>({
    labels: [
      'Consistency', 'Multitasking', 'Completionism', 'Exploration',
      'Organization', 'Intensity', 'Methodology', 'Momentum'
    ],
    datasets: []
  });

  public readonly chartData$: Observable<ReadingHabitsChartData> = this.chartDataSubject.asObservable();
  public habitInsights: HabitInsight[] = [];

  ngOnInit(): void {
    this.bookService.bookState$
      .pipe(
        filter(state => state.loaded),
        first(),
        catchError((error) => {
          console.error('Error processing reading habits data:', error);
          return EMPTY;
        }),
        takeUntil(this.destroy$)
      )
      .subscribe(() => {
        const profile = this.calculateReadingHabitsData();
        this.updateChartData(profile);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateChartData(profile: ReadingHabitsProfile | null): void {
    try {
      if (!profile) {
        this.chartDataSubject.next({
          labels: [],
          datasets: []
        });
        this.habitInsights = [];
        return;
      }

      const data = [
        profile.consistency,
        profile.multitasking,
        profile.completionism,
        profile.exploration,
        profile.organization,
        profile.intensity,
        profile.methodology,
        profile.momentum
      ];

      const habitColors = [
        '#9c27b0', '#e91e63', '#ff5722', '#ff9800',
        '#ffc107', '#4caf50', '#2196f3', '#673ab7'
      ];

      this.chartDataSubject.next({
        labels: [
          'Consistency', 'Multitasking', 'Completionism', 'Exploration',
          'Organization', 'Intensity', 'Methodology', 'Momentum'
        ],
        datasets: [{
          label: 'Reading Habits Profile',
          data,
          backgroundColor: 'rgba(156, 39, 176, 0.2)',
          borderColor: '#9c27b0',
          borderWidth: 3,
          pointBackgroundColor: habitColors,
          pointBorderColor: '#ffffff',
          pointBorderWidth: 3,
          pointRadius: 5,
          pointHoverRadius: 8,
          fill: true
        }]
      });

      this.habitInsights = this.convertToHabitInsights(profile);
    } catch (error) {
      console.error('Error updating reading habits chart data:', error);
    }
  }

  private calculateReadingHabitsData(): ReadingHabitsProfile | null {
    const currentState = this.bookService.getCurrentBookState();

    if (!this.isValidBookState(currentState)) {
      return null;
    }

    return this.analyzeReadingHabits(currentState.books!);
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

  private analyzeReadingHabits(books: Book[]): ReadingHabitsProfile {
    if (books.length === 0) {
      return this.getDefaultProfile();
    }

    return {
      consistency: this.calculateConsistencyScore(books),
      multitasking: this.calculateMultitaskingScore(books),
      completionism: this.calculateCompletionismScore(books),
      exploration: this.calculateExplorationScore(books),
      organization: this.calculateOrganizationScore(books),
      intensity: this.calculateIntensityScore(books),
      methodology: this.calculateMethodologyScore(books),
      momentum: this.calculateMomentumScore(books)
    };
  }

  private calculateConsistencyScore(books: Book[]): number {
    const booksWithDates = books.filter(book => book.dateFinished || book.addedOn);
    if (booksWithDates.length === 0) return 30;

    const completedBooks = books.filter(book => book.readStatus === ReadStatus.READ && book.dateFinished);
    if (completedBooks.length < 2) return 25;

    let consistencyScore = 50;

    const inProgress = books.filter(book =>
      book.readStatus === ReadStatus.READING || book.readStatus === ReadStatus.RE_READING
    );
    const progressRate = inProgress.length / books.length;
    consistencyScore += progressRate * 30;

    const sortedByCompletion = completedBooks
      .sort((a, b) => new Date(a.dateFinished!).getTime() - new Date(b.dateFinished!).getTime());

    if (sortedByCompletion.length >= 3) {
      consistencyScore += 20;
    }

    return Math.min(100, consistencyScore);
  }

  private calculateMultitaskingScore(books: Book[]): number {
    // ...existing code from service...
    const currentlyReading = books.filter(book => book.readStatus === ReadStatus.READING);
    const reReading = books.filter(book => book.readStatus === ReadStatus.RE_READING);
    const activeBooks = currentlyReading.length + reReading.length;

    const booksWithProgress = books.filter(book => {
      const progress = Math.max(
        book.epubProgress?.percentage || 0,
        book.pdfProgress?.percentage || 0,
        book.cbxProgress?.percentage || 0,
        book.koreaderProgress?.percentage || 0,
        book.koboProgress?.percentage || 0
      );
      return progress > 0 && progress < 100;
    });

    const multitaskingScore = Math.min(60, activeBooks * 15);
    const progressScore = Math.min(40, (booksWithProgress.length / books.length) * 80);

    return Math.min(100, multitaskingScore + progressScore);
  }

  private calculateCompletionismScore(books: Book[]): number {
    // ...existing code from service...
    const completed = books.filter(book => book.readStatus === ReadStatus.READ);
    const abandoned = books.filter(book => book.readStatus === ReadStatus.ABANDONED);
    const unfinished = books.filter(book => book.readStatus === ReadStatus.UNREAD || book.readStatus === ReadStatus.UNSET);

    const completionRate = completed.length / (books.length - unfinished.length);
    const abandonmentRate = abandoned.length / books.length;

    const completionScore = completionRate * 70;
    const abandonmentPenalty = abandonmentRate * 30;

    return Math.max(0, Math.min(100, completionScore - abandonmentPenalty + 30));
  }

  private calculateExplorationScore(books: Book[]): number {
    // ...existing code from service...
    const authors = new Set<string>();
    const authorCounts = new Map<string, number>();

    books.forEach(book => {
      book.metadata?.authors?.forEach(author => {
        const authorName = author.toLowerCase();
        authors.add(authorName);
        authorCounts.set(authorName, (authorCounts.get(authorName) || 0) + 1);
      });
    });

    const authorDiversityScore = Math.min(50, authors.size * 2);
    const maxBooksPerAuthor = Math.max(...Array.from(authorCounts.values()));
    const concentrationPenalty = Math.max(0, (maxBooksPerAuthor - 3) * 5);

    const years = new Set<number>();
    books.forEach(book => {
      if (book.metadata?.publishedDate) {
        years.add(new Date(book.metadata.publishedDate).getFullYear());
      }
    });
    const temporalScore = Math.min(30, years.size * 2);

    const languages = new Set<string>();
    books.forEach(book => {
      if (book.metadata?.language) languages.add(book.metadata.language);
    });
    const languageScore = Math.min(20, (languages.size - 1) * 10);

    return Math.max(10, Math.min(100, authorDiversityScore + temporalScore + languageScore - concentrationPenalty));
  }

  private calculateOrganizationScore(books: Book[]): number {
    // ...existing code from service...
    const seriesBooks = books.filter(book => book.metadata?.seriesName && book.metadata?.seriesNumber);
    const seriesScore = (seriesBooks.length / books.length) * 40;

    const wellOrganizedBooks = books.filter(book => {
      const metadata = book.metadata;
      if (!metadata) return false;

      const hasBasicInfo = metadata.title && metadata.authors && metadata.authors.length > 0;
      const hasDetailedInfo = metadata.publishedDate || metadata.publisher || metadata.isbn10;
      const hasCategories = metadata.categories && metadata.categories.length > 0;

      return hasBasicInfo && (hasDetailedInfo || hasCategories);
    });

    const metadataScore = (wellOrganizedBooks.length / books.length) * 35;

    const ratedBooks = books.filter(book => book.personalRating);
    const ratingScore = (ratedBooks.length / books.length) * 25;

    return Math.min(100, seriesScore + metadataScore + ratingScore);
  }

  private calculateIntensityScore(books: Book[]): number {
    // ...existing code from service...
    const booksWithPages = books.filter(book => book.metadata?.pageCount && book.metadata.pageCount > 0);
    if (booksWithPages.length === 0) return 40;

    const averagePages = booksWithPages.reduce((sum, book) => sum + (book.metadata?.pageCount || 0), 0) / booksWithPages.length;
    const intensityFromLength = Math.min(50, averagePages / 8);

    const highProgressBooks = books.filter(book => {
      const progress = Math.max(
        book.epubProgress?.percentage || 0,
        book.pdfProgress?.percentage || 0,
        book.cbxProgress?.percentage || 0,
        book.koreaderProgress?.percentage || 0
      );
      return progress > 75;
    });

    const progressScore = (highProgressBooks.length / books.length) * 30;

    const completedSeriesBooks = books.filter(book =>
      book.metadata?.seriesName && book.readStatus === ReadStatus.READ
    );
    const seriesIntensityScore = (completedSeriesBooks.length / books.length) * 20;

    return Math.min(100, intensityFromLength + progressScore + seriesIntensityScore);
  }

  private calculateMethodologyScore(books: Book[]): number {
    // ...existing code from service...
    const seriesBooks = books.filter(book => book.metadata?.seriesName);
    const seriesGroups = new Map<string, Book[]>();

    seriesBooks.forEach(book => {
      const seriesName = book.metadata!.seriesName!.toLowerCase();
      if (!seriesGroups.has(seriesName)) {
        seriesGroups.set(seriesName, []);
      }
      seriesGroups.get(seriesName)!.push(book);
    });

    let systematicSeriesScore = 0;
    seriesGroups.forEach(books => {
      if (books.length > 1) {
        const orderedBooks = books.filter(book => book.metadata?.seriesNumber).sort((a, b) =>
          (a.metadata?.seriesNumber || 0) - (b.metadata?.seriesNumber || 0)
        );
        if (orderedBooks.length >= 2) {
          systematicSeriesScore += 20;
        }
      }
    });

    const authorBooks = new Map<string, Book[]>();
    books.forEach(book => {
      book.metadata?.authors?.forEach(author => {
        const authorName = author.toLowerCase();
        if (!authorBooks.has(authorName)) {
          authorBooks.set(authorName, []);
        }
        authorBooks.get(authorName)!.push(book);
      });
    });

    const systematicAuthors = Array.from(authorBooks.values()).filter(books => books.length >= 2).length;
    const authorMethodologyScore = Math.min(30, systematicAuthors * 5);

    const categoryBooks = new Map<string, number>();
    books.forEach(book => {
      book.metadata?.categories?.forEach(category => {
        const cat = category.toLowerCase();
        categoryBooks.set(cat, (categoryBooks.get(cat) || 0) + 1);
      });
    });

    const majorCategories = Array.from(categoryBooks.values()).filter(count => count >= 3).length;
    const categoryMethodologyScore = Math.min(25, majorCategories * 8);

    const baseMethodologyScore = books.length >= 10 ? 15 : Math.max(5, books.length);

    return Math.min(100, systematicSeriesScore + authorMethodologyScore + categoryMethodologyScore + baseMethodologyScore);
  }

  private calculateMomentumScore(books: Book[]): number {
    // ...existing code from service...
    const completedBooks = books.filter(book => book.readStatus === ReadStatus.READ && book.dateFinished);

    if (completedBooks.length === 0) {
      const activeBooks = books.filter(book =>
        book.readStatus === ReadStatus.READING || book.readStatus === ReadStatus.RE_READING
      );
      return Math.min(40, activeBooks.length * 10);
    }

    const sortedBooks = completedBooks.sort((a, b) =>
      new Date(a.dateFinished!).getTime() - new Date(b.dateFinished!).getTime()
    );

    let momentumScore = 20;

    const sixMonthsAgo = new Date();
    sixMonthsAgo.setMonth(sixMonthsAgo.getMonth() - 6);

    const recentCompletions = sortedBooks.filter(book =>
      new Date(book.dateFinished!) > sixMonthsAgo
    );

    momentumScore += Math.min(40, recentCompletions.length * 5);

    const currentlyReading = books.filter(book =>
      book.readStatus === ReadStatus.READING || book.readStatus === ReadStatus.RE_READING
    );

    momentumScore += Math.min(25, currentlyReading.length * 8);

    const highProgressBooks = books.filter(book => {
      const progress = Math.max(
        book.epubProgress?.percentage || 0,
        book.pdfProgress?.percentage || 0,
        book.cbxProgress?.percentage || 0,
        book.koreaderProgress?.percentage || 0
      );
      return progress > 50 && progress < 100;
    });

    momentumScore += Math.min(15, highProgressBooks.length * 3);

    return Math.min(100, momentumScore);
  }

  private getDefaultProfile(): ReadingHabitsProfile {
    return {
      consistency: 40,
      multitasking: 30,
      completionism: 50,
      exploration: 45,
      organization: 35,
      intensity: 40,
      methodology: 35,
      momentum: 30
    };
  }

  private convertToHabitInsights(profile: ReadingHabitsProfile): HabitInsight[] {
    return [
      {
        habit: 'Consistency',
        score: profile.consistency,
        description: 'You maintain regular reading patterns and schedules',
        color: '#9c27b0'
      },
      {
        habit: 'Multitasking',
        score: profile.multitasking,
        description: 'You juggle multiple books simultaneously',
        color: '#e91e63'
      },
      {
        habit: 'Completionism',
        score: profile.completionism,
        description: 'You finish books rather than abandon them',
        color: '#ff5722'
      },
      {
        habit: 'Exploration',
        score: profile.exploration,
        description: 'You actively seek out new authors and genres',
        color: '#ff9800'
      },
      {
        habit: 'Organization',
        score: profile.organization,
        description: 'You maintain systematic book tracking and metadata',
        color: '#ffc107'
      },
      {
        habit: 'Intensity',
        score: profile.intensity,
        description: 'You prefer longer, immersive reading sessions',
        color: '#4caf50'
      },
      {
        habit: 'Methodology',
        score: profile.methodology,
        description: 'You follow systematic approaches to book selection',
        color: '#2196f3'
      },
      {
        habit: 'Momentum',
        score: profile.momentum,
        description: 'You maintain active reading streaks and continuity',
        color: '#673ab7'
      }
    ];
  }
}

