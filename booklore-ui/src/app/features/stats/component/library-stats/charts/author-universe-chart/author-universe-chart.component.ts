import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, switchMap, takeUntil} from 'rxjs/operators';
import {Chart, ChartConfiguration, ChartData} from 'chart.js';
import {LibraryFilterService} from '../../service/library-filter.service';
import {BookService} from '../../../../../book/service/book.service';
import {BookState} from '../../../../../book/model/state/book-state.model';
import {Book, ReadStatus} from '../../../../../book/model/book.model';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

interface AuthorStats {
  name: string;
  bookCount: number;
  totalPages: number;
  avgRating: number;
  readCount: number;
  completionRate: number;
  categories: string[];
  ratingSum: number;
  ratingCount: number;
}

interface BubbleDataPoint {
  x: number;
  y: number;
  r: number;
  authorStats: AuthorStats;
}

type AuthorUniverseChartData = ChartData<'bubble', BubbleDataPoint[], string>;

const COMPLETION_COLORS = {
  high: '#22c55e',      // 75-100% read - green
  medium: '#f59e0b',    // 50-74% read - amber
  low: '#3b82f6',       // 25-49% read - blue
  minimal: '#8b5cf6',   // 1-24% read - purple
  unread: '#6b7280'     // 0% read - gray
};

@Component({
  selector: 'app-author-universe-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, TranslocoDirective],
  templateUrl: './author-universe-chart.component.html',
  styleUrls: ['./author-universe-chart.component.scss']
})
export class AuthorUniverseChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'bubble' as const;
  public chartOptions: ChartConfiguration<'bubble'>['options'];
  public totalAuthors = 0;
  public topAuthors: AuthorStats[] = [];
  public insights: string[] = [];

  private readonly chartDataSubject = new BehaviorSubject<AuthorUniverseChartData>({
    labels: [],
    datasets: []
  });

  public readonly chartData$: Observable<AuthorUniverseChartData> = this.chartDataSubject.asObservable();

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
          console.error('Error processing author universe data:', error);
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
    document.getElementById('author-chart-tooltip')?.remove();
  }

  private initChartOptions(): void {
    this.chartOptions = {
      responsive: true,
      maintainAspectRatio: false,
      layout: {
        padding: {top: 20, right: 20, bottom: 20, left: 20}
      },
      scales: {
        x: {
          title: {
            display: true,
            text: this.t.translate('statsLibrary.authorUniverse.axisBooks'),
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
            precision: 0,
            stepSize: 1
          },
          grid: {
            color: 'rgba(255, 255, 255, 0.08)'
          },
          border: {display: false},
          min: 0
        },
        y: {
          title: {
            display: true,
            text: this.t.translate('statsLibrary.authorUniverse.axisRating'),
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
            callback: (value) => value.toLocaleString()
          },
          grid: {
            color: 'rgba(255, 255, 255, 0.08)'
          },
          border: {display: false},
          min: 0,
          max: 5.5,
          beginAtZero: true
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
            pointStyle: 'circle'
          }
        },
        tooltip: {
          enabled: false,
          external: (context) => this.handleExternalTooltip(context)
        },
        datalabels: {
          display: false
        }
      },
      interaction: {
        intersect: true,
        mode: 'nearest'
      }
    };
  }

  private calculateAndUpdateChart(): void {
    const currentState = this.bookService.getCurrentBookState();
    const selectedLibraryId = this.libraryFilterService.getCurrentSelectedLibrary();

    if (!this.isValidBookState(currentState)) {
      this.chartDataSubject.next({labels: [], datasets: []});
      this.totalAuthors = 0;
      this.topAuthors = [];
      this.insights = [];
      return;
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    const authorStats = this.calculateAuthorStats(filteredBooks);

    this.totalAuthors = authorStats.length;
    this.topAuthors = authorStats.slice(0, 10);
    this.insights = this.generateInsights(authorStats);
    this.updateChartData(authorStats);
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

  private calculateAuthorStats(books: Book[]): AuthorStats[] {
    // Single-pass aggregation - O(n) where n = books * avg_authors_per_book
    const authorMap = new Map<string, AuthorStats>();
    const categorySet = new Map<string, Set<string>>(); // Track unique categories per author

    for (const book of books) {
      const authors = book.metadata?.authors;
      if (!authors || authors.length === 0) continue;

      // Get book's rating once
      const bookRating = book.personalRating ||
        book.metadata?.goodreadsRating ||
        book.metadata?.amazonRating ||
        book.metadata?.hardcoverRating || 0;

      const isRead = book.readStatus === ReadStatus.READ;
      const pageCount = book.metadata?.pageCount || 0;
      const bookCategories = book.metadata?.categories;

      for (const authorName of authors) {
        const normalizedName = authorName.trim();
        if (!normalizedName) continue;

        let stats = authorMap.get(normalizedName);
        if (!stats) {
          stats = {
            name: normalizedName,
            bookCount: 0,
            totalPages: 0,
            avgRating: 0,
            readCount: 0,
            completionRate: 0,
            categories: [],
            ratingSum: 0,
            ratingCount: 0
          };
          authorMap.set(normalizedName, stats);
          categorySet.set(normalizedName, new Set());
        }

        // Aggregate in single pass
        stats.bookCount++;
        stats.totalPages += pageCount;

        if (isRead) {
          stats.readCount++;
        }

        if (bookRating > 0) {
          stats.ratingSum += bookRating;
          stats.ratingCount++;
        }

        // Track unique categories using Set (O(1) lookup)
        if (bookCategories) {
          const catSet = categorySet.get(normalizedName)!;
          for (const cat of bookCategories) {
            catSet.add(cat);
          }
        }
      }
    }

    // Finalize calculations and filter - only process authors with 2+ books
    const results: AuthorStats[] = [];

    for (const [name, stats] of authorMap) {
      if (stats.bookCount < 2) continue; // Skip single-book authors early

      stats.completionRate = (stats.readCount / stats.bookCount) * 100;
      stats.avgRating = stats.ratingCount > 0 ? stats.ratingSum / stats.ratingCount : 0;
      stats.categories = Array.from(categorySet.get(name) || []).slice(0, 5); // Limit to 5 categories

      results.push(stats);
    }

    // Sort and limit to top 50 for rendering performance
    results.sort((a, b) => b.bookCount - a.bookCount);
    return results.slice(0, 50);
  }

  private updateChartData(authorStats: AuthorStats[]): void {
    if (authorStats.length === 0) {
      this.chartDataSubject.next({labels: [], datasets: []});
      return;
    }

    // Group authors by completion rate for different colored datasets
    const highCompletion: BubbleDataPoint[] = [];
    const mediumCompletion: BubbleDataPoint[] = [];
    const lowCompletion: BubbleDataPoint[] = [];
    const minimalCompletion: BubbleDataPoint[] = [];
    const unread: BubbleDataPoint[] = [];

    const maxPages = Math.max(...authorStats.map(s => s.totalPages));

    for (const stats of authorStats) {
      // Scale bubble radius based on total pages (min 5, max 25)
      const normalizedPages = stats.totalPages / maxPages;
      const radius = 5 + (normalizedPages * 20);

      const point: BubbleDataPoint = {
        x: stats.bookCount,
        y: stats.avgRating || 2.5, // Default to 2.5 if no rating
        r: Math.max(5, Math.min(25, radius)),
        authorStats: stats
      };

      if (stats.completionRate >= 75) {
        highCompletion.push(point);
      } else if (stats.completionRate >= 50) {
        mediumCompletion.push(point);
      } else if (stats.completionRate >= 25) {
        lowCompletion.push(point);
      } else if (stats.completionRate > 0) {
        minimalCompletion.push(point);
      } else {
        unread.push(point);
      }
    }

    const datasets: AuthorUniverseChartData['datasets'] = [];

    if (highCompletion.length > 0) {
      datasets.push({
        label: this.t.translate('statsLibrary.authorUniverse.legend75to100'),
        data: highCompletion,
        backgroundColor: this.hexToRgba(COMPLETION_COLORS.high, 0.6),
        borderColor: COMPLETION_COLORS.high,
        borderWidth: 2,
        hoverBorderWidth: 3,
        hoverBorderColor: '#ffffff'
      });
    }

    if (mediumCompletion.length > 0) {
      datasets.push({
        label: this.t.translate('statsLibrary.authorUniverse.legend50to74'),
        data: mediumCompletion,
        backgroundColor: this.hexToRgba(COMPLETION_COLORS.medium, 0.6),
        borderColor: COMPLETION_COLORS.medium,
        borderWidth: 2,
        hoverBorderWidth: 3,
        hoverBorderColor: '#ffffff'
      });
    }

    if (lowCompletion.length > 0) {
      datasets.push({
        label: this.t.translate('statsLibrary.authorUniverse.legend25to49'),
        data: lowCompletion,
        backgroundColor: this.hexToRgba(COMPLETION_COLORS.low, 0.6),
        borderColor: COMPLETION_COLORS.low,
        borderWidth: 2,
        hoverBorderWidth: 3,
        hoverBorderColor: '#ffffff'
      });
    }

    if (minimalCompletion.length > 0) {
      datasets.push({
        label: this.t.translate('statsLibrary.authorUniverse.legend1to24'),
        data: minimalCompletion,
        backgroundColor: this.hexToRgba(COMPLETION_COLORS.minimal, 0.6),
        borderColor: COMPLETION_COLORS.minimal,
        borderWidth: 2,
        hoverBorderWidth: 3,
        hoverBorderColor: '#ffffff'
      });
    }

    if (unread.length > 0) {
      datasets.push({
        label: this.t.translate('statsLibrary.authorUniverse.legendUnread'),
        data: unread,
        backgroundColor: this.hexToRgba(COMPLETION_COLORS.unread, 0.6),
        borderColor: COMPLETION_COLORS.unread,
        borderWidth: 2,
        hoverBorderWidth: 3,
        hoverBorderColor: '#ffffff'
      });
    }

    this.chartDataSubject.next({
      labels: [],
      datasets
    });
  }

  private generateInsights(authorStats: AuthorStats[]): string[] {
    const insights: string[] = [];

    if (authorStats.length === 0) return insights;

    // Most prolific author
    const mostProlific = authorStats[0];
    if (mostProlific) {
      insights.push(this.t.translate('statsLibrary.authorUniverse.insightMostCollected', {name: mostProlific.name, count: mostProlific.bookCount}));
    }

    // Highest rated author (with at least 2 books)
    const ratedAuthors = authorStats.filter(a => a.avgRating > 0);
    if (ratedAuthors.length > 0) {
      const highestRated = ratedAuthors.reduce((a, b) => a.avgRating > b.avgRating ? a : b);
      insights.push(this.t.translate('statsLibrary.authorUniverse.insightHighestRated', {name: highestRated.name, rating: `${highestRated.avgRating.toFixed(1)}\u2605`}));
    }

    // Most pages by author
    const mostPages = authorStats.reduce((a, b) => a.totalPages > b.totalPages ? a : b);
    if (mostPages.totalPages > 0) {
      insights.push(this.t.translate('statsLibrary.authorUniverse.insightMostPages', {name: mostPages.name, count: mostPages.totalPages.toLocaleString()}));
    }

    // Best completion rate (with at least 3 books)
    const completionCandidates = authorStats.filter(a => a.bookCount >= 3);
    if (completionCandidates.length > 0) {
      const bestCompletion = completionCandidates.reduce((a, b) =>
        a.completionRate > b.completionRate ? a : b
      );
      if (bestCompletion.completionRate > 0) {
        insights.push(this.t.translate('statsLibrary.authorUniverse.insightMostRead', {name: bestCompletion.name, percent: Math.round(bestCompletion.completionRate)}));
      }
    }

    // Hidden gem - high rated but fewer books (quality over quantity)
    const hiddenGems = ratedAuthors.filter(a => a.bookCount <= 3 && a.avgRating >= 4.0);
    if (hiddenGems.length > 0) {
      const gem = hiddenGems.reduce((a, b) => a.avgRating > b.avgRating ? a : b);
      insights.push(this.t.translate('statsLibrary.authorUniverse.insightHiddenGem', {name: gem.name, rating: `${gem.avgRating.toFixed(1)}\u2605`, count: gem.bookCount}));
    }

    // Biggest backlog - author with most unread books
    const authorsWithBacklog = authorStats.filter(a => a.bookCount - a.readCount > 0);
    if (authorsWithBacklog.length > 0) {
      const biggestBacklog = authorsWithBacklog.reduce((a, b) =>
        (a.bookCount - a.readCount) > (b.bookCount - b.readCount) ? a : b
      );
      const unreadCount = biggestBacklog.bookCount - biggestBacklog.readCount;
      if (unreadCount >= 2) {
        insights.push(this.t.translate('statsLibrary.authorUniverse.insightBiggestBacklog', {name: biggestBacklog.name, count: unreadCount}));
      }
    }

    // Author concentration - what % of total books come from top 3 authors
    if (authorStats.length >= 3) {
      const totalBooks = authorStats.reduce((sum, a) => sum + a.bookCount, 0);
      const top3Books = authorStats.slice(0, 3).reduce((sum, a) => sum + a.bookCount, 0);
      const concentration = Math.round((top3Books / totalBooks) * 100);
      if (concentration >= 25) {
        insights.push(this.t.translate('statsLibrary.authorUniverse.insightTop3Concentration', {percent: concentration}));
      }
    }

    // Longest reads - author with highest avg pages per book
    const authorsWithPages = authorStats.filter(a => a.totalPages > 0);
    if (authorsWithPages.length > 0) {
      const longestReads = authorsWithPages.reduce((a, b) =>
        (a.totalPages / a.bookCount) > (b.totalPages / b.bookCount) ? a : b
      );
      const avgPages = Math.round(longestReads.totalPages / longestReads.bookCount);
      if (avgPages >= 300) {
        insights.push(this.t.translate('statsLibrary.authorUniverse.insightLongestReads', {name: longestReads.name, pages: avgPages}));
      }
    }

    // Most versatile - author appearing in most genres
    const versatileAuthors = authorStats.filter(a => a.categories.length >= 3);
    if (versatileAuthors.length > 0) {
      const mostVersatile = versatileAuthors.reduce((a, b) =>
        a.categories.length > b.categories.length ? a : b
      );
      insights.push(this.t.translate('statsLibrary.authorUniverse.insightMostVersatile', {name: mostVersatile.name, count: mostVersatile.categories.length}));
    }

    // Completely unread - authors with 0% completion but multiple books
    const completelyUnread = authorStats.filter(a => a.completionRate === 0 && a.bookCount >= 2);
    if (completelyUnread.length > 0) {
      const topUnread = completelyUnread.reduce((a, b) => a.bookCount > b.bookCount ? a : b);
      insights.push(this.t.translate('statsLibrary.authorUniverse.insightUntouchedAuthor', {name: topUnread.name, count: topUnread.bookCount}));
    }

    // Total reading commitment
    const totalPages = authorStats.reduce((sum, a) => sum + a.totalPages, 0);
    const totalRead = authorStats.reduce((sum, a) => sum + (a.totalPages * a.completionRate / 100), 0);
    if (totalPages > 0) {
      const overallProgress = Math.round((totalRead / totalPages) * 100);
      insights.push(this.t.translate('statsLibrary.authorUniverse.insightOverallProgress', {percent: overallProgress}));
    }

    return insights;
  }

  private handleExternalTooltip(context: { chart: Chart; tooltip: any }): void {
    const {chart, tooltip} = context;
    let tooltipEl = document.getElementById('author-chart-tooltip');

    if (!tooltipEl) {
      tooltipEl = document.createElement('div');
      tooltipEl.id = 'author-chart-tooltip';
      Object.assign(tooltipEl.style, {
        position: 'fixed',
        zIndex: '9999',
        background: 'rgba(0, 0, 0, 0.95)',
        border: '2px solid #8b5cf6',
        borderRadius: '8px',
        padding: '12px 16px',
        pointerEvents: 'none',
        opacity: '0',
        transition: 'opacity 0.15s ease',
        transform: 'translate(-50%, calc(-100% - 12px))',
        maxWidth: '280px',
        whiteSpace: 'nowrap',
        fontFamily: "'Inter', sans-serif"
      });
      document.body.appendChild(tooltipEl);
    }

    if (tooltip.opacity === 0) {
      tooltipEl.style.opacity = '0';
      return;
    }

    // Only use the first (nearest) data point
    const dataPoint = tooltip.dataPoints?.[0];
    if (!dataPoint) {
      tooltipEl.style.opacity = '0';
      return;
    }

    const raw = dataPoint.raw as BubbleDataPoint;
    const stats = raw.authorStats;

    const ratingText = stats.avgRating > 0
      ? `${stats.avgRating.toFixed(2)} \u2605`
      : this.t.translate('statsLibrary.authorUniverse.tooltipNoRatings');

    const categoriesHtml = stats.categories.length > 0
      ? `<div style="color:rgba(255,255,255,0.9);font-size:12px;line-height:1.6">${this.t.translate('statsLibrary.authorUniverse.tooltipGenres', {genres: stats.categories.slice(0, 3).join(', ')})}</div>`
      : '';

    const booksLine = this.t.translate('statsLibrary.authorUniverse.tooltipBooks', {count: stats.bookCount});
    const pagesLine = this.t.translate('statsLibrary.authorUniverse.tooltipTotalPages', {count: stats.totalPages.toLocaleString()});
    const ratingLine = this.t.translate('statsLibrary.authorUniverse.tooltipAvgRating', {rating: ratingText});
    const readLine = this.t.translate('statsLibrary.authorUniverse.tooltipRead', {read: stats.readCount, total: stats.bookCount, percent: Math.round(stats.completionRate)});

    tooltipEl.innerHTML = `
      <div style="color:#fff;font-size:14px;font-weight:700;margin-bottom:6px">${stats.name}</div>
      <div style="color:rgba(255,255,255,0.9);font-size:12px;line-height:1.6">${booksLine}</div>
      <div style="color:rgba(255,255,255,0.9);font-size:12px;line-height:1.6">${pagesLine}</div>
      <div style="color:rgba(255,255,255,0.9);font-size:12px;line-height:1.6">${ratingLine}</div>
      <div style="color:rgba(255,255,255,0.9);font-size:12px;line-height:1.6">${readLine}</div>
      ${categoriesHtml}
    `;

    const canvasRect = chart.canvas.getBoundingClientRect();
    tooltipEl.style.opacity = '1';
    tooltipEl.style.left = (canvasRect.left + tooltip.caretX) + 'px';
    tooltipEl.style.top = (canvasRect.top + tooltip.caretY) + 'px';
  }

  private hexToRgba(hex: string, alpha: number): string {
    const r = parseInt(hex.slice(1, 3), 16);
    const g = parseInt(hex.slice(3, 5), 16);
    const b = parseInt(hex.slice(5, 7), 16);
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
  }
}
