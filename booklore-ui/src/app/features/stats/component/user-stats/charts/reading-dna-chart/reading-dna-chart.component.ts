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

interface ReadingDNAProfile {
  adventurous: number;
  perfectionist: number;
  intellectual: number;
  emotional: number;
  patient: number;
  social: number;
  nostalgic: number;
  ambitious: number;
}

interface PersonalityInsight {
  trait: string;
  score: number;
  description: string;
  color: string;
}

type ReadingDNAChartData = ChartData<'radar', number[], string>;

@Component({
  selector: 'app-reading-dna-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, Tooltip, TranslocoDirective],
  templateUrl: './reading-dna-chart.component.html',
  styleUrls: ['./reading-dna-chart.component.scss']
})
export class ReadingDNAChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly t = inject(TranslocoService);
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
          callback: (label: string) => {
            const traitKeys = ['adventurous', 'perfectionist', 'intellectual', 'emotional', 'patient', 'social', 'nostalgic', 'ambitious'];
            const icons = ['🌟', '💎', '🧠', '💖', '🕰️', '👥', '📚', '🚀'];
            const translatedLabels = traitKeys.map(k => this.t.translate(`statsUser.readingDna.traits.${k}`));
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
        borderColor: '#e91e63',
        borderWidth: 2,
        cornerRadius: 8,
        padding: 16,
        titleFont: {size: 14, weight: 'bold'},
        bodyFont: {size: 12},
        callbacks: {
          title: (context) => {
            const label = context[0]?.label || '';
            return this.t.translate('statsUser.readingDna.tooltipPersonality', {label});
          },
          label: (context) => {
            const score = context.parsed.r;
            const insight = this.personalityInsights.find(i => i.trait === context.label);

            return [
              this.t.translate('statsUser.readingDna.tooltipScore', {score}),
              '',
              insight ? insight.description : this.t.translate('statsUser.readingDna.tooltipDefaultDescription')
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

  private readonly traitKeys = ['adventurous', 'perfectionist', 'intellectual', 'emotional', 'patient', 'social', 'nostalgic', 'ambitious'];

  private readonly chartDataSubject = new BehaviorSubject<ReadingDNAChartData>({
    labels: [],
    datasets: []
  });

  public readonly chartData$: Observable<ReadingDNAChartData> = this.chartDataSubject.asObservable();
  public personalityInsights: PersonalityInsight[] = [];

  ngOnInit(): void {
    this.bookService.bookState$
      .pipe(
        filter(state => state.loaded),
        first(),
        catchError((error) => {
          console.error('Error processing reading DNA data:', error);
          return EMPTY;
        }),
        takeUntil(this.destroy$)
      )
      .subscribe(() => {
        const profile = this.calculateReadingDNAData();
        this.updateChartData(profile);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateChartData(profile: ReadingDNAProfile | null): void {
    if (!profile) {
      this.chartDataSubject.next({labels: [], datasets: []});
      this.personalityInsights = [];
      return;
    }

    const data = [
      profile.adventurous,
      profile.perfectionist,
      profile.intellectual,
      profile.emotional,
      profile.patient,
      profile.social,
      profile.nostalgic,
      profile.ambitious
    ];

    const gradientColors = [
      '#e91e63', '#2196f3', '#00bcd4', '#ff9800',
      '#9c27b0', '#3f51b5', '#673ab7', '#009688'
    ];

    const translatedLabels = this.traitKeys.map(k => this.t.translate(`statsUser.readingDna.traits.${k}`));

    this.chartDataSubject.next({
      labels: translatedLabels,
      datasets: [{
        label: this.t.translate('statsUser.readingDna.readingDnaProfile'),
        data,
        backgroundColor: 'rgba(233, 30, 99, 0.2)',
        borderColor: '#e91e63',
        borderWidth: 3,
        pointBackgroundColor: gradientColors,
        pointBorderColor: '#ffffff',
        pointBorderWidth: 3,
        pointRadius: 5,
        pointHoverRadius: 8,
        fill: true
      }]
    });

    this.personalityInsights = this.buildPersonalityInsights(profile);
  }

  private calculateReadingDNAData(): ReadingDNAProfile | null {
    const currentState = this.bookService.getCurrentBookState();

    if (!this.isValidBookState(currentState)) {
      return null;
    }

    return this.analyzeReadingDNA(currentState.books!);
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

  private analyzeReadingDNA(books: Book[]): ReadingDNAProfile | null {
    if (books.length === 0) {
      return null;
    }

    return {
      adventurous: this.calculateAdventurousScore(books),
      perfectionist: this.calculatePerfectionistScore(books),
      intellectual: this.calculateIntellectualScore(books),
      emotional: this.calculateEmotionalScore(books),
      patient: this.calculatePatienceScore(books),
      social: this.calculateSocialScore(books),
      nostalgic: this.calculateNostalgicScore(books),
      ambitious: this.calculateAmbitiousScore(books)
    };
  }

  // Genre diversity + language variety
  private calculateAdventurousScore(books: Book[]): number {
    const genres = new Set<string>();
    const languages = new Set<string>();

    books.forEach(book => {
      book.metadata?.categories?.forEach(cat => genres.add(cat.toLowerCase()));
      if (book.metadata?.language) languages.add(book.metadata.language);
    });

    // Having unique genres equal to 40% of book count = max genre diversity
    const diversityRatio = genres.size / Math.max(1, books.length * 0.4);
    const genreScore = Math.min(75, diversityRatio * 75);

    // Each language beyond the first adds 12.5 pts
    const languageScore = Math.min(25, Math.max(0, languages.size - 1) * 12.5);

    return Math.min(100, Math.round(genreScore + languageScore));
  }

  // Completion rate + high personal ratings
  private calculatePerfectionistScore(books: Book[]): number {
    const completedBooks = books.filter(b => b.readStatus === ReadStatus.READ);
    const completionRate = completedBooks.length / books.length;

    const ratedBooks = books.filter(book => book.personalRating);
    const highRatedBooks = ratedBooks.filter(book => book.personalRating! >= 4);
    const highRatingRate = ratedBooks.length > 0 ? highRatedBooks.length / ratedBooks.length : 0;

    return Math.min(100, Math.round(completionRate * 60 + highRatingRate * 40));
  }

  // Non-fiction/academic genre proportion + long books
  private calculateIntellectualScore(books: Book[]): number {
    const intellectualGenres = [
      'philosophy', 'history', 'biography', 'politics', 'psychology',
      'economics', 'mathematics', 'engineering', 'medicine', 'law',
      'education', 'sociology', 'nonfiction', 'non-fiction', 'academic'
    ];

    const intellectualBooks = books.filter(book =>
      this.bookMatchesGenres(book, intellectualGenres)
    );

    const longBooks = books.filter(book =>
      book.metadata?.pageCount && book.metadata.pageCount > 400
    );

    const intellectualRate = intellectualBooks.length / books.length;
    const longBookRate = longBooks.length / books.length;

    return Math.min(100, Math.round(intellectualRate * 70 + longBookRate * 30));
  }

  // Emotionally-driven genre proportion + rating engagement
  private calculateEmotionalScore(books: Book[]): number {
    const emotionalGenres = [
      'romance', 'memoir', 'poetry', 'drama', 'self-help',
      'autobiography', 'literary fiction', 'coming of age'
    ];

    const emotionalBooks = books.filter(book =>
      this.bookMatchesGenres(book, emotionalGenres)
    );

    const ratedBooks = books.filter(book => book.personalRating);
    const ratingEngagement = ratedBooks.length / books.length;
    const emotionalRate = emotionalBooks.length / books.length;

    return Math.min(100, Math.round(emotionalRate * 70 + ratingEngagement * 30));
  }

  // Long books + series reading + in-progress commitment
  private calculatePatienceScore(books: Book[]): number {
    const longBooks = books.filter(book =>
      book.metadata?.pageCount && book.metadata.pageCount > 500
    );

    const seriesBooks = books.filter(book =>
      book.metadata?.seriesName && book.metadata?.seriesNumber
    );

    const progressBooks = books.filter(book => this.getBookProgress(book) > 50);

    const longBookRate = longBooks.length / books.length;
    const seriesRate = seriesBooks.length / books.length;
    const progressRate = progressBooks.length / books.length;

    return Math.min(100, Math.round(longBookRate * 40 + seriesRate * 35 + progressRate * 25));
  }

  // Popular/mainstream genre proportion + high review counts
  private calculateSocialScore(books: Book[]): number {
    const mainstreamGenres = [
      'thriller', 'mystery', 'crime', 'suspense', 'horror',
      'fantasy', 'science fiction', 'adventure', 'true crime',
      'humor', 'graphic novel', 'manga', 'comic'
    ];

    const mainstreamBooks = books.filter(book =>
      this.bookMatchesGenres(book, mainstreamGenres)
    );

    const popularBooks = books.filter(book => {
      const m = book.metadata;
      if (!m) return false;
      return (m.goodreadsReviewCount && m.goodreadsReviewCount > 10000) ||
        (m.amazonReviewCount && m.amazonReviewCount > 2000);
    });

    const mainstreamRate = mainstreamBooks.length / books.length;
    const popularRate = popularBooks.length / books.length;

    return Math.min(100, Math.round(mainstreamRate * 50 + popularRate * 50));
  }

  // Old publication dates + classic genre proportion
  private calculateNostalgicScore(books: Book[]): number {
    const currentYear = new Date().getFullYear();
    const classicThreshold = currentYear - 30;

    const oldBooks = books.filter(book => {
      if (!book.metadata?.publishedDate) return false;
      const pubYear = new Date(book.metadata.publishedDate).getFullYear();
      return pubYear > 0 && pubYear < classicThreshold;
    });

    const classicGenres = [
      'classic', 'mythology', 'folklore', 'fairy tale',
      'ancient', 'medieval', 'victorian', 'gothic'
    ];

    const classicBooks = books.filter(book =>
      this.bookMatchesGenres(book, classicGenres)
    );

    const oldBookRate = oldBooks.length / books.length;
    const classicRate = classicBooks.length / books.length;

    return Math.min(100, Math.round(oldBookRate * 60 + classicRate * 40));
  }

  // Library volume + challenging book proportion + completion of challenging books
  private calculateAmbitiousScore(books: Book[]): number {
    // Need ~100 books to max out the volume component
    const volumeScore = Math.min(40, books.length * 0.4);

    const challengingBooks = books.filter(book =>
      book.metadata?.pageCount && book.metadata.pageCount > 600
    );

    const completedChallenging = challengingBooks.filter(book =>
      book.readStatus === ReadStatus.READ
    );

    const challengingRate = challengingBooks.length / books.length;
    const completionRate = challengingBooks.length > 0 ?
      completedChallenging.length / challengingBooks.length : 0;

    return Math.min(100, Math.round(volumeScore + challengingRate * 35 + completionRate * 25));
  }

  private bookMatchesGenres(book: Book, genres: string[]): boolean {
    if (!book.metadata?.categories) return false;
    return book.metadata.categories.some(cat =>
      genres.some(genre => cat.toLowerCase().includes(genre))
    );
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

  private getTraitDescription(traitKey: string, score: number): string {
    const level = score < 33 ? 'low' : score < 67 ? 'mid' : 'high';
    return this.t.translate(`statsUser.readingDna.descriptions.${traitKey}.${level}`);
  }

  private buildPersonalityInsights(profile: ReadingDNAProfile): PersonalityInsight[] {
    const traitColors = ['#e91e63', '#2196f3', '#00bcd4', '#ff9800', '#9c27b0', '#3f51b5', '#673ab7', '#009688'];

    return this.traitKeys.map((key, i) => ({
      trait: this.t.translate(`statsUser.readingDna.traits.${key}`),
      score: profile[key as keyof ReadingDNAProfile],
      description: this.getTraitDescription(key, profile[key as keyof ReadingDNAProfile]),
      color: traitColors[i]
    }));
  }
}
