import {AfterViewInit, Component, effect, ElementRef, inject, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Tooltip} from 'primeng/tooltip';
import {BookService} from '../../../../../book/service/book.service';
import {Book, ReadStatus} from '../../../../../book/model/book.model';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

interface SankeyNode {
  id: string;
  label: string;
  column: number;
  y: number;
  height: number;
  color: string;
  count: number;
}

interface SankeyLink {
  source: SankeyNode;
  target: SankeyNode;
  value: number;
  color: string;
}

@Component({
  selector: 'app-book-flow-chart',
  standalone: true,
  imports: [CommonModule, Tooltip, TranslocoDirective],
  templateUrl: './book-flow-chart.component.html',
  styleUrls: ['./book-flow-chart.component.scss']
})
export class BookFlowChartComponent implements AfterViewInit {
  @ViewChild('flowCanvas', {static: false}) canvasRef!: ElementRef<HTMLCanvasElement>;

  private readonly bookService = inject(BookService);
  private readonly t = inject(TranslocoService);
  private readonly syncChartEffect = effect(() => {
    if (this.bookService.isBooksLoading()) {
      this.dataReady = false;
      return;
    }

    this.processData(this.bookService.books());
    this.dataReady = true;
    this.tryRender();
  });

  public hasData = false;
  public totalBooks = 0;
  public topQuarter = '';
  public topStatus = '';
  public completionRate = '';

  private nodes: SankeyNode[] = [];
  private links: SankeyLink[] = [];
  private canvasReady = false;
  private dataReady = false;

  ngAfterViewInit(): void {
    this.canvasReady = true;
    this.tryRender();
  }

  private tryRender(): void {
    if (this.canvasReady && this.dataReady && this.hasData) {
      requestAnimationFrame(() => this.draw());
    }
  }

  private processData(books: Book[]): void {
    this.hasData = false;
    this.totalBooks = 0;
    this.topQuarter = '';
    this.topStatus = '';
    this.completionRate = '';
    this.nodes = [];
    this.links = [];

    if (books.length === 0) {
      return;
    }

    const quarterMap = new Map<string, number>();
    const statusMap = new Map<string, number>();
    const ratingMap = new Map<string, number>();
    const quarterToStatus = new Map<string, Map<string, number>>();
    const statusToRating = new Map<string, Map<string, number>>();

    const statusColors: Record<string, string> = {
      'Read': '#66bb6a', 'Reading': '#42a5f5', 'Unread': '#78909c',
      'Paused': '#ffa726', 'Abandoned': '#ef5350', 'Other': '#ab47bc'
    };

    const ratingColors: Record<string, string> = {
      'Rated 4-5': '#66bb6a', 'Rated 3': '#ffc107',
      'Rated 1-2': '#ef5350', 'Unrated': '#78909c'
    };

    for (const book of books) {
      const addedOn = book.addedOn;
      let quarter = 'Unknown';
      if (addedOn) {
        const date = new Date(addedOn);
        const q = Math.ceil((date.getMonth() + 1) / 3);
        quarter = `${date.getFullYear()} Q${q}`;
      }

      let status = 'Other';
      switch (book.readStatus) {
        case ReadStatus.READ: status = 'Read'; break;
        case ReadStatus.READING: case ReadStatus.RE_READING: status = 'Reading'; break;
        case ReadStatus.UNREAD: case ReadStatus.UNSET: status = 'Unread'; break;
        case ReadStatus.PAUSED: status = 'Paused'; break;
        case ReadStatus.ABANDONED: case ReadStatus.WONT_READ: status = 'Abandoned'; break;
      }

      let ratingBucket = 'Unrated';
      const rating = book.personalRating;
      if (rating && rating > 0) {
        const normalized = rating / 2;
        if (normalized >= 4) ratingBucket = 'Rated 4-5';
        else if (normalized >= 3) ratingBucket = 'Rated 3';
        else ratingBucket = 'Rated 1-2';
      }

      quarterMap.set(quarter, (quarterMap.get(quarter) || 0) + 1);
      statusMap.set(status, (statusMap.get(status) || 0) + 1);
      ratingMap.set(ratingBucket, (ratingMap.get(ratingBucket) || 0) + 1);

      if (!quarterToStatus.has(quarter)) quarterToStatus.set(quarter, new Map());
      const qsMap = quarterToStatus.get(quarter)!;
      qsMap.set(status, (qsMap.get(status) || 0) + 1);

      if (!statusToRating.has(status)) statusToRating.set(status, new Map());
      const srMap = statusToRating.get(status)!;
      srMap.set(ratingBucket, (srMap.get(ratingBucket) || 0) + 1);
    }

    if (quarterMap.size === 0) return;
    this.hasData = true;
    this.totalBooks = books.length;

    const sortedQuarters = [...quarterMap.entries()]
      .sort((a, b) => b[1] - a[1])
      .slice(0, 8);

    this.topQuarter = sortedQuarters[0]?.[0] || '';
    const topStatusEntry = [...statusMap.entries()].sort((a, b) => b[1] - a[1])[0];
    this.topStatus = topStatusEntry?.[0] || '';

    const readCount = statusMap.get('Read') || 0;
    this.completionRate = books.length > 0 ? Math.round((readCount / books.length) * 100) + '%' : '0%';

    const quarterColors = ['#42a5f5', '#26c6da', '#66bb6a', '#ffa726', '#ab47bc', '#ef5350', '#ec407a', '#7e57c2'];

    const totalHeight = 420;
    const padding = 6;

    const createColumnNodes = (
      entries: [string, number][],
      column: number,
      colors: Record<string, string> | string[]
    ): SankeyNode[] => {
      const total = entries.reduce((s, e) => s + e[1], 0);
      let currentY = 30;
      return entries.map(([id, count], i) => {
        const height = Math.max(10, (count / total) * (totalHeight - entries.length * padding));
        const node: SankeyNode = {
          id, label: id, column,
          y: currentY, height,
          color: Array.isArray(colors) ? colors[i % colors.length] : (colors[id] || '#78909c'),
          count
        };
        currentY += height + padding;
        return node;
      });
    };

    const quarterNodes = createColumnNodes(sortedQuarters, 0, quarterColors);
    const statusEntries = [...statusMap.entries()].sort((a, b) => b[1] - a[1]);
    const statusNodes = createColumnNodes(statusEntries, 1, statusColors);
    const ratingEntries = [...ratingMap.entries()].sort((a, b) => b[1] - a[1]);
    const ratingNodes = createColumnNodes(ratingEntries, 2, ratingColors);

    this.nodes = [...quarterNodes, ...statusNodes, ...ratingNodes];
    this.links = [];

    const findNode = (id: string, col: number) => this.nodes.find(n => n.id === id && n.column === col);

    for (const [quarter, statusCounts] of quarterToStatus) {
      const qNode = findNode(quarter, 0);
      if (!qNode) continue;
      for (const [status, count] of statusCounts) {
        const sNode = findNode(status, 1);
        if (sNode) {
          this.links.push({source: qNode, target: sNode, value: count, color: qNode.color});
        }
      }
    }

    for (const [status, ratingCounts] of statusToRating) {
      const sNode = findNode(status, 1);
      if (!sNode) continue;
      for (const [rating, count] of ratingCounts) {
        const rNode = findNode(rating, 2);
        if (rNode) {
          this.links.push({source: sNode, target: rNode, value: count, color: sNode.color});
        }
      }
    }
  }

  private draw(): void {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const rect = canvas.parentElement!.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    canvas.width = rect.width * dpr;
    canvas.height = 480 * dpr;
    canvas.style.width = rect.width + 'px';
    canvas.style.height = '480px';
    ctx.scale(dpr, dpr);

    const width = rect.width;
    const nodeWidth = 18;
    const leftMargin = 100;
    const rightMargin = 110;
    const colX = [leftMargin, width * 0.45, width - rightMargin];

    ctx.fillStyle = 'rgba(255, 255, 255, 0.5)';
    ctx.font = '11px Inter, sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText(this.t.translate('statsUser.bookFlow.colAdded'), colX[0] + nodeWidth / 2, 18);
    ctx.fillText(this.t.translate('statsUser.bookFlow.colStatus'), colX[1] + nodeWidth / 2, 18);
    ctx.fillText(this.t.translate('statsUser.bookFlow.colRating'), colX[2] + nodeWidth / 2, 18);

    const sourceOffsets = new Map<string, number>();
    const targetOffsets = new Map<string, number>();

    for (const link of this.links) {
      const sKey = `${link.source.id}-${link.source.column}`;
      const tKey = `${link.target.id}-${link.target.column}`;
      const sOff = sourceOffsets.get(sKey) || 0;
      const tOff = targetOffsets.get(tKey) || 0;

      const linkHeight = Math.max(1, (link.value / link.source.count) * link.source.height);
      const tLinkHeight = Math.max(1, (link.value / link.target.count) * link.target.height);

      const x0 = colX[link.source.column] + nodeWidth;
      const y0 = link.source.y + sOff + linkHeight / 2;
      const x1 = colX[link.target.column];
      const y1 = link.target.y + tOff + tLinkHeight / 2;

      ctx.beginPath();
      ctx.moveTo(x0, y0 - linkHeight / 2);
      const cpx = (x0 + x1) / 2;
      ctx.bezierCurveTo(cpx, y0 - linkHeight / 2, cpx, y1 - tLinkHeight / 2, x1, y1 - tLinkHeight / 2);
      ctx.lineTo(x1, y1 + tLinkHeight / 2);
      ctx.bezierCurveTo(cpx, y1 + tLinkHeight / 2, cpx, y0 + linkHeight / 2, x0, y0 + linkHeight / 2);
      ctx.closePath();

      ctx.fillStyle = link.color + '55';
      ctx.fill();
      ctx.strokeStyle = link.color + '30';
      ctx.lineWidth = 0.5;
      ctx.stroke();

      sourceOffsets.set(sKey, sOff + linkHeight);
      targetOffsets.set(tKey, tOff + tLinkHeight);
    }

    for (const node of this.nodes) {
      const x = colX[node.column];

      ctx.fillStyle = node.color;
      ctx.globalAlpha = 0.92;
      ctx.beginPath();
      ctx.roundRect(x, node.y, nodeWidth, node.height, 3);
      ctx.fill();
      ctx.globalAlpha = 1;

      ctx.strokeStyle = 'rgba(255, 255, 255, 0.15)';
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.roundRect(x, node.y, nodeWidth, node.height, 3);
      ctx.stroke();

      ctx.fillStyle = 'rgba(255, 255, 255, 0.9)';
      ctx.font = '11px Inter, sans-serif';
      const labelX = node.column === 2 ? x + nodeWidth + 8 : x - 8;
      ctx.textAlign = node.column === 2 ? 'left' : 'right';
      ctx.textBaseline = 'middle';
      const label = node.label.length > 18 ? node.label.substring(0, 16) + '..' : node.label;
      ctx.fillText(`${label} (${node.count})`, labelX, node.y + node.height / 2);
    }
  }
}
