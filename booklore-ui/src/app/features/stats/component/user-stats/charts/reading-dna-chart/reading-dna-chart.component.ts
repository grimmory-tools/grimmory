import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BookService} from '../../../../../book/service/book.service';
import {BookState} from '../../../../../book/model/state/book-state.model';
import {Book, ReadStatus} from '../../../../../book/model/book.model';

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
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './reading-dna-chart.component.html',
  styleUrls: ['./reading-dna-chart.component.scss']
})
export class ReadingDNAChartComponent implements OnInit, OnDestroy {
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
              'Adventurous': '🌟',
              'Perfectionist': '💎',
              'Intellectual': '🧠',
              'Emotional': '💖',
              'Patient': '🕰️',
              'Social': '👥',
              'Nostalgic': '📚',
              'Ambitious': '🚀'
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
        borderColor: '#e91e63',
        borderWidth: 2,
        cornerRadius: 8,
        padding: 16,
        titleFont: {size: 14, weight: 'bold'},
        bodyFont: {size: 12},
        callbacks: {
          title: (context) => {
            const label = context[0]?.label || '';
            return `${label} Personality`;
          },
          label: (context) => {
            const score = context.parsed.r;
            const insight = this.personalityInsights.find(i => i.trait === context.label);

            return [
              `Score: ${score.toFixed(1)}/100`,
              '',
              insight ? insight.description : 'Your reading personality trait'
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

  private readonly chartDataSubject = new BehaviorSubject<ReadingDNAChartData>({
    labels: [
      'Adventurous', 'Perfectionist', 'Intellectual', 'Emotional',
      'Patient', 'Social', 'Nostalgic', 'Ambitious'
    ],
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
    try {
      if (!profile) {
        this.chartDataSubject.next({
          labels: [],
          datasets: []
        });
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

      this.chartDataSubject.next({
        labels: [
          'Adventurous', 'Perfectionist', 'Intellectual', 'Emotional',
          'Patient', 'Social', 'Nostalgic', 'Ambitious'
        ],
        datasets: [{
          label: 'Reading DNA Profile',
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

      this.personalityInsights = this.convertToPersonalityInsights(profile);
    } catch (error) {
      console.error('Error updating reading DNA chart data:', error);
    }
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

  private analyzeReadingDNA(books: Book[]): ReadingDNAProfile {
    if (books.length === 0) {
      return this.getDefaultProfile();
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

  private calculateAdventurousScore(books: Book[]): number {
    const genres = new Set<string>();
    const languages = new Set<string>();
    const formats = new Set<string>();

    books.forEach(book => {
      book.metadata?.categories?.forEach(cat => genres.add(cat.toLowerCase()));
      if (book.metadata?.language) languages.add(book.metadata.language);
      formats.add(book.bookType);
    });

    const genreScore = Math.min(60, genres.size * 4);
    const languageScore = Math.min(20, languages.size * 10);
    const formatScore = Math.min(20, formats.size * 7);

    return genreScore + languageScore + formatScore;
  }

  private calculatePerfectionistScore(books: Book[]): number {
    const completedBooks = books.filter(b => b.readStatus === ReadStatus.READ);
    const completionRate = completedBooks.length / books.length;

    const qualityBooks = books.filter(book => {
      const metadata = book.metadata;
      if (!metadata) return false;
      return (metadata.goodreadsRating && metadata.goodreadsRating >= 4.0) ||
        (metadata.amazonRating && metadata.amazonRating >= 4.0) ||
        (book.personalRating && book.personalRating >= 4);
    });

    const qualityRate = qualityBooks.length / books.length;
    const completionScore = completionRate * 60;
    const qualityScore = qualityRate * 40;

    return Math.min(100, completionScore + qualityScore);
  }

  private calculateIntellectualScore(books: Book[]): number {
    const intellectualGenres = [
      'philosophy', 'science', 'history', 'biography', 'politics',
      'psychology', 'sociology', 'economics', 'technology', 'mathematics',
      'physics', 'chemistry', 'medicine', 'law', 'education',
      'anthropology', 'archaeology', 'astronomy', 'biology', 'geology',
      'linguistics', 'neuroscience', 'quantum physics', 'engineering',
      'computer science', 'artificial intelligence', 'data science',
      'research', 'academic', 'scholarly', 'theoretical', 'scientific',
      'analytical', 'critical thinking', 'logic', 'rhetoric',
      'cultural studies', 'international relations', 'diplomacy',
      'public policy', 'governance', 'constitutional law', 'ethics',
      'moral philosophy', 'epistemology', 'metaphysics', 'theology',
      'religious studies', 'comparative religion', 'apologetics'
    ];

    const intellectualBooks = books.filter(book => {
      if (!book.metadata?.categories) return false;
      return book.metadata.categories.some(cat =>
        intellectualGenres.some(genre => cat.toLowerCase().includes(genre))
      );
    });

    const longBooks = books.filter(book =>
      book.metadata?.pageCount && book.metadata.pageCount > 400
    );

    const intellectualRate = intellectualBooks.length / books.length;
    const longBookRate = longBooks.length / books.length;

    return Math.min(100, (intellectualRate * 70) + (longBookRate * 30));
  }

  private calculateEmotionalScore(books: Book[]): number {
    const emotionalGenres = [
      'fiction', 'romance', 'drama', 'literary', 'contemporary',
      'memoir', 'poetry', 'young adult', 'coming of age', 'family',
      'love story', 'relationships', 'emotional', 'heartbreak',
      'healing', 'self-help', 'personal development', 'inspirational',
      'motivational', 'spiritual', 'mindfulness', 'meditation',
      'grief', 'loss', 'trauma', 'recovery', 'therapy',
      'women\'s fiction', 'chick lit', 'new adult', 'teen',
      'childhood', 'parenting', 'motherhood', 'fatherhood',
      'friendship', 'betrayal', 'forgiveness', 'redemption',
      'slice of life', 'domestic fiction', 'family saga',
      'generational saga', 'multicultural', 'immigrant stories',
      'lgbtq+', 'queer fiction', 'feminist', 'gender studies',
      'social issues', 'mental health', 'addiction', 'wellness',
      'autobiography', 'personal narrative', 'diary', 'journal'
    ];

    const emotionalBooks = books.filter(book => {
      if (!book.metadata?.categories) return false;
      return book.metadata.categories.some(cat =>
        emotionalGenres.some(genre => cat.toLowerCase().includes(genre))
      );
    });

    const personallyRatedBooks = books.filter(book => book.personalRating);

    const emotionalRate = emotionalBooks.length / books.length;
    const ratingEngagement = personallyRatedBooks.length / books.length;

    return Math.min(100, (emotionalRate * 60) + (ratingEngagement * 40));
  }

  private calculatePatienceScore(books: Book[]): number {
    const longBooks = books.filter(book =>
      book.metadata?.pageCount && book.metadata.pageCount > 500
    );

    const seriesBooks = books.filter(book =>
      book.metadata?.seriesName && book.metadata?.seriesNumber
    );

    const progressBooks = books.filter(book => {
      const progress = Math.max(
        book.epubProgress?.percentage || 0,
        book.pdfProgress?.percentage || 0,
        book.cbxProgress?.percentage || 0,
        book.koreaderProgress?.percentage || 0,
        book.koboProgress?.percentage || 0
      );
      return progress > 50;
    });

    const longBookRate = longBooks.length / books.length;
    const seriesRate = seriesBooks.length / books.length;
    const progressRate = progressBooks.length / books.length;

    return Math.min(100, (longBookRate * 40) + (seriesRate * 35) + (progressRate * 25));
  }

  private calculateSocialScore(books: Book[]): number {
    const popularBooks = books.filter(book => {
      const metadata = book.metadata;
      if (!metadata) return false;
      return (metadata.goodreadsReviewCount && metadata.goodreadsReviewCount > 1000) ||
        (metadata.amazonReviewCount && metadata.amazonReviewCount > 500);
    });

    const mainstreamGenres = [
      'thriller', 'mystery', 'romance', 'fantasy', 'science fiction',
      'horror', 'adventure', 'bestseller', 'contemporary', 'popular',
      'crime', 'detective', 'suspense', 'action', 'espionage',
      'spy', 'police procedural', 'cozy mystery', 'psychological thriller',
      'domestic thriller', 'legal thriller', 'medical thriller',
      'urban fantasy', 'paranormal', 'supernatural', 'magic',
      'dystopian', 'post-apocalyptic', 'cyberpunk', 'space opera',
      'military science fiction', 'hard science fiction', 'steampunk',
      'alternate history', 'time travel', 'vampire', 'werewolf',
      'zombie', 'ghost', 'gothic', 'dark fantasy', 'epic fantasy',
      'sword and sorcery', 'high fantasy', 'historical romance',
      'regency romance', 'western', 'sports', 'celebrity',
      'entertainment', 'pop culture', 'reality tv', 'social media',
      'true crime', 'celebrity biography', 'gossip', 'lifestyle',
      'fashion', 'beauty', 'cooking', 'travel', 'humor',
      'comedy', 'satire', 'graphic novel', 'manga', 'comic'
    ];

    const mainstreamBooks = books.filter(book => {
      if (!book.metadata?.categories) return false;
      return book.metadata.categories.some(cat =>
        mainstreamGenres.some(genre => cat.toLowerCase().includes(genre))
      );
    });

    const popularRate = popularBooks.length / books.length;
    const mainstreamRate = mainstreamBooks.length / books.length;

    return Math.min(100, (popularRate * 50) + (mainstreamRate * 50));
  }

  private calculateNostalgicScore(books: Book[]): number {
    const currentYear = new Date().getFullYear();
    const classicThreshold = currentYear - 30;

    const oldBooks = books.filter(book => {
      if (!book.metadata?.publishedDate) return false;
      const pubYear = new Date(book.metadata.publishedDate).getFullYear();
      return pubYear < classicThreshold;
    });

    const classicGenres = [
      'classic', 'literature', 'historical', 'vintage', 'traditional',
      'heritage', 'timeless', 'canonical', 'masterpiece', 'landmark',
      'seminal', 'influential', 'groundbreaking', 'pioneering',
      'classical literature', 'world literature', 'nobel prize',
      'pulitzer prize', 'booker prize', 'national book award',
      'literary fiction', 'modernist', 'post-modernist', 'realist',
      'naturalist', 'romantic', 'victorian', 'edwardian',
      'renaissance', 'enlightenment', 'ancient', 'medieval',
      'colonial', 'antebellum', 'gilded age', 'jazz age',
      'lost generation', 'beat generation', 'harlem renaissance',
      'golden age', 'silver age', 'folk tales', 'fairy tales',
      'mythology', 'legends', 'folklore', 'oral tradition',
      'epic poetry', 'sonnets', 'ballads', 'odes',
      'dramatic works', 'shakespearean', 'greek tragedy',
      'roman literature', 'biblical', 'religious classics',
      'philosophical classics', 'historical classics'
    ];

    const oldBookRate = oldBooks.length / books.length;
    const classicRate = classicGenres.length / books.length;

    return Math.min(100, (oldBookRate * 60) + (classicRate * 40));
  }

  private calculateAmbitiousScore(books: Book[]): number {
    const totalBooks = books.length;
    const volumeScore = Math.min(40, totalBooks * 2);

    const challengingBooks = books.filter(book =>
      book.metadata?.pageCount && book.metadata.pageCount > 600
    );

    const completedChallenging = challengingBooks.filter(book =>
      book.readStatus === ReadStatus.READ
    );

    const challengingRate = challengingBooks.length / books.length;
    const completionRate = challengingBooks.length > 0 ?
      completedChallenging.length / challengingBooks.length : 0;

    const challengingScore = challengingRate * 35;
    const completionBonus = completionRate * 25;

    return Math.min(100, volumeScore + challengingScore + completionBonus);
  }

  private getDefaultProfile(): ReadingDNAProfile {
    return {
      adventurous: 50,
      perfectionist: 50,
      intellectual: 50,
      emotional: 50,
      patient: 50,
      social: 50,
      nostalgic: 50,
      ambitious: 50
    };
  }

  private convertToPersonalityInsights(profile: ReadingDNAProfile): PersonalityInsight[] {
    return [
      {
        trait: 'Adventurous',
        score: profile.adventurous,
        description: 'You explore diverse genres and experimental content',
        color: '#e91e63'
      },
      {
        trait: 'Perfectionist',
        score: profile.perfectionist,
        description: 'You prefer high-quality books and finish what you start',
        color: '#2196f3'
      },
      {
        trait: 'Intellectual',
        score: profile.intellectual,
        description: 'You gravitate toward complex, educational material',
        color: '#00bcd4'
      },
      {
        trait: 'Emotional',
        score: profile.emotional,
        description: 'You connect emotionally with fiction and personal stories',
        color: '#ff9800'
      },
      {
        trait: 'Patient',
        score: profile.patient,
        description: 'You tackle long books and complete series',
        color: '#9c27b0'
      },
      {
        trait: 'Social',
        score: profile.social,
        description: 'You enjoy popular, widely-discussed books',
        color: '#3f51b5'
      },
      {
        trait: 'Nostalgic',
        score: profile.nostalgic,
        description: 'You appreciate classic literature and older works',
        color: '#673ab7'
      },
      {
        trait: 'Ambitious',
        score: profile.ambitious,
        description: 'You challenge yourself with volume and difficulty',
        color: '#009688'
      }
    ];
  }
}
