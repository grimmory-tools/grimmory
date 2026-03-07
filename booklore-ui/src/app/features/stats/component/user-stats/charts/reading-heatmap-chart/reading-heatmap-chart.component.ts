import {
  AfterViewInit,
  Component,
  ElementRef,
  inject,
  NgZone,
  OnDestroy,
  OnInit,
  Renderer2,
  ViewChild
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router} from '@angular/router';
import {BaseChartDirective} from 'ng2-charts';
import {Tooltip} from 'primeng/tooltip';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, filter, first, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BookService} from '../../../../../book/service/book.service';
import {BookState} from '../../../../../book/model/state/book-state.model';
import {Book} from '../../../../../book/model/book.model';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

interface MatrixDataPoint {
  x: number; // month (0-11)
  y: number; // year index
  v: number; // book count
}

interface YearMonthData {
  year: number;
  month: number;
  count: number;
}

const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const PANEL_WIDTH   = 260;
const PANEL_MAX_H   = 300;
/** px gap between cell edge and arrow tip */
const ARROW_SIZE    = 7;
const GAP           = 2;
/** nudge panel downward (positive = closer to cell when panel is above) */
const PANEL_TOP_NUDGE = 8;
/** nudge arrow rightward relative to computed cell-centre position */
const ARROW_X_NUDGE   = 10;
/** ms to wait before hiding — lets the mouse cross from cell to panel */
const HIDE_DELAY_MS = 180;

type HeatmapChartData = ChartData<'matrix', MatrixDataPoint[], string>;

@Component({
  selector: 'app-reading-heatmap-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, Tooltip, TranslocoDirective],
  templateUrl: './reading-heatmap-chart.component.html',
  styleUrls: ['./reading-heatmap-chart.component.scss']
})
export class ReadingHeatmapChartComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly bookService  = inject(BookService);
  private readonly router       = inject(Router);
  private readonly t            = inject(TranslocoService);
  private readonly zone         = inject(NgZone);
  private readonly renderer     = inject(Renderer2);
  private readonly destroy$     = new Subject<void>();

  @ViewChild('chartCanvas', {static: false}) chartCanvasRef!: ElementRef<HTMLCanvasElement>;

  public readonly chartType = 'matrix' as const;

  private yearLabels: string[]          = [];
  private maxBookCount                  = 1;
  private booksByYearMonth              = new Map<string, Book[]>();
  private activeCellKey: string | null  = null;
  private panelEl: HTMLElement | null   = null;
  private mouseInPanel                  = false;
  private hideTimer: ReturnType<typeof setTimeout> | null = null;

  // ── Chart options ─────────────────────────────────────────────────────────

  public readonly chartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    layout: {padding: {top: 20}},
    plugins: {
      legend:  {display: false},
      tooltip: {enabled: false},
      datalabels: {
        display: true,
        color: '#ffffff',
        font: {family: "'Inter', sans-serif", size: 10, weight: 'bold'},
        formatter: (value: MatrixDataPoint) => value.v > 0 ? value.v.toString() : ''
      }
    },
    onHover: (event: any, elements: any[]) => {
      this.zone.run(() => this.onChartHover(event, elements));
    },
    scales: {
      x: {
        type: 'linear', position: 'bottom',
        ticks: {stepSize: 1, callback: (v) => MONTH_NAMES[v as number] || '',
                color: '#ffffff', font: {family: "'Inter', sans-serif", size: 11}},
        grid: {display: false}
      },
      y: {
        type: 'linear', offset: true,
        ticks: {stepSize: 1, callback: (v) => this.yearLabels[v as number] || '',
                color: '#ffffff', font: {family: "'Inter', sans-serif", size: 11}},
        grid: {display: false}
      }
    }
  };

  private readonly chartDataSubject = new BehaviorSubject<HeatmapChartData>({
    labels: [],
    datasets: [{label: this.t.translate('statsUser.readingHeatmap.booksRead'), data: []}]
  });
  public readonly chartData$: Observable<HeatmapChartData> = this.chartDataSubject.asObservable();

  private canvasMouseLeaveUnlisten: (() => void) | null = null;

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.bookService.bookState$
      .pipe(
        filter(state => state.loaded), first(),
        catchError(err => { console.error('Reading heatmap error:', err); return EMPTY; }),
        takeUntil(this.destroy$)
      )
      .subscribe(() => this.updateChartData(this.calculateHeatmapData()));
  }

  ngAfterViewInit(): void {
    // onHover stops firing when the mouse leaves the canvas entirely.
    // Listen to mouseleave on the canvas so we always schedule a hide.
    const canvas = this.chartCanvasRef?.nativeElement;
    if (canvas) {
      this.canvasMouseLeaveUnlisten = this.renderer.listen(canvas, 'mouseleave', () => {
        if (!this.mouseInPanel) this.scheduleHide();
      });
    }
  }

  ngOnDestroy(): void {
    this.canvasMouseLeaveUnlisten?.();
    this.canvasMouseLeaveUnlisten = null;
    this.cancelHideTimer();
    this.destroy$.next();
    this.destroy$.complete();
    this.destroyPanel();
  }

  // ── Hide timer helpers ────────────────────────────────────────────────────

  private scheduleHide(): void {
    this.cancelHideTimer();
    this.hideTimer = setTimeout(() => {
      if (!this.mouseInPanel) this.hidePanelNow();
    }, HIDE_DELAY_MS);
  }

  private cancelHideTimer(): void {
    if (this.hideTimer !== null) {
      clearTimeout(this.hideTimer);
      this.hideTimer = null;
    }
  }

  // ── Hover panel — body-appended ───────────────────────────────────────────

  private getOrCreatePanel(): HTMLElement {
    if (this.panelEl) return this.panelEl;

    const el = this.renderer.createElement('div') as HTMLElement;
    el.className = 'heatmap-hover-panel-global';
    this.renderer.appendChild(document.body, el);

    this.renderer.listen(el, 'mouseenter', () => {
      this.mouseInPanel = true;
      this.cancelHideTimer();          // cancel any pending hide
    });
    this.renderer.listen(el, 'mouseleave', () => {
      this.mouseInPanel = false;
      this.hidePanelNow();
    });

    this.panelEl = el;
    return el;
  }

  private destroyPanel(): void {
    if (this.panelEl) {
      this.renderer.removeChild(document.body, this.panelEl);
      this.panelEl = null;
    }
    this.activeCellKey = null;
  }

  private hidePanelNow(): void {
    this.panelEl?.classList.remove('visible');
    this.panelEl?.classList.remove('arrow-below');
    this.panelEl?.classList.remove('arrow-above');
    this.activeCellKey = null;
  }

  private showPanel(cellKey: string, month: string, year: string, books: Book[], cellRect: DOMRect): void {
    this.cancelHideTimer();
    const el = this.getOrCreatePanel();

    // ── Content ──────────────────────────────────────────────────────────
    const countLabel = books.length === 1 ? '1 book' : `${books.length} books`;
    const itemsHtml = books.map(book => {
      const title = this.escapeHtml(book.metadata?.title ?? 'Unknown title');
      return `<button class="heatmap-panel-item" data-book-id="${book.id}" title="${title}">
        <i class="pi pi-book heatmap-panel-item-icon"></i>
        <span class="heatmap-panel-item-title">${title}</span>
      </button>`;
    }).join('');

    el.innerHTML = `
      <div class="heatmap-panel-header">
        <span class="heatmap-panel-month">${month} ${year}</span>
        <span class="heatmap-panel-count">${countLabel}</span>
      </div>
      <div class="heatmap-panel-list">${itemsHtml}</div>
    `;

    el.querySelectorAll<HTMLButtonElement>('.heatmap-panel-item').forEach(btn => {
      btn.addEventListener('click', () => {
        const id   = Number(btn.dataset['bookId']);
        const book = books.find(b => b.id === id);
        if (book) this.zone.run(() => { this.hidePanelNow(); this.router.navigate(['/book', book.id]); });
      });
    });

    // ── Position ─────────────────────────────────────────────────────────
    const vw = window.innerWidth;
    const vh = window.innerHeight;

    // Set sizing before measuring so offsetHeight is accurate
    el.style.width     = `${PANEL_WIDTH}px`;
    el.style.maxHeight = `${PANEL_MAX_H}px`;
    // Place off-screen while measuring to avoid flicker
    el.style.top  = '-9999px';
    el.style.left = '-9999px';
    el.classList.remove('arrow-above', 'arrow-below', 'visible');

    // Force layout so we get the real rendered height (not the estimated one)
    const panelH = el.offsetHeight;
    const totalH = panelH + ARROW_SIZE;

    // Horizontal: centre on cell, clamp to viewport
    let left = cellRect.left + cellRect.width / 2 - PANEL_WIDTH / 2;
    left = Math.max(8, Math.min(left, vw - PANEL_WIDTH - 8));

    // Arrow offset so it still points at cell centre regardless of clamping
    const cellCentreX = cellRect.left + cellRect.width / 2;
    const arrowLeft   = Math.max(12, Math.min(cellCentreX - left - ARROW_SIZE + ARROW_X_NUDGE, PANEL_WIDTH - 12 - ARROW_SIZE * 2));

    // Vertical: prefer above cell, fall back to below
    let top: number;
    let arrowSide: 'below' | 'above';

    if (cellRect.top - totalH - GAP >= 8) {
      top = cellRect.top - totalH - GAP + PANEL_TOP_NUDGE;
      arrowSide = 'below';
    } else {
      top = cellRect.bottom + GAP - PANEL_TOP_NUDGE;
      arrowSide = 'above';
    }
    top = Math.max(8, Math.min(top, vh - totalH - 8));

    el.style.setProperty('--arrow-left', `${arrowLeft}px`);
    el.style.top  = `${top}px`;
    el.style.left = `${left}px`;

    el.classList.add(arrowSide === 'below' ? 'arrow-below' : 'arrow-above');
    el.classList.add('visible');

    this.activeCellKey = cellKey;
  }

  private escapeHtml(s: string): string {
    return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  }

  // ── Chart hover handler ───────────────────────────────────────────────────

  private onChartHover(event: any, elements: any[]): void {
    if (!elements || elements.length === 0) {
      // Mouse left a cell — schedule hide; panel mouseenter will cancel it
      this.scheduleHide();
      return;
    }

    const element = elements[0];
    const point   = element.element.$context?.raw as MatrixDataPoint;

    if (!point || point.v === 0) {
      this.scheduleHide();
      return;
    }

    const year  = this.yearLabels[point.y];
    const month = MONTH_NAMES[point.x];
    const key   = `${year}-${point.x + 1}`;

    if (key === this.activeCellKey) {
      this.cancelHideTimer(); // still on same cell — stay visible
      return;
    }

    const books = this.booksByYearMonth.get(key) ?? [];
    const canvas = this.chartCanvasRef?.nativeElement;
    if (!canvas) return;

    const canvasRect  = canvas.getBoundingClientRect();
    const cellEl      = element.element;
    const cellW       = cellEl.width  ?? (cellEl.options?.width  ?? 20);
    const cellH       = cellEl.height ?? (cellEl.options?.height ?? 20);

    const cellRect = new DOMRect(
      canvasRect.left + cellEl.x - cellW / 2,
      canvasRect.top  + cellEl.y - cellH / 2,
      cellW, cellH
    );

    this.showPanel(key, month, year, books, cellRect);
  }

  // ── Chart data ────────────────────────────────────────────────────────────

  private updateChartData(yearMonthData: YearMonthData[]): void {
    const currentYear = new Date().getFullYear();
    const years = Array.from({length: 10}, (_, i) => currentYear - 9 + i);

    this.yearLabels  = years.map(String);
    this.maxBookCount = Math.max(1, ...yearMonthData.map(d => d.count));

    const heatmapData: MatrixDataPoint[] = [];
    years.forEach((year, yi) => {
      for (let m = 0; m <= 11; m++) {
        const dp = yearMonthData.find(d => d.year === year && d.month === m + 1);
        heatmapData.push({x: m, y: yi, v: dp?.count || 0});
      }
    });

    if (this.chartOptions?.scales?.['y'])
      (this.chartOptions.scales['y'] as any).max = years.length - 1;

    this.chartDataSubject.next({
      labels: [],
      datasets: [{
        label: this.t.translate('statsUser.readingHeatmap.booksRead'),
        data: heatmapData,
        backgroundColor: (ctx) => {
          const p = ctx.raw as MatrixDataPoint;
          if (!p?.v) return 'rgba(255,255,255,0.05)';
          const a = Math.max(0.2, Math.min(1.0, (p.v / this.maxBookCount) * 0.8 + 0.2));
          return `rgba(239,71,111,${a})`;
        },
        borderColor: 'rgba(255,255,255,0.2)',
        borderWidth: 1,
        width:  ({chart}) => (chart.chartArea?.width  || 0) / 12           - 1,
        height: ({chart}) => (chart.chartArea?.height || 0) / years.length - 1
      }]
    });
  }

  private calculateHeatmapData(): YearMonthData[] {
    const s = this.bookService.getCurrentBookState();
    return this.isValidBookState(s) ? this.processHeatmapData(s.books!) : [];
  }

  private isValidBookState(s: unknown): s is BookState {
    return typeof s === 'object' && s !== null
      && 'loaded' in s && typeof (s as any).loaded === 'boolean'
      && 'books'  in s && Array.isArray((s as any).books)
      && (s as any).books.length > 0;
  }

  private processHeatmapData(books: Book[]): YearMonthData[] {
    const map = new Map<string, number>();
    this.booksByYearMonth.clear();

    const now       = new Date().getFullYear();
    const startYear = now - 9;

    books.filter(b => b.dateFinished).forEach(b => {
      const d    = new Date(b.dateFinished!);
      const year = d.getFullYear();
      if (year < startYear || year > now) return;
      const month = d.getMonth() + 1;
      const key   = `${year}-${month}`;
      map.set(key, (map.get(key) || 0) + 1);
      const arr = this.booksByYearMonth.get(key) ?? [];
      arr.push(b);
      this.booksByYearMonth.set(key, arr);
    });

    return Array.from(map.entries())
      .map(([k, count]) => { const [y, m] = k.split('-').map(Number); return {year: y, month: m, count}; })
      .sort((a, b) => a.year - b.year || a.month - b.month);
  }
}
