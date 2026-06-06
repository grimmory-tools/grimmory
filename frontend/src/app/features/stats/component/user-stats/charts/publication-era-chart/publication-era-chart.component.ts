import {Component, effect, inject} from '@angular/core';
import {BaseChartDirective} from 'ng2-charts';
import {Tooltip} from 'primeng/tooltip';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BookService} from '../../../../../book/service/book.service';
import {Book} from '../../../../../book/model/book.model';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-publication-era-chart',
  standalone: true,
  imports: [BaseChartDirective, Tooltip, TranslocoDirective],
  templateUrl: './publication-era-chart.component.html',
  styleUrls: ['./publication-era-chart.component.scss']
})
export class PublicationEraChartComponent {
  private readonly bookService = inject(BookService);
  private readonly t = inject(TranslocoService);
  private readonly syncChartEffect = effect(() => {
    if (this.bookService.isBooksLoading()) {
      return;
    }

    this.processData(this.bookService.books());
  });

  public readonly chartType = 'line' as const;
  public hasData = false;
  public bestDecade = '';
  public bestAvgRating = 0;
  public totalRated = 0;

  private readonly DECADE_COLORS = [
    '#e91e63', '#9c27b0', '#673ab7', '#3f51b5',
    '#2196f3', '#00bcd4', '#009688', '#4caf50',
    '#8bc34a', '#ff9800'
  ];

  public chartData: ChartData<'line', number[], string> = {labels: [], datasets: []};

  public readonly chartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    animation: {duration: 400},
    layout: {padding: {top: 10, right: 10}},
    plugins: {
      legend: {
        display: true, position: 'bottom',
        labels: {color: 'rgba(255, 255, 255, 0.8)', font: {family: "'Inter', sans-serif", size: 11}, boxWidth: 12, padding: 12}
      },
      tooltip: {
        enabled: true, backgroundColor: 'rgba(0, 0, 0, 0.9)',
        titleColor: '#ffffff', bodyColor: '#ffffff',
        borderColor: '#ffffff', borderWidth: 1, cornerRadius: 6, padding: 10,
        callbacks: {
          label: (ctx) => {
            return `${ctx.dataset.label}: ${ctx.parsed.y} books`;
          }
        }
      },
      datalabels: {display: false}
    },
    scales: {
      x: {
        grid: {color: 'rgba(255, 255, 255, 0.08)'},
        ticks: {color: 'rgba(255, 255, 255, 0.6)', font: {size: 11}},
        title: {display: true, text: 'Rating Range', color: 'rgba(255, 255, 255, 0.5)', font: {size: 11}}
      },
      y: {
        beginAtZero: true,
        grid: {color: 'rgba(255, 255, 255, 0.08)'},
        ticks: {color: 'rgba(255, 255, 255, 0.6)', font: {size: 11}, stepSize: 1},
        title: {display: true, text: 'Books', color: 'rgba(255, 255, 255, 0.5)', font: {size: 11}}
      }
    },
    interaction: {mode: 'index', intersect: false}
  };

  private processData(books: Book[]): void {
    if (books.length === 0) {
      this.hasData = false;
      this.bestDecade = '';
      this.bestAvgRating = 0;
      this.totalRated = 0;
      this.chartData = {labels: [], datasets: []};
      return;
    }

    const ratedBooks = books.filter(b => b.metadata?.publishedDate && b.personalRating && b.personalRating > 0);
    if (ratedBooks.length < 3) return;

    this.totalRated = ratedBooks.length;

    const decadeData = new Map<string, Map<number, number>>();
    const decadeAvg = new Map<string, {total: number; count: number}>();
    const ratingLabels = ['1-2', '3-4', '5-6', '7-8', '9-10'];

    for (const book of ratedBooks) {
      const pubYear = new Date(book.metadata!.publishedDate!).getFullYear();
      if (pubYear < 1900 || pubYear > 2030) continue;
      const decade = `${Math.floor(pubYear / 10) * 10}s`;
      const rating = book.personalRating!;
      const bucket = Math.min(4, Math.floor((rating - 1) / 2));

      if (!decadeData.has(decade)) decadeData.set(decade, new Map());
      const dMap = decadeData.get(decade)!;
      dMap.set(bucket, (dMap.get(bucket) || 0) + 1);

      if (!decadeAvg.has(decade)) decadeAvg.set(decade, {total: 0, count: 0});
      const avg = decadeAvg.get(decade)!;
      avg.total += rating;
      avg.count++;
    }

    if (decadeData.size === 0) return;

    const decades = [...decadeData.keys()].sort();
    let bestDec = '';
    let bestAvg = 0;
    for (const [dec, avg] of decadeAvg) {
      const a = avg.total / avg.count;
      if (a > bestAvg) { bestAvg = a; bestDec = dec; }
    }
    this.bestDecade = bestDec;
    this.bestAvgRating = Math.round(bestAvg * 10) / 10;

    const datasets = decades.map((decade, i) => {
      const dMap = decadeData.get(decade)!;
      return {
        label: decade,
        data: ratingLabels.map((_, idx) => dMap.get(idx) || 0),
        borderColor: this.DECADE_COLORS[i % this.DECADE_COLORS.length],
        backgroundColor: this.DECADE_COLORS[i % this.DECADE_COLORS.length] + '20',
        borderWidth: 2.5,
        pointRadius: 5,
        pointBackgroundColor: this.DECADE_COLORS[i % this.DECADE_COLORS.length],
        pointBorderColor: '#ffffff',
        pointBorderWidth: 1.5,
        tension: 0.3,
        fill: false
      };
    });

    this.chartData = {labels: ratingLabels, datasets};
    this.hasData = true;
  }
}
