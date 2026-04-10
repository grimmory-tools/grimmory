import {Component, Input, OnChanges, OnDestroy, SimpleChanges, ViewChild, ElementRef, AfterViewInit} from '@angular/core';
import {CbxPageSplitOption} from '../../../settings/user-management/user.service';

export type CanvasSplitState = 'NO_SPLIT' | 'LEFT_PART' | 'RIGHT_PART';

/**
 * Renders a wide page image on a <canvas>, optionally showing only
 * the left or right half depending on the split state.
 *
 * Browser canvas limits respected:
 *   Safari: 4096×4096    Others: 16384×16384
 */
@Component({
  selector: 'app-canvas-renderer',
  standalone: true,
  imports: [],
  template: `
    <canvas
      #canvas
      class="canvas-page"
      [class.canvas-hidden]="!imageLoaded"
      [style.max-width.%]="100"
      [style.max-height.%]="100">
    </canvas>
  `,
  styles: [`
    :host {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 100%;
      height: 100%;
    }
    .canvas-page {
      object-fit: contain;
      max-width: 100%;
      max-height: 100%;
    }
    .canvas-hidden {
      visibility: hidden;
    }
  `]
})
export class CanvasRendererComponent implements OnChanges, AfterViewInit, OnDestroy {
  @Input() imageUrl = '';
  @Input() splitState: CanvasSplitState = 'NO_SPLIT';
  @Input() splitOption: CbxPageSplitOption = CbxPageSplitOption.NO_SPLIT;

  @ViewChild('canvas', {static: true}) canvasRef!: ElementRef<HTMLCanvasElement>;

  imageLoaded = false;
  private currentImage: HTMLImageElement | null = null;
  private pendingImage: HTMLImageElement | null = null;

  private static readonly MAX_CANVAS_SAFARI = 4096;
  private static readonly MAX_CANVAS_OTHER = 16384;

  ngAfterViewInit(): void {
    if (this.imageUrl) {
      this.loadAndDraw();
    }
  }

  ngOnDestroy(): void {
    // Release canvas memory
    const canvas = this.canvasRef?.nativeElement;
    if (canvas) {
      canvas.width = 0;
      canvas.height = 0;
    }
    this.cancelPendingImage();
    if (this.currentImage) {
      this.currentImage.onload = null;
      this.currentImage.onerror = null;
      this.currentImage.src = '';
      this.currentImage = null;
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['imageUrl'] || changes['splitState'] || changes['splitOption']) {
      if (changes['imageUrl'] && this.currentImage?.src !== this.imageUrl) {
        this.loadAndDraw();
      } else if (this.currentImage) {
        this.drawImage(this.currentImage);
      }
    }
  }

  private loadAndDraw(): void {
    if (!this.imageUrl) return;

    this.cancelPendingImage();

    this.imageLoaded = false;
    const img = new Image();
    img.crossOrigin = 'anonymous';
    this.pendingImage = img;
    img.onload = () => {
      if (this.pendingImage === img) {
        this.currentImage = img;
        this.drawImage(img);
        this.imageLoaded = true;
        this.pendingImage = null;
      }
    };
    img.src = this.imageUrl;
  }

  private cancelPendingImage(): void {
    if (this.pendingImage) {
      this.pendingImage.onload = null;
      this.pendingImage.onerror = null;
      this.pendingImage.src = '';
      this.pendingImage = null;
    }
  }

  private drawImage(img: HTMLImageElement): void {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const maxDim = this.getMaxCanvasSize();
    const srcW = img.naturalWidth;
    const srcH = img.naturalHeight;

    if (this.splitState === 'NO_SPLIT' || this.splitOption === CbxPageSplitOption.NO_SPLIT) {
      // Draw full image
      const w = Math.min(srcW, maxDim);
      const h = Math.min(srcH, maxDim);
      canvas.width = w;
      canvas.height = h;
      ctx.drawImage(img, 0, 0, srcW, srcH, 0, 0, w, h);
      return;
    }

    // Split: draw only left or right half
    const halfW = Math.floor(srcW / 2);
    const outW = Math.min(halfW, maxDim);
    const outH = Math.min(srcH, maxDim);
    canvas.width = outW;
    canvas.height = outH;

    const isLeftToRight = this.splitOption === CbxPageSplitOption.SPLIT_LEFT_TO_RIGHT
      || this.splitOption === CbxPageSplitOption.FIT_SPLIT;
    const showLeft = isLeftToRight
      ? this.splitState === 'LEFT_PART'
      : this.splitState === 'RIGHT_PART';

    const srcX = showLeft ? 0 : halfW;
    ctx.drawImage(img, srcX, 0, halfW, srcH, 0, 0, outW, outH);
  }

  private getMaxCanvasSize(): number {
    const ua = navigator.userAgent;
    if (/Safari/.test(ua) && !/Chrome/.test(ua)) {
      return CanvasRendererComponent.MAX_CANVAS_SAFARI;
    }
    return CanvasRendererComponent.MAX_CANVAS_OTHER;
  }
}
