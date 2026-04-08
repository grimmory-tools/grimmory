import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {ReaderAnnotationService} from '../features/annotations/annotation-renderer.service';
import {ReaderEventService, ViewEvent} from './event.service';

interface TestView extends HTMLDivElement {
  addAnnotation: (annotation: {value: string}) => void;
  addAnnotationSpy: (annotation: {value: string}) => void;
}

interface RangeLike {
  toString: () => string;
  getBoundingClientRect: () => DOMRect;
  commonAncestorContainer: Node;
  startContainer: Node;
  endContainer: Node;
  intersectsNode: (node: Node) => boolean;
}

interface TestSelection {
  isCollapsed: boolean;
  rangeCount: number;
  getRangeAt: (index: number) => RangeLike;
  anchorNode: Node | null;
  focusNode: Node | null;
}

interface PrivateReaderEventService {
  longHoldTimeout: ReturnType<typeof setTimeout> | null;
  isNavigating: boolean;
  touchStartX: number;
  touchStartY: number;
  touchStartTime: number;
  selectionChangeTimeout: ReturnType<typeof setTimeout> | null;
  handleIframeClickMessage: (data: {
    type: 'iframe-click';
    clientX: number;
    clientY: number;
    iframeLeft: number;
    iframeWidth: number;
    eventClientX: number;
    target?: string;
  }) => void;
  processIframeClick: (data: {
    type: 'iframe-click';
    clientX: number;
    clientY: number;
    iframeLeft: number;
    iframeWidth: number;
    eventClientX: number;
    target?: string;
  }) => void;
  handleSelectionEnd: (doc: Document) => void;
  handleSelectionChange: (doc: Document) => void;
  handleTouchEnd: (event: TouchEvent, doc: Document) => void;
}

describe('ReaderEventService', () => {
  const getAllAnnotations = vi.fn(() => [] as {value: string}[]);
  const getAnnotationStyle = vi.fn();
  const getOverlayerDrawFunction = vi.fn(() => vi.fn(() => document.createElementNS('http://www.w3.org/2000/svg', 'path')));
  const prev = vi.fn();
  const next = vi.fn();
  const getCFI = vi.fn();
  const getContents = vi.fn();
  const postMessageSpy = vi.spyOn(window, 'postMessage').mockImplementation(() => undefined);

  let service: ReaderEventService;
  let privateService: PrivateReaderEventService;
  let view: TestView;
  let emittedEvents: ViewEvent[];
  let defaultSelection: TestSelection;
  let defaultView: {
    frameElement: HTMLIFrameElement;
    getSelection: () => TestSelection;
  };
  let doc: Document;
  let iframe: HTMLIFrameElement;

  function createView(width = 600): TestView {
    const element = document.createElement('div') as TestView;
    element.addAnnotationSpy = vi.fn();
    element.addAnnotation = annotation => {
      element.addAnnotationSpy(annotation);
    };
    Object.defineProperty(element, 'getBoundingClientRect', {
      value: () => new DOMRect(20, 10, width, 400),
    });
    return element;
  }

  function installSelection(targetDoc: Document, selection: TestSelection): void {
    const currentDefaultView = targetDoc.defaultView;
    if (!currentDefaultView) {
      throw new Error('expected defaultView');
    }
    Object.defineProperty(currentDefaultView, 'getSelection', {
      value: () => selection,
      configurable: true,
    });
  }

  function createTouchEvent(
    type: string,
    changed: {clientX: number; clientY: number}[],
    active: {clientX: number; clientY: number}[] = changed,
  ): TouchEvent {
    const event = new Event(type, {bubbles: true, cancelable: true}) as TouchEvent;
    Object.defineProperty(event, 'changedTouches', {value: changed, configurable: true});
    Object.defineProperty(event, 'touches', {value: active, configurable: true});
    return event;
  }

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-03-27T03:30:00Z'));
    Reflect.deleteProperty(window, 'ontouchstart');
    Object.defineProperty(navigator, 'maxTouchPoints', {
      value: 0,
      configurable: true,
    });

    getAllAnnotations.mockReset();
    getAllAnnotations.mockReturnValue([]);
    getAnnotationStyle.mockReset();
    getOverlayerDrawFunction.mockReset();
    getOverlayerDrawFunction.mockReturnValue(vi.fn(() => document.createElementNS('http://www.w3.org/2000/svg', 'path')));
    prev.mockReset();
    next.mockReset();
    getCFI.mockReset();
    getContents.mockReset();
    postMessageSpy.mockClear();

    doc = document.implementation.createHTMLDocument('reader-doc');
    iframe = doc.createElement('iframe');
    Object.defineProperty(iframe, 'getBoundingClientRect', {
      value: () => new DOMRect(120, 80, 500, 400),
    });

    defaultSelection = {
      isCollapsed: true,
      rangeCount: 0,
      getRangeAt: () => ({
        toString: () => '',
        getBoundingClientRect: () => new DOMRect(0, 0, 0, 0),
        commonAncestorContainer: doc.body,
        startContainer: doc.body,
        endContainer: doc.body,
        intersectsNode: () => false,
      }),
      anchorNode: null,
      focusNode: null,
    };
    defaultView = {
      frameElement: iframe,
      getSelection: () => defaultSelection,
    };
    Object.defineProperty(doc, 'defaultView', {
      value: defaultView,
      configurable: true,
    });
    installSelection(doc, defaultSelection);

    TestBed.configureTestingModule({
      providers: [
        {
          provide: ReaderAnnotationService,
          useValue: {
            getAllAnnotations,
            getAnnotationStyle,
            getOverlayerDrawFunction,
          },
        },
      ],
    });

    service = TestBed.inject(ReaderEventService);
    privateService = service as unknown as PrivateReaderEventService;
    view = createView();
    emittedEvents = [];
    service.events$.subscribe(event => emittedEvents.push(event));
    service.initialize(view, {prev, next, getCFI, getContents});
  });

  afterEach(() => {
    vi.useRealTimers();
    service.destroy();
  });

  it('hydrates annotations on load and forwards load events', () => {
    getAllAnnotations.mockReturnValue([{value: 'cfi-1'}, {value: 'cfi-2'}]);

    view.dispatchEvent(new CustomEvent('load', {detail: {doc}}));
    vi.advanceTimersByTime(100);

    expect(view.addAnnotationSpy).toHaveBeenNthCalledWith(1, {value: 'cfi-1'});
    expect(view.addAnnotationSpy).toHaveBeenNthCalledWith(2, {value: 'cfi-2'});
    expect(emittedEvents).toContainEqual({type: 'load', detail: {doc}});
  });

  it('navigates and emits keyboard events for reader shortcuts while ignoring editable targets', () => {
    const input = document.createElement('input');
    input.dispatchEvent(new KeyboardEvent('keydown', {key: 'ArrowLeft', bubbles: true}));
    expect(prev).not.toHaveBeenCalled();

    document.dispatchEvent(new KeyboardEvent('keydown', {key: 'ArrowLeft', bubbles: true}));
    document.dispatchEvent(new KeyboardEvent('keydown', {key: 'ArrowRight', bubbles: true}));
    document.dispatchEvent(new KeyboardEvent('keydown', {key: ' ', shiftKey: true, bubbles: true}));
    document.dispatchEvent(new KeyboardEvent('keydown', {key: ' ', bubbles: true}));
    document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Home', bubbles: true}));
    document.dispatchEvent(new KeyboardEvent('keydown', {key: 'End', bubbles: true}));
    document.dispatchEvent(new KeyboardEvent('keydown', {key: 'f', bubbles: true}));
    document.dispatchEvent(new KeyboardEvent('keydown', {key: 't', bubbles: true}));
    document.dispatchEvent(new KeyboardEvent('keydown', {key: 's', bubbles: true}));
    document.dispatchEvent(new KeyboardEvent('keydown', {key: 'n', bubbles: true}));
    document.dispatchEvent(new KeyboardEvent('keydown', {key: '?', bubbles: true}));
    document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true}));

    expect(prev).toHaveBeenCalledTimes(2);
    expect(next).toHaveBeenCalledTimes(2);
    expect(emittedEvents.map(event => event.type)).toEqual(expect.arrayContaining([
      'go-first-section',
      'go-last-section',
      'toggle-fullscreen',
      'toggle-toc',
      'toggle-search',
      'toggle-notes',
      'toggle-shortcuts-help',
      'escape-pressed',
    ]));
  });

  it('handles iframe clicks with double-click suppression and middle tap fallback', () => {
    privateService.longHoldTimeout = setTimeout(() => undefined, 1_000);

    privateService.handleIframeClickMessage({
      type: 'iframe-click',
      clientX: 40,
      clientY: 100,
      iframeLeft: 0,
      iframeWidth: 600,
      eventClientX: 20,
    });
    vi.advanceTimersByTime(300);
    expect(prev).toHaveBeenCalledTimes(1);

    vi.setSystemTime(new Date('2026-03-27T03:30:01Z'));
    privateService.handleIframeClickMessage({
      type: 'iframe-click',
      clientX: 280,
      clientY: 100,
      iframeLeft: 0,
      iframeWidth: 600,
      eventClientX: 260,
    });
    vi.setSystemTime(new Date('2026-03-27T03:30:01.100Z'));
    privateService.handleIframeClickMessage({
      type: 'iframe-click',
      clientX: 285,
      clientY: 100,
      iframeLeft: 0,
      iframeWidth: 600,
      eventClientX: 265,
    });
    vi.advanceTimersByTime(300);

    expect(emittedEvents.filter(event => event.type === 'middle-single-tap')).toHaveLength(1);

    vi.setSystemTime(new Date('2026-03-27T03:30:02Z'));
    privateService.handleIframeClickMessage({
      type: 'iframe-click',
      clientX: 300,
      clientY: 100,
      iframeLeft: 0,
      iframeWidth: 600,
      eventClientX: 280,
      target: 'SPAN',
    });
    vi.advanceTimersByTime(300);

    expect(emittedEvents.filter(event => event.type === 'middle-single-tap')).toHaveLength(2);
    expect(emittedEvents.at(-1)?.type).toBe('middle-single-tap');
  });

  it('processes touch gestures for swipe navigation, short taps, and selected text', () => {
    privateService.longHoldTimeout = setTimeout(() => undefined, 1_000);
    privateService.touchStartX = 150;
    privateService.touchStartY = 120;
    privateService.touchStartTime = Date.now() - 100;

    const swipeLeft = createTouchEvent('touchend', [{clientX: 60, clientY: 120}]);
    privateService.handleTouchEnd(swipeLeft, doc);
    expect(next).toHaveBeenCalledTimes(1);

    privateService.isNavigating = false;
    privateService.touchStartX = 120;
    privateService.touchStartY = 90;
    privateService.touchStartTime = Date.now() - 100;

    const tap = createTouchEvent('touchend', [{clientX: 122, clientY: 92}]);
    privateService.handleTouchEnd(tap, doc);
    expect(postMessageSpy).toHaveBeenCalledWith(expect.objectContaining({
      type: 'iframe-click',
      target: undefined,
    }), '*');

    const selectedRange: RangeLike = {
      toString: () => 'Highlighted text',
      getBoundingClientRect: () => new DOMRect(20, 15, 80, 20),
      commonAncestorContainer: doc.body,
      startContainer: doc.body,
      endContainer: doc.body,
      intersectsNode: () => false,
    };
    installSelection(doc, {
      isCollapsed: false,
      rangeCount: 1,
      getRangeAt: () => selectedRange,
      anchorNode: doc.body,
      focusNode: doc.body,
    });
    getContents.mockReturnValue([{index: 7, doc}]);
    getCFI.mockReturnValue('epubcfi(/6/2!/4/2:0)');

    const selectedTouch = createTouchEvent('touchend', [{clientX: 140, clientY: 120}]);
    privateService.handleTouchEnd(selectedTouch, doc);
    vi.advanceTimersByTime(50);
    vi.advanceTimersByTime(10);

    expect(emittedEvents.at(-1)).toMatchObject({
      type: 'text-selected',
      detail: {
        text: 'Highlighted text',
        cfi: 'epubcfi(/6/2!/4/2:0)',
        index: 7,
      },
    });
  });

  it('debounces selection changes and only emits when selection text and CFI are available', () => {
    const emptyRange: RangeLike = {
      toString: () => '   ',
      getBoundingClientRect: () => new DOMRect(0, 0, 0, 0),
      commonAncestorContainer: doc.body,
      startContainer: doc.body,
      endContainer: doc.body,
      intersectsNode: () => false,
    };
    installSelection(doc, {
      isCollapsed: false,
      rangeCount: 1,
      getRangeAt: () => emptyRange,
      anchorNode: doc.body,
      focusNode: doc.body,
    });

    privateService.handleSelectionChange(doc);
    vi.advanceTimersByTime(300);
    expect(emittedEvents).toHaveLength(0);

    const visibleRange: RangeLike = {
      toString: () => 'Visible note',
      getBoundingClientRect: () => new DOMRect(10, 10, 60, 25),
      commonAncestorContainer: doc.body,
      startContainer: doc.body,
      endContainer: doc.body,
      intersectsNode: () => false,
    };
    installSelection(doc, {
      isCollapsed: false,
      rangeCount: 1,
      getRangeAt: () => visibleRange,
      anchorNode: doc.body,
      focusNode: doc.body,
    });
    getContents.mockReturnValue([{index: 3, doc}]);
    getCFI.mockReturnValueOnce(null).mockReturnValueOnce('epubcfi(/6/8!/4/2:0)');

    privateService.handleSelectionEnd(doc);
    vi.advanceTimersByTime(10);
    expect(emittedEvents).toHaveLength(0);

    privateService.handleSelectionEnd(doc);
    vi.advanceTimersByTime(10);
    expect(emittedEvents.at(-1)).toMatchObject({
      type: 'text-selected',
      detail: {
        text: 'Visible note',
        cfi: 'epubcfi(/6/8!/4/2:0)',
        index: 3,
      },
      popupPosition: {
        showBelow: true,
      },
    });
  });

  it('styles draw-annotation events when a stored annotation style exists', () => {
    const draw = vi.fn();
    const overlayer = vi.fn(() => document.createElementNS('http://www.w3.org/2000/svg', 'rect'));

    getAnnotationStyle.mockReturnValue({color: '#0f0', style: 'highlight'});
    getOverlayerDrawFunction.mockReturnValue(overlayer);

    view.dispatchEvent(new CustomEvent('draw-annotation', {
      detail: {
        draw,
        annotation: {value: 'epubcfi(/6/2!/4/2:0)'},
        doc,
        range: document.createRange(),
      },
    }));

    expect(getAnnotationStyle).toHaveBeenCalledWith('epubcfi(/6/2!/4/2:0)');
    expect(draw).toHaveBeenCalledWith(overlayer, {color: '#0f0'});
    expect(emittedEvents.at(-1)?.type).toBe('draw-annotation');
  });
});
