import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, switchMap, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData, ScatterDataPoint} from 'chart.js';
import {BookService} from '../../../../../book/service/book.service';
import {LibraryFilterService} from '../../../library-stats/service/library-filter.service';
import {BookState} from '../../../../../book/model/state/book-state.model';
import {Book} from '../../../../../book/model/book.model';

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

@Component({
  selector: 'app-rating-taste-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './rating-taste-chart.component.html',
  styleUrls: ['./rating-taste-chart.component.scss']
})
export class RatingTasteChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'scatter' as const;
  public quadrants: TasteQuadrant[] = [];
  public totalRatedBooks = 0;
  public averageDeviation = 0;

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
          text: 'External Rating (1-5 scale)',
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
          text: 'Your Personal Rating (normalized from 1-10)',
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
            return point.bookTitle || 'Unknown Book';
          },
          label: (context) => {
            const point = context.raw as BookDataPoint;
            const diff = point.personalRatingNormalized - point.externalRating;
            const diffText = diff > 0 ? `+${diff.toFixed(1)}` : diff.toFixed(1);
            return [
              `Your Rating: ${point.personalRating}/10 (${point.personalRatingNormalized.toFixed(1)}/5)`,
              `External Rating: ${point.externalRating.toFixed(1)}/5`,
              `Difference: ${diffText} (on 5-point scale)`,
              `Category: ${point.quadrant}`
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

  private readonly chartDataSubject = new BehaviorSubject<RatingTasteChartData>({
    datasets: []
  });

  public readonly chartData$: Observable<RatingTasteChartData> = this.chartDataSubject.asObservable();

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
          console.error('Error processing rating taste data:', error);
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
      this.chartDataSubject.next({datasets: []});
      this.quadrants = [];
      return;
    }

    const filteredBooks = this.filterBooksByLibrary(currentState.books!, selectedLibraryId);
    const ratedBooks = this.getBooksWithBothRatings(filteredBooks);

    if (ratedBooks.length === 0) {
      this.chartDataSubject.next({datasets: []});
      this.quadrants = [];
      this.totalRatedBooks = 0;
      return;
    }

    this.totalRatedBooks = ratedBooks.length;
    const dataPoints = this.categorizeBooks(ratedBooks);
    this.updateChartData(dataPoints);
    this.calculateStatistics(dataPoints);
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
      ['Hidden Gems', []],
      ['Popular Favorites', []],
      ['Overrated', []],
      ['Agreed Misses', []]
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
        quadrant = 'Popular Favorites';
      } else if (personalRatingNormalized >= 3 && externalRating < 3) {
        quadrant = 'Hidden Gems';
      } else if (personalRatingNormalized < 3 && externalRating >= 3) {
        quadrant = 'Overrated';
      } else {
        quadrant = 'Agreed Misses';
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

  private updateChartData(dataPoints: Map<string, BookDataPoint[]>): void {
    const quadrantColors: Record<string, { bg: string, border: string }> = {
      'Popular Favorites': {bg: 'rgba(76, 175, 80, 0.7)', border: '#4caf50'},
      'Hidden Gems': {bg: 'rgba(156, 39, 176, 0.7)', border: '#9c27b0'},
      'Overrated': {bg: 'rgba(255, 152, 0, 0.7)', border: '#ff9800'},
      'Agreed Misses': {bg: 'rgba(158, 158, 158, 0.7)', border: '#9e9e9e'}
    };

    const datasets = Array.from(dataPoints.entries())
      .filter(([_, points]) => points.length > 0)
      .map(([label, points]) => ({
        label: `${label} (${points.length})`,
        data: points,
        backgroundColor: quadrantColors[label].bg,
        borderColor: quadrantColors[label].border,
        pointRadius: 6,
        pointHoverRadius: 9,
        pointBorderWidth: 2
      }));

    this.chartDataSubject.next({datasets});
  }

  private calculateStatistics(dataPoints: Map<string, BookDataPoint[]>): void {
    const quadrantInfo: Record<string, { description: string, icon: string, color: string }> = {
      'Popular Favorites': {
        description: 'Books you and the public both love',
        icon: '⭐',
        color: '#4caf50'
      },
      'Hidden Gems': {
        description: 'Books you rate higher than the public',
        icon: '💎',
        color: '#9c27b0'
      },
      'Overrated': {
        description: 'Popular books that disappointed you',
        icon: '📉',
        color: '#ff9800'
      },
      'Agreed Misses': {
        description: 'Books neither you nor public enjoyed',
        icon: '👎',
        color: '#9e9e9e'
      }
    };

    this.quadrants = Array.from(dataPoints.entries()).map(([name, points]) => ({
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
    this.averageDeviation = totalPoints > 0 ? totalDeviation / totalPoints : 0;
  }
}
