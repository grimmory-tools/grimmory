import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {Tooltip} from 'primeng/tooltip';
import {EMPTY, Subject} from 'rxjs';
import {catchError, filter, first, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BookService} from '../../../../../book/service/book.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-reading-debt-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, Tooltip, TranslocoDirective],
  templateUrl: './reading-debt-chart.component.html',
  styleUrls: ['./reading-debt-chart.component.scss']
})
export class ReadingDebtChartComponent implements OnInit, OnDestroy {
  private readonly bookService = inject(BookService);
  private readonly t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'bar' as const;
  public hasData = false;
  public currentBacklog = 0;
  public trend = '';

  public chartData: ChartData<'bar', number[], string> = {labels: [], datasets: []};

  public chartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    animation: {duration: 400},
    layout: {padding: {top: 10}},
    plugins: {
      legend: {
        display: true, position: 'bottom',
        labels: {color: 'rgba(255, 255, 255, 0.8)', font: {family: "'Inter', sans-serif", size: 11}, boxWidth: 12, padding: 15}
      },
      tooltip: {
        enabled: true, backgroundColor: 'rgba(0, 0, 0, 0.9)',
        titleColor: '#ffffff', bodyColor: '#ffffff', borderColor: '#ffffff',
        borderWidth: 1, cornerRadius: 6, padding: 10
      },
      datalabels: {display: false}
    },
    scales: {
      x: {
        grid: {color: 'rgba(255, 255, 255, 0.08)'},
        ticks: {color: 'rgba(255, 255, 255, 0.6)', font: {size: 10}}
      },
      y: {
        grid: {color: 'rgba(255, 255, 255, 0.08)'},
        ticks: {color: 'rgba(255, 255, 255, 0.6)', font: {size: 11}}
      },
      y1: {
        position: 'right',
        grid: {drawOnChartArea: false},
        ticks: {color: 'rgba(255, 193, 7, 0.8)', font: {size: 11}}
      }
    }
  };

  ngOnInit(): void {
    this.t.langChanges$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.bookService.bookState$
          .pipe(filter(state => state.loaded), first(), catchError(() => EMPTY))
          .subscribe(() => this.processData());
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private processData(): void {
    const state = this.bookService.getCurrentBookState();
    const books = state.books;
    if (!books || books.length === 0) return;

    const now = new Date();
    const locale = this.t.getActiveLang();
    const monthlyAdded = new Map<string, number>();
    const monthlyFinished = new Map<string, number>();
    const months: string[] = [];
    const monthLabels: string[] = [];
    const formatter = new Intl.DateTimeFormat(locale, {month: 'short'});

    for (let i = 11; i >= 0; i--) {
      const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
      const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
      months.push(key);
      monthLabels.push(`${formatter.format(d)} ${String(d.getFullYear()).slice(2)}`);
      monthlyAdded.set(key, 0);
      monthlyFinished.set(key, 0);
    }

    for (const book of books) {
      if (book.addedOn) {
        const d = new Date(book.addedOn);
        const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
        if (monthlyAdded.has(key)) monthlyAdded.set(key, monthlyAdded.get(key)! + 1);
      }
      if (book.dateFinished) {
        const d = new Date(book.dateFinished);
        const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
        if (monthlyFinished.has(key)) monthlyFinished.set(key, monthlyFinished.get(key)! + 1);
      }
    }

    const added = months.map(m => monthlyAdded.get(m) || 0);
    const finished = months.map(m => monthlyFinished.get(m) || 0);

    if (added.every(v => v === 0) && finished.every(v => v === 0)) return;

    const backlog: number[] = [];
    let running = 0;
    for (let i = 0; i < months.length; i++) {
      running += added[i] - finished[i];
      backlog.push(running);
    }
    this.currentBacklog = running;
    this.trend = running > (backlog[0] || 0) ? this.t.translate('statsUser.readingDebt.growing') : this.t.translate('statsUser.readingDebt.shrinking');

    this.chartData = {
      labels: monthLabels,
      datasets: [
        {
          label: this.t.translate('statsUser.readingDebt.booksAdded'),
          data: added,
          backgroundColor: 'rgba(239, 83, 80, 0.7)',
          borderColor: '#ef5350',
          borderWidth: 1,
          borderRadius: 4,
          order: 2
        },
        {
          label: this.t.translate('statsUser.readingDebt.booksFinished'),
          data: finished,
          backgroundColor: 'rgba(102, 187, 106, 0.7)',
          borderColor: '#66bb6a',
          borderWidth: 1,
          borderRadius: 4,
          order: 2
        },
        {
          label: this.t.translate('statsUser.readingDebt.backlog'),
          data: backlog,
          type: 'line' as const,
          borderColor: '#ffc107',
          backgroundColor: 'rgba(255, 193, 7, 0.1)',
          borderWidth: 2,
          pointRadius: 3,
          pointBackgroundColor: '#ffc107',
          fill: false,
          tension: 0.3,
          yAxisID: 'y1',
          order: 1
        } as any
      ]
    };
    this.hasData = true;
  }
}