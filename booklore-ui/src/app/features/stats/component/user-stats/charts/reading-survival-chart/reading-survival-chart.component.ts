import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {Tooltip} from 'primeng/tooltip';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BookService} from '../../../../../book/service/book.service';
import {BookState} from '../../../../../book/model/state/book-state.model';
import {Book} from '../../../../../book/model/book.model';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

type SurvivalChartData = ChartData<'line', number[], string>;

const THRESHOLDS = [0, 10, 25, 50, 75, 90, 100];

@Component({
  selector: 'app-reading-survival-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, Tooltip, TranslocoDirective],
  templateUrl: './reading-survival-chart.component.html',
  styleUrls: ['./reading-survival-chart.component.scss']
})
export class ReadingSurvivalChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'line' as const;
  public totalStarted = 0;
  public completionRate = 0;
  public medianDropout = '';
  public dangerZoneRange = '';
  public dangerZoneDrop = '';

  public readonly chartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    layout: {
      padding: {top: 10, bottom: 10, left: 10, right: 10}
    },
    plugins: {
      legend: {display: false},
      tooltip: {
        enabled: true,
        backgroundColor: 'rgba(0, 0, 0, 0.9)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: '#e91e63',
        borderWidth: 1,
        cornerRadius: 6,
        padding: 12,
        titleFont: {size: 13, weight: 'bold'},
        bodyFont: {size: 12},
        callbacks: {
          title: (context) => this.t.translate('statsUser.readingSurvival.tooltipProgress', {label: context[0].label}),
          label: (context) => this.t.translate('statsUser.readingSurvival.tooltipSurvival', {value: (context.parsed.y ?? 0).toFixed(1)})
        }
      },
      datalabels: {display: false}
    },
    scales: {
      x: {
        title: {
          display: true,
          text: this.t.translate('statsUser.readingSurvival.axisProgressThreshold'),
          color: '#ffffff',
          font: {family: "'Inter', sans-serif", size: 12, weight: 'bold'}
        },
        ticks: {
          color: '#ffffff',
          font: {family: "'Inter', sans-serif", size: 11}
        },
        grid: {color: 'rgba(255, 255, 255, 0.1)'},
        border: {display: false}
      },
      y: {
        min: 0,
        max: 100,
        title: {
          display: true,
          text: this.t.translate('statsUser.readingSurvival.axisBooksSurviving'),
          color: '#ffffff',
          font: {family: "'Inter', sans-serif", size: 12, weight: 'bold'}
        },
        ticks: {
          color: '#ffffff',
          font: {family: "'Inter', sans-serif", size: 11},
          callback: (value) => `${value}%`
        },
        grid: {color: 'rgba(255, 255, 255, 0.1)'},
        border: {display: false}
      }
    }
  };

  private readonly chartDataSubject = new BehaviorSubject<SurvivalChartData>({
    labels: [],
    datasets: []
  });

  public readonly chartData$: Observable<SurvivalChartData> = this.chartDataSubject.asObservable();

  ngOnInit(): void {
    this.bookService.bookState$
      .pipe(
        filter(state => state.loaded),
        first(),
        catchError((error) => {
          console.error('Error processing survival data:', error);
          return EMPTY;
        }),
        takeUntil(this.destroy$)
      )
      .subscribe(() => this.calculateSurvivalCurve());
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private calculateSurvivalCurve(): void {
    const currentState = this.bookService.getCurrentBookState();
    if (!this.isValidBookState(currentState)) return;

    const books = currentState.books!;
    const startedBooks = books.filter(b => this.getBookProgress(b) > 0);
    this.totalStarted = startedBooks.length;

    if (this.totalStarted === 0) return;

    const progresses = startedBooks.map(b => this.getBookProgress(b));
    const survivalValues = THRESHOLDS.map(threshold => {
      const survived = progresses.filter(p => p >= threshold).length;
      return (survived / this.totalStarted) * 100;
    });

    // Completion rate
    this.completionRate = Math.round(survivalValues[survivalValues.length - 1]);

    // Median dropout: find where survival drops below 50%
    let medianIdx = survivalValues.findIndex(v => v < 50);
    if (medianIdx === -1) {
      this.medianDropout = '100%+';
    } else if (medianIdx === 0) {
      this.medianDropout = `${THRESHOLDS[0]}%`;
    } else {
      this.medianDropout = `${THRESHOLDS[medianIdx - 1]}-${THRESHOLDS[medianIdx]}%`;
    }

    // Danger zone: steepest drop
    let maxDrop = 0;
    let dangerIdx = 0;
    for (let i = 1; i < survivalValues.length; i++) {
      const drop = survivalValues[i - 1] - survivalValues[i];
      if (drop > maxDrop) {
        maxDrop = drop;
        dangerIdx = i;
      }
    }
    this.dangerZoneRange = `${THRESHOLDS[dangerIdx - 1]}-${THRESHOLDS[dangerIdx]}%`;
    this.dangerZoneDrop = `-${maxDrop.toFixed(0)}%`;

    const labels = THRESHOLDS.map(t => `${t}%`);
    this.chartDataSubject.next({
      labels,
      datasets: [{
        label: this.t.translate('statsUser.readingSurvival.survivalRate'),
        data: survivalValues,
        borderColor: '#e91e63',
        backgroundColor: 'rgba(233, 30, 99, 0.15)',
        fill: true,
        stepped: true,
        pointRadius: 5,
        pointHoverRadius: 7,
        pointBackgroundColor: '#e91e63',
        pointBorderColor: '#ffffff',
        pointBorderWidth: 2,
        borderWidth: 2
      }]
    });
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

  private getBookProgress(book: Book): number {
    if (book.pdfProgress?.percentage) return book.pdfProgress.percentage;
    if (book.epubProgress?.percentage) return book.epubProgress.percentage;
    if (book.cbxProgress?.percentage) return book.cbxProgress.percentage;
    if (book.koreaderProgress?.percentage) return book.koreaderProgress.percentage;
    if (book.koboProgress?.percentage) return book.koboProgress.percentage;
    return 0;
  }
}
