import {Component, computed, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {Tooltip} from 'primeng/tooltip';
import {ChartConfiguration, ChartData, ScatterDataPoint} from 'chart.js';
import {BookService} from '../../../../../book/service/book.service';
import {LibraryFilterService} from '../../../library-stats/service/library-filter.service';
import {Book} from '../../../../../book/model/book.model';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

interface TasteQuadrant {
  name: string;
  description: string;
  count: number;
  color: string;
  icon: string;
}

interface BookDataPoint extends ScatterDataPoint {
  bookTitle: string;
  personalRating: number;
  personalRatingNormalized: number;
  externalRating: number;
  quadrant: string;
}

type RatingTasteChartData = ChartData<'scatter', BookDataPoint[], string>;

interface RatingTasteMetrics {
  quadrants: TasteQuadrant[];
  totalRatedBooks: number;
  averageDeviation: number;
  chartData: RatingTasteChartData;
}

@Component({
  selector: 'app-rating-taste-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, Tooltip, TranslocoDirective],
  templateUrl: './rating-taste-chart.component.html',
  styleUrls: ['./rating-taste-chart.component.scss']
})
export class RatingTasteChartComponent {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly t = inject(TranslocoService);
  private readonly metrics = computed<RatingTasteMetrics>(() => {
    if (this.bookService.isBooksLoading()) {
      return this.emptyMetrics();
    }

    return this.calculateMetrics(this.bookService.books(), this.libraryFilterService.selectedLibrary());
  });

  public readonly chartType = 'scatter' as const;
  public readonly quadrants = computed(() => this.metrics().quadrants);
  public readonly totalRatedBooks = computed(() => this.metrics().totalRatedBooks);
  public readonly averageDeviation = computed(() => this.metrics().averageDeviation);

  public readonly chartOptions: ChartConfiguration<'scatter'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    layout: {
      padding: {top: 20, right: 20, bottom: 10, left: 10}
    },
    scales: {
      x: {
        min: 0,
        max: 5,
        title: {
          display: true,
          text: this.t.translate('statsUser.ratingTaste.axisExternalRating'),
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 12,
            weight: 500
          }
        },
        ticks: {
          color: 'rgba(255, 255, 255, 0.8)',
          stepSize: 1,
          font: {
            family: "'Inter', sans-serif",
            size: 11
          }
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.1)',
          drawTicks: true
        }
      },
      y: {
        min: 0,
        max: 5,
        title: {
          display: true,
          text: this.t.translate('statsUser.ratingTaste.axisPersonalRating'),
          color: '#ffffff',
          font: {
            family: "'Inter', sans-serif",
            size: 12,
            weight: 500
          }
        },
        ticks: {
          color: 'rgba(255, 255, 255, 0.8)',
          stepSize: 1,
          font: {
            family: "'Inter', sans-serif",
            size: 11
          }
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.1)',
          drawTicks: true
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
          pointStyle: 'circle',
          padding: 15
        }
      },
      tooltip: {
        enabled: true,
        backgroundColor: 'rgba(0, 0, 0, 0.95)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: '#9c27b0',
        borderWidth: 2,
        cornerRadius: 8,
        padding: 12,
        titleFont: {size: 13, weight: 'bold'},
        bodyFont: {size: 11},
        callbacks: {
          title: (context) => {
            const point = context[0].raw as BookDataPoint;
            return point.bookTitle || this.t.translate('statsUser.ratingTaste.tooltipUnknownBook');
          },
          label: (context) => {
            const point = context.raw as BookDataPoint;
            const diff = point.personalRatingNormalized - point.externalRating;
            const diffText = diff > 0 ? `+${diff.toFixed(1)}` : diff.toFixed(1);
            return [
              this.t.translate('statsUser.ratingTaste.tooltipYourRating', {rating: point.personalRating, normalized: point.personalRatingNormalized.toFixed(1)}),
              this.t.translate('statsUser.ratingTaste.tooltipExternalRating', {rating: point.externalRating.toFixed(1)}),
              this.t.translate('statsUser.ratingTaste.tooltipDifference', {diff: diffText}),
              this.t.translate('statsUser.ratingTaste.tooltipCategory', {category: point.quadrant})
            ];
          }
        }
      }
    },
    elements: {
      point: {
        radius: 6,
        hoverRadius: 9,
        borderWidth: 2
      }
    }
  };

  public readonly chartData = computed(() => this.metrics().chartData);

  private calculateMetrics(books: Book[], selectedLibraryId: string | number | null): RatingTasteMetrics {
    if (books.length === 0) {
      return this.emptyMetrics();
    }

    const filteredBooks = this.filterBooksByLibrary(books, selectedLibraryId);
    const ratedBooks = this.getBooksWithBothRatings(filteredBooks);

    if (ratedBooks.length === 0) {
      return this.emptyMetrics();
    }

    const totalRatedBooks = ratedBooks.length;
    const dataPoints = this.categorizeBooks(ratedBooks);
    const chartData = this.buildChartData(dataPoints);
    const {quadrants, averageDeviation} = this.calculateStatistics(dataPoints);

    return {
      quadrants,
      totalRatedBooks,
      averageDeviation,
      chartData
    };
  }

  private filterBooksByLibrary(books: Book[], selectedLibraryId: string | number | null): Book[] {
    return selectedLibraryId
      ? books.filter(book => book.libraryId === selectedLibraryId)
      : books;
  }

  private getBooksWithBothRatings(books: Book[]): Book[] {
    return books.filter(book => {
      const hasPersonalRating = book.personalRating && book.personalRating > 0;
      const hasExternalRating = this.getExternalRating(book) > 0;
      return hasPersonalRating && hasExternalRating;
    });
  }

  private getExternalRating(book: Book): number {
    const ratings: number[] = [];

    if (book.metadata?.goodreadsRating) ratings.push(book.metadata.goodreadsRating);
    if (book.metadata?.amazonRating) ratings.push(book.metadata.amazonRating);
    if (book.metadata?.hardcoverRating) ratings.push(book.metadata.hardcoverRating);
    if (book.metadata?.lubimyczytacRating) ratings.push(book.metadata.lubimyczytacRating);
    if (book.metadata?.ranobedbRating) ratings.push(book.metadata.ranobedbRating);

    if (ratings.length > 0) {
      return ratings.reduce((sum, rating) => sum + rating, 0) / ratings.length;
    }

    if (book.metadata?.rating) return book.metadata.rating;
    return 0;
  }

  private categorizeBooks(books: Book[]): Map<string, BookDataPoint[]> {
    const categories = new Map<string, BookDataPoint[]>([
      [this.t.translate('statsUser.ratingTaste.quadrantHiddenGems'), []],
      [this.t.translate('statsUser.ratingTaste.quadrantPopularFavorites'), []],
      [this.t.translate('statsUser.ratingTaste.quadrantOverrated'), []],
      [this.t.translate('statsUser.ratingTaste.quadrantAgreedMisses'), []]
    ]);

    books.forEach(book => {
      const personalRating = book.personalRating!;
      // Normalize personal rating from 1-10 to 1-5 scale for comparison
      const personalRatingNormalized = personalRating / 2;
      const externalRating = this.getExternalRating(book);
      const bookTitle = book.metadata?.title || book.fileName || 'Unknown';

      // Use normalized rating (3 is midpoint on 1-5 scale) for quadrant calculation
      let quadrant: string;
      if (personalRatingNormalized >= 3 && externalRating >= 3) {
        quadrant = this.t.translate('statsUser.ratingTaste.quadrantPopularFavorites');
      } else if (personalRatingNormalized >= 3 && externalRating < 3) {
        quadrant = this.t.translate('statsUser.ratingTaste.quadrantHiddenGems');
      } else if (personalRatingNormalized < 3 && externalRating >= 3) {
        quadrant = this.t.translate('statsUser.ratingTaste.quadrantOverrated');
      } else {
        quadrant = this.t.translate('statsUser.ratingTaste.quadrantAgreedMisses');
      }

      const dataPoint: BookDataPoint = {
        x: externalRating,
        y: personalRatingNormalized,
        bookTitle,
        personalRating,
        personalRatingNormalized,
        externalRating,
        quadrant
      };

      categories.get(quadrant)!.push(dataPoint);
    });

    return categories;
  }

  private buildChartData(dataPoints: Map<string, BookDataPoint[]>): RatingTasteChartData {
    const pf = this.t.translate('statsUser.ratingTaste.quadrantPopularFavorites');
    const hg = this.t.translate('statsUser.ratingTaste.quadrantHiddenGems');
    const or = this.t.translate('statsUser.ratingTaste.quadrantOverrated');
    const am = this.t.translate('statsUser.ratingTaste.quadrantAgreedMisses');
    const quadrantColors: Record<string, { bg: string, border: string }> = {
      [pf]: {bg: 'rgba(76, 175, 80, 0.7)', border: '#4caf50'},
      [hg]: {bg: 'rgba(156, 39, 176, 0.7)', border: '#9c27b0'},
      [or]: {bg: 'rgba(255, 152, 0, 0.7)', border: '#ff9800'},
      [am]: {bg: 'rgba(158, 158, 158, 0.7)', border: '#9e9e9e'}
    };

    const datasets = Array.from(dataPoints.entries())
      .filter(([, points]) => points.length > 0)
      .map(([label, points]) => ({
        label: `${label} (${points.length})`,
        data: points,
        backgroundColor: quadrantColors[label].bg,
        borderColor: quadrantColors[label].border,
        pointRadius: 6,
        pointHoverRadius: 9,
        pointBorderWidth: 2
      }));

    return {datasets};
  }

  private calculateStatistics(dataPoints: Map<string, BookDataPoint[]>): Pick<RatingTasteMetrics, 'quadrants' | 'averageDeviation'> {
    const pfKey = this.t.translate('statsUser.ratingTaste.quadrantPopularFavorites');
    const hgKey = this.t.translate('statsUser.ratingTaste.quadrantHiddenGems');
    const orKey = this.t.translate('statsUser.ratingTaste.quadrantOverrated');
    const amKey = this.t.translate('statsUser.ratingTaste.quadrantAgreedMisses');
    const quadrantInfo: Record<string, { description: string, icon: string, color: string }> = {
      [pfKey]: {
        description: this.t.translate('statsUser.ratingTaste.quadrantDescPopularFavorites'),
        icon: '⭐',
        color: '#4caf50'
      },
      [hgKey]: {
        description: this.t.translate('statsUser.ratingTaste.quadrantDescHiddenGems'),
        icon: '💎',
        color: '#9c27b0'
      },
      [orKey]: {
        description: this.t.translate('statsUser.ratingTaste.quadrantDescOverrated'),
        icon: '📉',
        color: '#ff9800'
      },
      [amKey]: {
        description: this.t.translate('statsUser.ratingTaste.quadrantDescAgreedMisses'),
        icon: '👎',
        color: '#9e9e9e'
      }
    };

    const quadrants = Array.from(dataPoints.entries()).map(([name, points]) => ({
      name,
      description: quadrantInfo[name].description,
      count: points.length,
      color: quadrantInfo[name].color,
      icon: quadrantInfo[name].icon
    }));

    // Calculate average deviation from external ratings (using normalized personal rating)
    let totalDeviation = 0;
    let totalPoints = 0;
    dataPoints.forEach(points => {
      points.forEach(point => {
        totalDeviation += Math.abs(point.personalRatingNormalized - point.externalRating);
        totalPoints++;
      });
    });
    const averageDeviation = totalPoints > 0 ? totalDeviation / totalPoints : 0;

    return {quadrants, averageDeviation};
  }

  private emptyMetrics(): RatingTasteMetrics {
    return {
      quadrants: [],
      totalRatedBooks: 0,
      averageDeviation: 0,
      chartData: {datasets: []}
    };
  }
}
