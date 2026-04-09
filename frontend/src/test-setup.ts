import '@angular/compiler';
import 'zone.js';
import 'zone.js/testing';
import '@testing-library/jest-dom/vitest';
import 'vitest-canvas-mock';
import {TestBed} from '@angular/core/testing';
import {BrowserDynamicTestingModule, platformBrowserDynamicTesting} from '@angular/platform-browser-dynamic/testing';

declare global {
  var __ANGULAR_TESTBED_INITIALIZED__: boolean | undefined;
}

class MockResizeObserver {
  observe(): undefined { return undefined; }
  unobserve(): undefined { return undefined; }
  disconnect(): undefined { return undefined; }
}

class MockIntersectionObserver {
  readonly root = null;
  readonly rootMargin = '';
  readonly thresholds = [0];
  readonly scrollMargin = '';

  observe(): undefined { return undefined; }
  unobserve(): undefined { return undefined; }
  disconnect(): undefined { return undefined; }
  takeRecords(): IntersectionObserverEntry[] {
    return [];
  }
}

if (!globalThis.matchMedia) {
  globalThis.matchMedia = (query: string): MediaQueryList => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    addListener: () => undefined,
    removeListener: () => undefined,
    dispatchEvent: () => false,
  });
}

if (!globalThis.ResizeObserver) {
  globalThis.ResizeObserver = MockResizeObserver as typeof ResizeObserver;
}

if (!globalThis.IntersectionObserver) {
  globalThis.IntersectionObserver = MockIntersectionObserver as typeof IntersectionObserver;
}

if (!globalThis.PointerEvent) {
  globalThis.PointerEvent = MouseEvent as typeof PointerEvent;
}

if (!globalThis.DOMRect) {
  globalThis.DOMRect = class DOMRectMock implements DOMRect {
    bottom = 0;
    height = 0;
    left = 0;
    right = 0;
    top = 0;
    width = 0;
    x = 0;
    y = 0;
    toJSON(): Record<string, number> {
      return {
        bottom: this.bottom,
        height: this.height,
        left: this.left,
        right: this.right,
        top: this.top,
        width: this.width,
        x: this.x,
        y: this.y
      };
    }
  } as typeof DOMRect;
}

if (!window.scrollTo) {
  window.scrollTo = () => undefined;
}

if (!window.HTMLElement.prototype.scrollIntoView) {
  window.HTMLElement.prototype.scrollIntoView = () => undefined;
}

if (!window.URL.createObjectURL) {
  window.URL.createObjectURL = () => 'blob:mock-url';
}

if (!window.URL.revokeObjectURL) {
  window.URL.revokeObjectURL = () => undefined;
}

// Only initialize if not already initialized
if (!globalThis.__ANGULAR_TESTBED_INITIALIZED__) {
  globalThis.__ANGULAR_TESTBED_INITIALIZED__ = true;
  TestBed.initTestEnvironment(
    BrowserDynamicTestingModule,
    platformBrowserDynamicTesting(),
    {teardown: {destroyAfterEach: true}}
  );
}
