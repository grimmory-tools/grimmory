import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, switchMap, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {LibraryFilterService} from '../../service/library-filter.service';
import {BookService} from '../../../../../book/service/book.service';
import {BookState} from '../../../../../book/model/state/book-state.model';
import {Book} from '../../../../../book/model/book.model';

interface LanguageStats {
  language: string;
  displayName: string;
  count: number;
  percentage: number;
}

type LanguageChartData = ChartData<'pie', number[], string>;

// Professional color palette for languages
const LANGUAGE_COLORS = [
  '#2563EB', // Blue
  '#0D9488', // Teal
  '#7C3AED', // Violet
  '#DC2626', // Red
  '#F59E0B', // Amber
  '#16A34A', // Green
  '#EC4899', // Pink
  '#8B5CF6', // Purple
  '#06B6D4', // Cyan
  '#EA580C', // Orange
  '#6366F1', // Indigo
  '#14B8A6', // Teal-500
  '#F43F5E', // Rose
  '#84CC16', // Lime
  '#A855F7'  // Purple-500
] as const;

// Common language code to display name mapping
const LANGUAGE_NAMES: Record<string, string> = {
  'en': 'English',
  'eng': 'English',
  'english': 'English',
  'es': 'Spanish',
  'spa': 'Spanish',
  'spanish': 'Spanish',
  'fr': 'French',
  'fra': 'French',
  'french': 'French',
  'de': 'German',
  'deu': 'German',
  'german': 'German',
  'it': 'Italian',
  'ita': 'Italian',
  'italian': 'Italian',
  'pt': 'Portuguese',
  'por': 'Portuguese',
  'portuguese': 'Portuguese',
  'ru': 'Russian',
  'rus': 'Russian',
  'russian': 'Russian',
  'zh': 'Chinese',
  'zho': 'Chinese',
  'chinese': 'Chinese',
  'ja': 'Japanese',
  'jpn': 'Japanese',
  'japanese': 'Japanese',
  'ko': 'Korean',
  'kor': 'Korean',
  'korean': 'Korean',
  'pl': 'Polish',
  'pol': 'Polish',
  'polish': 'Polish',
  'nl': 'Dutch',
  'nld': 'Dutch',
  'dutch': 'Dutch',
  'sv': 'Swedish',
  'swe': 'Swedish',
  'swedish': 'Swedish',
  'ar': 'Arabic',
  'ara': 'Arabic',
  'arabic': 'Arabic',
  'hi': 'Hindi',
  'hin': 'Hindi',
  'hindi': 'Hindi',
  'tr': 'Turkish',
  'tur': 'Turkish',
  'turkish': 'Turkish',
  'cs': 'Czech',
  'ces': 'Czech',
  'czech': 'Czech',
  'da': 'Danish',
  'dan': 'Danish',
  'danish': 'Danish',
  'fi': 'Finnish',
  'fin': 'Finnish',
  'finnish': 'Finnish',
  'no': 'Norwegian',
  'nor': 'Norwegian',
  'norwegian': 'Norwegian',
  'uk': 'Ukrainian',
  'ukr': 'Ukrainian',
  'ukrainian': 'Ukrainian',
  'he': 'Hebrew',
  'heb': 'Hebrew',
  'hebrew': 'Hebrew',
  'el': 'Greek',
  'ell': 'Greek',
  'greek': 'Greek',
  'hu': 'Hungarian',
  'hun': 'Hungarian',
  'hungarian': 'Hungarian',
  'ro': 'Romanian',
  'ron': 'Romanian',
  'romanian': 'Romanian',
  'th': 'Thai',
  'tha': 'Thai',
  'thai': 'Thai',
  'vi': 'Vietnamese',
  'vie': 'Vietnamese',
  'vietnamese': 'Vietnamese',
  'id': 'Indonesian',
  'ind': 'Indonesian',
  'indonesian': 'Indonesian',
  'ms': 'Malay',
  'msa': 'Malay',
  'malay': 'Malay'
};

@Component({
  selector: 'app-language-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './language-chart.component.html',
  styleUrls: ['./language-chart.component.scss']
})
export class LanguageChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'pie' as const;
  public languageStats: LanguageStats[] = [];
  public totalBooks = 0;
  public booksWithLanguage = 0;

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
        borderColor: '#2563EB',
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
            return `${context.label}: ${value} books (${percentage}%)`;
          }
        }
      },
      datalabels: {
        display: false
      }
    }
  };

  private readonly chartDataSubject = new BehaviorSubject<LanguageChartData>({
    labels: [],
    datasets: []
  });

  public readonly chartData$: Observable<LanguageChartData> = this.chartDataSubject.asObservable();

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
          console.error('Error processing language data:', error);
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
      this.languageStats = [];
      this.totalBooks = 0;
      this.booksWithLanguage = 0;
      return;
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    this.totalBooks = filteredBooks.length;

    if (filteredBooks.length === 0) {
      this.chartDataSubject.next({labels: [], datasets: []});
      this.languageStats = [];
      return;
    }

    const stats = this.calculateLanguageStats(filteredBooks);
    this.languageStats = stats;
    this.booksWithLanguage = stats.reduce((sum, s) => sum + s.count, 0);
    this.updateChartData(stats);
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

  private calculateLanguageStats(books: Book[]): LanguageStats[] {
    const languageCounts = new Map<string, number>();

    books.forEach(book => {
      const language = book.metadata?.language?.trim().toLowerCase();
      if (language) {
        // Normalize the language to a display name
        const normalizedKey = this.normalizeLanguage(language);
        languageCounts.set(normalizedKey, (languageCounts.get(normalizedKey) || 0) + 1);
      }
    });

    const total = Array.from(languageCounts.values()).reduce((a, b) => a + b, 0);

    return Array.from(languageCounts.entries())
      .map(([language, count]) => ({
        language,
        displayName: this.getDisplayName(language),
        count,
        percentage: (count / total) * 100
      }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 15); // Show top 15 languages
  }

  private normalizeLanguage(language: string): string {
    const lower = language.toLowerCase().trim();
    // Check if it maps to a known language
    if (LANGUAGE_NAMES[lower]) {
      return lower;
    }
    return lower;
  }

  private getDisplayName(language: string): string {
    const lower = language.toLowerCase();
    if (LANGUAGE_NAMES[lower]) {
      return LANGUAGE_NAMES[lower];
    }
    // Capitalize first letter if no mapping found
    return language.charAt(0).toUpperCase() + language.slice(1);
  }

  private updateChartData(stats: LanguageStats[]): void {
    const labels = stats.map(s => s.displayName);
    const data = stats.map(s => s.count);
    const colors = stats.map((_, index) => LANGUAGE_COLORS[index % LANGUAGE_COLORS.length]);

    this.chartDataSubject.next({
      labels,
      datasets: [{
        data,
        backgroundColor: colors,
        borderColor: colors.map(() => 'rgba(255, 255, 255, 0.2)'),
        borderWidth: 2,
        hoverBorderColor: '#ffffff',
        hoverBorderWidth: 3
      }]
    });
  }
}
