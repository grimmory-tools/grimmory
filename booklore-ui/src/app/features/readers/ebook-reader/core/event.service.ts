import {inject, Injectable} from '@angular/core';
import {Subject} from 'rxjs';
import {ReaderAnnotationService} from '../features/annotations/annotation-renderer.service';

export interface ViewEvent {
  type: 'load' | 'relocate' | 'error' | 'middle-single-tap' | 'draw-annotation' | 'show-annotation' | 'text-selected';
  detail?: any;
  popupPosition?: { x: number; y: number; showBelow?: boolean };
}

export interface TextSelection {
  text: string;
  cfi: string;
  range: Range;
  index: number;
}

interface ViewCallbacks {
  prev: () => void;
  next: () => void;
  getCFI: (index: number, range: Range) => string | null;
  getContents: () => Array<{ index: number; doc: Document }> | null;
}

@Injectable({
  providedIn: 'root'
})
export class ReaderEventService {
  private readonly DOUBLE_CLICK_INTERVAL_MS = 300;
  private readonly LONG_HOLD_THRESHOLD_MS = 500;
  private readonly LEFT_ZONE_PERCENT = 0.3;
  private readonly RIGHT_ZONE_PERCENT = 0.7;
  private readonly SWIPE_THRESHOLD_PX = 50;

  private annotationService = inject(ReaderAnnotationService);

  private view: any;
  private viewCallbacks: ViewCallbacks | null = null;
  private isNavigating = false;
  private lastClickTime = 0;
  private lastClickZone: 'left' | 'middle' | 'right' | null = null;
  private longHoldTimeout: ReturnType<typeof setTimeout> | null = null;
  private keydownHandler?: (event: KeyboardEvent) => void;
  private clickedDocs = new WeakSet<Document>();

  private touchStartX = 0;
  private touchStartY = 0;
  private isTextSelectionInProgress = false;
  private touchStartTime = 0;
  private selectionChangeTimeout: ReturnType<typeof setTimeout> | null = null;
  private lastTouchTime = 0;

  private eventSubject = new Subject<ViewEvent>();
  public events$ = this.eventSubject.asObservable();

  initialize(view: any, callbacks: ViewCallbacks): void {
    this.view = view;
    this.viewCallbacks = callbacks;
    this.attachViewEventListeners();
    this.attachKeyboardHandler();
    this.attachWindowMessageHandler();
  }

  destroy(): void {
    if (this.keydownHandler) {
      document.removeEventListener('keydown', this.keydownHandler);
      this.keydownHandler = undefined;
    }
    this.view = null;
    this.viewCallbacks = null;
    this.clickedDocs = new WeakSet<Document>();
  }

  emit(event: ViewEvent): void {
    this.eventSubject.next(event);
  }

  private attachViewEventListeners(): void {
    if (!this.view) return;

    this.view.addEventListener('load', (e: any) => {
      this.eventSubject.next({type: 'load', detail: e.detail});
      if (e.detail?.doc) {
        if (this.keydownHandler) {
          e.detail.doc.addEventListener('keydown', this.keydownHandler);
        }
        this.attachIframeEventHandlers(e.detail.doc);
      }

      const allAnnotations = this.annotationService.getAllAnnotations();
      if (allAnnotations.length > 0 && this.view) {
        setTimeout(() => {
          allAnnotations.forEach(annotation => {
            this.view?.addAnnotation({value: annotation.value});
          });
        }, 100);
      }
    });

    this.view.addEventListener('relocate', (e: any) => {
      this.eventSubject.next({type: 'relocate', detail: e.detail});
    });

    this.view.addEventListener('error', (e: any) => {
      this.eventSubject.next({type: 'error', detail: e.detail});
    });

    this.view.addEventListener('draw-annotation', (e: any) => {
      const {draw, annotation, doc, range} = e.detail;
      const storedStyle = this.annotationService.getAnnotationStyle(annotation.value);
      if (storedStyle) {
        const overlayerStyle = this.annotationService.getOverlayerDrawFunction(storedStyle.style);
        draw(overlayerStyle, {color: storedStyle.color});
      }
      this.eventSubject.next({type: 'draw-annotation', detail: {annotation, doc, range}});
    });

    this.view.addEventListener('show-annotation', (e: any) => {
      this.eventSubject.next({type: 'show-annotation', detail: e.detail});
    });
  }

  private attachKeyboardHandler(): void {
    this.keydownHandler = (event: KeyboardEvent) => {
      const k = event.key;
      if (k === 'ArrowLeft' || k === 'h' || k === 'PageUp') {
        this.viewCallbacks?.prev();
        event.preventDefault();
      } else if (k === 'ArrowRight' || k === 'l' || k === 'PageDown') {
        this.viewCallbacks?.next();
        event.preventDefault();
      }
    };
    document.addEventListener('keydown', this.keydownHandler);
  }

  private attachWindowMessageHandler(): void {
    window.addEventListener('message', (event) => {
      if (event.data?.type === 'iframe-click') {
        this.handleIframeClickMessage(event.data);
      }
    });
  }

  private attachIframeEventHandlers(doc: Document): void {
    if (this.clickedDocs.has(doc)) {
      return;
    }
    this.clickedDocs.add(doc);

    doc.addEventListener('mousedown', () => {
      this.longHoldTimeout = setTimeout(() => {
        this.longHoldTimeout = null;
      }, this.LONG_HOLD_THRESHOLD_MS);
    }, true);

    doc.addEventListener('mouseup', () => {
      this.handleSelectionEnd(doc);
    });

    doc.addEventListener('click', (event: MouseEvent) => {
      // Ignore synthesized mouse events that follow touch events
      if (Date.now() - this.lastTouchTime < 500) {
        return;
      }

      const iframe = doc.defaultView?.frameElement as HTMLIFrameElement | null;
      if (!iframe) return;

      const iframeRect = iframe.getBoundingClientRect();
      const viewportX = iframeRect.left + event.clientX;
      const viewportY = iframeRect.top + event.clientY;

      window.postMessage({
        type: 'iframe-click',
        clientX: viewportX,
        clientY: viewportY,
        iframeLeft: iframeRect.left,
        iframeWidth: iframeRect.width,
        eventClientX: event.clientX,
        target: (event.target as HTMLElement)?.tagName
      }, '*');
    }, true);

    doc.addEventListener('touchstart', (event: TouchEvent) => {
      this.handleTouchStart(event, doc);
    }, {passive: true});

    doc.addEventListener('touchmove', (event: TouchEvent) => {
      this.handleTouchMove(event, doc);
    }, {passive: false});

    doc.addEventListener('touchend', (event: TouchEvent) => {
      this.handleTouchEnd(event, doc);
    }, {passive: false});

    doc.addEventListener('contextmenu', (event: MouseEvent) => {
      const selection = doc.defaultView?.getSelection();
      if (selection && !selection.isCollapsed) {
        event.preventDefault();
      }
    });

    doc.addEventListener('selectionchange', () => {
      this.handleSelectionChange(doc);
    });

    this.injectMobileSelectionStyles(doc);
  }

  private handleSelectionChange(doc: Document): void {
    if (this.selectionChangeTimeout) {
      clearTimeout(this.selectionChangeTimeout);
    }

    this.selectionChangeTimeout = setTimeout(() => {
      const selection = doc.defaultView?.getSelection();
      if (!selection || selection.isCollapsed || selection.rangeCount === 0) {
        return;
      }

      const range = selection.getRangeAt(0);
      const text = range.toString().trim();
      if (!text) return;

      if ('ontouchstart' in window || navigator.maxTouchPoints > 0) {
        this.handleSelectionEnd(doc);
      }
    }, 300);
  }

  private injectMobileSelectionStyles(doc: Document): void {
    const styleId = 'booklore-mobile-selection-styles';
    if (doc.getElementById(styleId)) return;

    const style = doc.createElement('style');
    style.id = styleId;
    style.textContent = `
      * {
        -webkit-touch-callout: none !important;
        -webkit-user-select: text !important;
        user-select: text !important;
      }
    `;
    doc.head.appendChild(style);
  }

  private handleTouchStart(event: TouchEvent, _doc: Document): void {
    if (event.touches.length !== 1) return;

    const touch = event.touches[0];
    this.touchStartX = touch.clientX;
    this.touchStartY = touch.clientY;
    this.touchStartTime = Date.now();
    this.isTextSelectionInProgress = false;

    this.longHoldTimeout = setTimeout(() => {
      this.longHoldTimeout = null;
    }, this.LONG_HOLD_THRESHOLD_MS);
  }

  private handleTouchMove(event: TouchEvent, doc: Document): void {
    if (event.touches.length !== 1) return;

    const touch = event.touches[0];
    const deltaX = Math.abs(touch.clientX - this.touchStartX);
    const deltaY = Math.abs(touch.clientY - this.touchStartY);

    const selection = doc.defaultView?.getSelection();
    if (selection && !selection.isCollapsed && selection.rangeCount > 0) {
      this.isTextSelectionInProgress = true;
      event.preventDefault();
      return;
    }

    if (deltaX > 10 && deltaX > deltaY && !this.isTextSelectionInProgress) {
      return;
    }
  }

  private handleTouchEnd(event: TouchEvent, doc: Document): void {
    const touchEndTime = Date.now();
    const touchDuration = touchEndTime - this.touchStartTime;
    this.lastTouchTime = touchEndTime;

    const selection = doc.defaultView?.getSelection();
    const hasSelection = selection && !selection.isCollapsed && selection.rangeCount > 0;

    if (hasSelection) {
      this.isTextSelectionInProgress = false;
      event.preventDefault();

      setTimeout(() => {
        this.handleSelectionEnd(doc);
      }, 50);
      return;
    }

    if (!this.isTextSelectionInProgress && event.changedTouches.length === 1) {
      const touch = event.changedTouches[0];
      const deltaX = touch.clientX - this.touchStartX;
      const deltaY = Math.abs(touch.clientY - this.touchStartY);

      if (Math.abs(deltaX) >= this.SWIPE_THRESHOLD_PX && Math.abs(deltaX) > deltaY) {
        if (this.isNavigating) return;

        this.isNavigating = true;
        if (deltaX < 0) {
          this.viewCallbacks?.next();
        } else {
          this.viewCallbacks?.prev();
        }
        setTimeout(() => this.isNavigating = false, 300);
        return;
      }

      if (touchDuration < this.LONG_HOLD_THRESHOLD_MS && Math.abs(deltaX) < 10 && deltaY < 10) {
        const iframe = doc.defaultView?.frameElement as HTMLIFrameElement | null;
        if (!iframe) return;

        const iframeRect = iframe.getBoundingClientRect();
        const viewportX = iframeRect.left + touch.clientX;

        window.postMessage({
          type: 'iframe-click',
          clientX: viewportX,
          clientY: iframeRect.top + touch.clientY,
          iframeLeft: iframeRect.left,
          iframeWidth: iframeRect.width,
          eventClientX: touch.clientX,
          target: (event.target as HTMLElement)?.tagName
        }, '*');
      }
    }

    this.isTextSelectionInProgress = false;
  }

  private handleSelectionEnd(doc: Document): void {
    setTimeout(() => {
      const selection = doc.defaultView?.getSelection();
      if (!selection || selection.isCollapsed || selection.rangeCount === 0) {
        return;
      }

      const range = selection.getRangeAt(0);
      const text = range.toString().trim();
      if (!text) return;

      const contents = this.viewCallbacks?.getContents();
      if (!contents || contents.length === 0) return;

      const {index} = contents[0];
      const cfi = this.viewCallbacks?.getCFI(index, range);

      if (cfi) {
        const iframe = doc.defaultView?.frameElement as HTMLIFrameElement | null;
        const rangeRect = range.getBoundingClientRect();
        let popupX = rangeRect.left + (rangeRect.width / 2);
        let selectionTop = rangeRect.top;
        let selectionBottom = rangeRect.bottom;

        if (iframe) {
          const iframeRect = iframe.getBoundingClientRect();
          popupX = iframeRect.left + rangeRect.left + (rangeRect.width / 2);
          selectionTop = iframeRect.top + rangeRect.top;
          selectionBottom = iframeRect.top + rangeRect.bottom;
        }

        const minSpaceAbove = 120;
        const showBelow = selectionTop < minSpaceAbove;

        let popupY: number;
        if (showBelow) {
          popupY = selectionBottom + 10;
        } else {
          popupY = selectionTop - 50;
        }

        popupX = Math.max(100, Math.min(popupX, window.innerWidth - 150));

        this.eventSubject.next({
          type: 'text-selected',
          detail: {text, cfi, range, index},
          popupPosition: {x: popupX, y: popupY, showBelow}
        });
      }
    }, 10);
  }

  private handleIframeClickMessage(data: any): void {
    if (!this.view) return;

    const now = Date.now();
    const timeSinceLastClick = now - this.lastClickTime;

    const viewRect = this.view.getBoundingClientRect();
    const x = data.clientX - viewRect.left;
    const width = viewRect.width;

    const leftThreshold = width * this.LEFT_ZONE_PERCENT;
    const rightThreshold = width * this.RIGHT_ZONE_PERCENT;

    let currentZone: 'left' | 'middle' | 'right';
    if (x < leftThreshold) {
      currentZone = 'left';
    } else if (x > rightThreshold) {
      currentZone = 'right';
    } else {
      currentZone = 'middle';
    }

    if (timeSinceLastClick < this.DOUBLE_CLICK_INTERVAL_MS && this.lastClickZone === currentZone) {
      this.lastClickTime = now;
      this.lastClickZone = currentZone;

      if (currentZone !== 'middle') {
      }
      return;
    }

    this.lastClickTime = now;
    this.lastClickZone = currentZone;

    setTimeout(() => {
      if (Date.now() - this.lastClickTime >= this.DOUBLE_CLICK_INTERVAL_MS) {
        this.processIframeClick(data);
      }
    }, this.DOUBLE_CLICK_INTERVAL_MS);
  }

  private processIframeClick(data: any): void {
    if (!this.longHoldTimeout) {
      return;
    }

    if (this.isNavigating) {
      return;
    }

    if (!this.view) return;

    const viewRect = this.view.getBoundingClientRect();
    const x = data.clientX - viewRect.left;
    const width = viewRect.width;

    const leftThreshold = width * this.LEFT_ZONE_PERCENT;
    const rightThreshold = width * this.RIGHT_ZONE_PERCENT;

    if (x < leftThreshold) {
      this.isNavigating = true;
      this.viewCallbacks?.prev();
      setTimeout(() => this.isNavigating = false, 300);
    } else if (x > rightThreshold) {
      this.isNavigating = true;
      this.viewCallbacks?.next();
      setTimeout(() => this.isNavigating = false, 300);
    } else {
      this.eventSubject.next({type: 'middle-single-tap'});
    }
  }
}
