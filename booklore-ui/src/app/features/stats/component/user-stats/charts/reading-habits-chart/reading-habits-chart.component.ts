import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {Tooltip} from 'primeng/tooltip';
import {BookService} from '../../../../../book/service/book.service';
import {BookState} from '../../../../../book/model/state/book-state.model';
import {Book, ReadStatus} from '../../../../../book/model/book.model';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

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
  imports: [CommonModule, BaseChartDirective, Tooltip, TranslocoDirective],
  templateUrl: './reading-habits-chart.component.html',
  styleUrls: ['./reading-habits-chart.component.scss']
})
export class ReadingHabitsChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();

  private readonly habitKeys = ['consistency', 'multitasking', 'completionism', 'exploration', 'organization', 'intensity', 'methodology', 'momentum'];

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
          callback: (label: string) => {
            const icons = ['📅', '📚', '✅', '🔍', '📋', '⚡', '🎯', '🔥'];
            const translatedLabels = this.habitKeys.map(k => this.t.translate(`statsUser.readingHabits.habits.${k}`));
            const idx = translatedLabels.indexOf(label);
            return [idx >= 0 ? icons[idx] : '', label];
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
            return this.t.translate('statsUser.readingHabits.tooltipHabit', {label});
          },
          label: (context) => {
            const score = context.parsed.r;
            const insight = this.habitInsights.find(i => i.habit === context.label);

            return [
              this.t.translate('statsUser.readingHabits.tooltipScore', {score}),
              '',
              insight ? insight.description : this.t.translate('statsUser.readingHabits.tooltipDefaultDescription')
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
    labels: [],
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
    if (!profile) {
      this.chartDataSubject.next({labels: [], datasets: []});
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

    const translatedLabels = this.habitKeys.map(k => this.t.translate(`statsUser.readingHabits.habits.${k}`));

    this.chartDataSubject.next({
      labels: translatedLabels,
      datasets: [{
        label: this.t.translate('statsUser.readingHabits.readingHabitsProfile'),
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

    this.habitInsights = this.buildHabitInsights(profile);
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

  private analyzeReadingHabits(books: Book[]): ReadingHabitsProfile | null {
    if (books.length === 0) {
      return null;
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

  // Regularity of reading over time (coefficient of variation of gaps between completions)
  private calculateConsistencyScore(books: Book[]): number {
    const completedBooks = books
      .filter(book => book.readStatus === ReadStatus.READ && book.dateFinished)
      .sort((a, b) => new Date(a.dateFinished!).getTime() - new Date(b.dateFinished!).getTime());

    if (completedBooks.length < 3) {
      return Math.min(20, completedBooks.length * 10);
    }

    const dates = completedBooks.map(b => new Date(b.dateFinished!).getTime());
    const gaps: number[] = [];
    for (let i = 1; i < dates.length; i++) {
      gaps.push((dates[i] - dates[i - 1]) / (1000 * 60 * 60 * 24));
    }

    const meanGap = gaps.reduce((a, b) => a + b, 0) / gaps.length;
    if (meanGap === 0) return 50; // all finished same day — likely bulk import

    const variance = gaps.reduce((sum, g) => sum + Math.pow(g - meanGap, 2), 0) / gaps.length;
    const stdDev = Math.sqrt(variance);
    const cv = stdDev / meanGap; // coefficient of variation: < 0.5 = very regular, > 2 = very irregular

    const regularityScore = Math.max(0, Math.min(70, (1 - cv / 2) * 70));
    const volumeBonus = Math.min(30, completedBooks.length * 1.5);

    return Math.min(100, Math.round(regularityScore + volumeBonus));
  }

  // How many books are being read simultaneously
  private calculateMultitaskingScore(books: Book[]): number {
    const activeBooks = books.filter(book =>
      book.readStatus === ReadStatus.READING || book.readStatus === ReadStatus.RE_READING
    ).length;

    // 1 active = 10, 2 = 30, 3 = 50, 4 = 65, 5+ = 75+
    const activeScore = Math.min(75, activeBooks <= 1 ? activeBooks * 10 : 10 + (activeBooks - 1) * 20);

    // Partial-progress books (started but not currently reading or finished)
    const partialBooks = books.filter(book => {
      if (book.readStatus === ReadStatus.READING || book.readStatus === ReadStatus.RE_READING) return false;
      if (book.readStatus === ReadStatus.READ) return false;
      const progress = this.getBookProgress(book);
      return progress > 10 && progress < 90;
    });
    const partialScore = Math.min(25, partialBooks.length * 5);

    return Math.min(100, Math.round(activeScore + partialScore));
  }

  // Completion rate vs abandonment among started books
  private calculateCompletionismScore(books: Book[]): number {
    const started = books.filter(b =>
      b.readStatus === ReadStatus.READ ||
      b.readStatus === ReadStatus.ABANDONED ||
      b.readStatus === ReadStatus.READING ||
      b.readStatus === ReadStatus.RE_READING ||
      this.getBookProgress(b) > 0
    );

    if (started.length === 0) return 0;

    const completed = books.filter(b => b.readStatus === ReadStatus.READ);
    const abandoned = books.filter(b => b.readStatus === ReadStatus.ABANDONED);

    const completionRate = completed.length / started.length;
    const abandonmentRate = abandoned.length / started.length;

    const completionScore = completionRate * 75;
    const loyaltyScore = (1 - abandonmentRate) * 25;

    return Math.min(100, Math.round(completionScore + loyaltyScore));
  }

  // Author diversity relative to library size + publication era spread + languages
  private calculateExplorationScore(books: Book[]): number {
    const authors = new Set<string>();
    books.forEach(book => {
      book.metadata?.authors?.forEach(a => authors.add(a.toLowerCase()));
    });

    // Unique authors relative to book count (1:1 ratio = max diversity)
    const authorRatio = authors.size / Math.max(1, books.length);
    const diversityScore = Math.min(60, authorRatio * 60);

    // Publication era spread
    const years: number[] = [];
    books.forEach(book => {
      if (book.metadata?.publishedDate) {
        const year = new Date(book.metadata.publishedDate).getFullYear();
        if (year > 0) years.push(year);
      }
    });
    let temporalScore = 0;
    if (years.length >= 2) {
      const yearSpread = Math.max(...years) - Math.min(...years);
      temporalScore = Math.min(25, yearSpread * 0.5);
    }

    // Language variety
    const languages = new Set<string>();
    books.forEach(book => {
      if (book.metadata?.language) languages.add(book.metadata.language);
    });
    const languageScore = Math.min(15, Math.max(0, languages.size - 1) * 7.5);

    return Math.min(100, Math.round(diversityScore + temporalScore + languageScore));
  }

  // Library curation: rating discipline + read status management + series tracking
  private calculateOrganizationScore(books: Book[]): number {
    // Rating discipline: % of completed books with personal ratings
    const completedBooks = books.filter(b => b.readStatus === ReadStatus.READ);
    const ratedCompleted = completedBooks.filter(b => b.personalRating);
    const ratingRate = completedBooks.length > 0 ? ratedCompleted.length / completedBooks.length : 0;
    const ratingScore = ratingRate * 40;

    // Read status discipline: % of books with a status set (not UNSET)
    const statusSet = books.filter(b => b.readStatus && b.readStatus !== ReadStatus.UNSET);
    const statusRate = statusSet.length / books.length;
    const statusScore = statusRate * 35;

    // Series tracking: % of series books with series numbers
    const seriesBooks = books.filter(b => b.metadata?.seriesName);
    const numberedSeries = seriesBooks.filter(b => b.metadata?.seriesNumber);
    const seriesRate = seriesBooks.length > 0 ? numberedSeries.length / seriesBooks.length : 1;
    const seriesScore = seriesRate * 25;

    return Math.min(100, Math.round(ratingScore + statusScore + seriesScore));
  }

  // Average book length + deep reading progress
  private calculateIntensityScore(books: Book[]): number {
    const booksWithPages = books.filter(b => b.metadata?.pageCount && b.metadata.pageCount > 0);
    if (booksWithPages.length === 0) return 0;

    const avgPages = booksWithPages.reduce((sum, b) => sum + (b.metadata?.pageCount || 0), 0) / booksWithPages.length;
    // 200 avg = 20, 400 avg = 40, 600+ avg = 60
    const lengthScore = Math.min(60, avgPages / 10);

    // Books read past 75% progress
    const deepReaders = books.filter(b => this.getBookProgress(b) > 75);
    const progressScore = books.length > 0 ? Math.min(40, (deepReaders.length / books.length) * 40) : 0;

    return Math.min(100, Math.round(lengthScore + progressScore));
  }

  // Reading series in order + deep author dives + focused genre reading
  private calculateMethodologyScore(books: Book[]): number {
    // Series order discipline
    const seriesBooks = books.filter(b => b.metadata?.seriesName && b.metadata?.seriesNumber);
    const seriesGroups = new Map<string, Book[]>();
    seriesBooks.forEach(book => {
      const name = book.metadata!.seriesName!.toLowerCase();
      if (!seriesGroups.has(name)) seriesGroups.set(name, []);
      seriesGroups.get(name)!.push(book);
    });

    let orderedSeries = 0;
    let totalMultiBookSeries = 0;
    seriesGroups.forEach(group => {
      if (group.length < 2) return;
      totalMultiBookSeries++;

      const sorted = [...group].sort((a, b) =>
        (a.metadata?.seriesNumber || 0) - (b.metadata?.seriesNumber || 0)
      );

      // Check if completion dates follow series number order
      const datesInOrder = sorted.every((book, i) => {
        if (i === 0) return true;
        if (!book.dateFinished || !sorted[i - 1].dateFinished) return true;
        return new Date(book.dateFinished) >= new Date(sorted[i - 1].dateFinished!);
      });

      if (datesInOrder) orderedSeries++;
    });

    const orderScore = totalMultiBookSeries > 0
      ? (orderedSeries / totalMultiBookSeries) * 50
      : 25;

    // Deep author dives (3+ books by same author)
    const authorCounts = new Map<string, number>();
    books.forEach(book => {
      book.metadata?.authors?.forEach(a => {
        const name = a.toLowerCase();
        authorCounts.set(name, (authorCounts.get(name) || 0) + 1);
      });
    });
    const deepDiveAuthors = Array.from(authorCounts.values()).filter(c => c >= 3).length;
    const authorDepthScore = Math.min(30, deepDiveAuthors * 10);

    // Focused genre reading (5+ books in a genre)
    const genreCounts = new Map<string, number>();
    books.forEach(book => {
      book.metadata?.categories?.forEach(cat => {
        genreCounts.set(cat.toLowerCase(), (genreCounts.get(cat.toLowerCase()) || 0) + 1);
      });
    });
    const focusedGenres = Array.from(genreCounts.values()).filter(c => c >= 5).length;
    const genreDepthScore = Math.min(20, focusedGenres * 5);

    return Math.min(100, Math.round(orderScore + authorDepthScore + genreDepthScore));
  }

  // Recent reading activity + currently reading + acceleration
  private calculateMomentumScore(books: Book[]): number {
    const now = new Date();
    const threeMonthsAgo = new Date(now);
    threeMonthsAgo.setMonth(threeMonthsAgo.getMonth() - 3);
    const sixMonthsAgo = new Date(now);
    sixMonthsAgo.setMonth(sixMonthsAgo.getMonth() - 6);

    // Recent completions (last 6 months): ~1 per month = ~45pts
    const recentCompletions = books.filter(b =>
      b.readStatus === ReadStatus.READ && b.dateFinished &&
      new Date(b.dateFinished) > sixMonthsAgo
    );
    const recentScore = Math.min(45, recentCompletions.length * 7.5);

    // Currently reading
    const activeBooks = books.filter(b =>
      b.readStatus === ReadStatus.READING || b.readStatus === ReadStatus.RE_READING
    );
    const activeScore = Math.min(30, activeBooks.length * 10);

    // Almost-done books (>70% progress, not yet finished)
    const almostDone = books.filter(b => {
      const p = this.getBookProgress(b);
      return p > 70 && p < 100 && b.readStatus !== ReadStatus.READ;
    });
    const progressScore = Math.min(25, almostDone.length * 8);

    return Math.min(100, Math.round(recentScore + activeScore + progressScore));
  }

  private getBookProgress(book: Book): number {
    return Math.max(
      book.epubProgress?.percentage || 0,
      book.pdfProgress?.percentage || 0,
      book.cbxProgress?.percentage || 0,
      book.koreaderProgress?.percentage || 0,
      book.koboProgress?.percentage || 0
    );
  }

  private getHabitDescription(habitKey: string, score: number): string {
    const level = score < 33 ? 'low' : score < 67 ? 'mid' : 'high';
    return this.t.translate(`statsUser.readingHabits.descriptions.${habitKey}.${level}`);
  }

  private buildHabitInsights(profile: ReadingHabitsProfile): HabitInsight[] {
    const habitColors = ['#9c27b0', '#e91e63', '#ff5722', '#ff9800', '#ffc107', '#4caf50', '#2196f3', '#673ab7'];

    return this.habitKeys.map((key, i) => ({
      habit: this.t.translate(`statsUser.readingHabits.habits.${key}`),
      score: profile[key as keyof ReadingHabitsProfile],
      description: this.getHabitDescription(key, profile[key as keyof ReadingHabitsProfile]),
      color: habitColors[i]
    }));
  }
}
