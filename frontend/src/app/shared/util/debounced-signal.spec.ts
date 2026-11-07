import {effect, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {debouncedSignal} from './debounced-signal';

describe('debouncedSignal', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({});
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
  });

  it('exposes the initial source value immediately', () => {
    const source = signal('initial');
    const debounced = TestBed.runInInjectionContext(() => debouncedSignal(source, 200));

    expect(debounced()).toBe('initial');
  });

  it('updates only after the debounce window', () => {
    const source = signal('initial');
    const debounced = TestBed.runInInjectionContext(() => debouncedSignal(source, 200));

    source.set('changed');
    TestBed.flushEffects();
    vi.advanceTimersByTime(200 - 1);
    expect(debounced()).toBe('initial');

    vi.advanceTimersByTime(1);
    expect(debounced()).toBe('changed');
  });

  it('does not emit when a change reverts to the settled value', () => {
    const source = signal('settled');
    const debounced = TestBed.runInInjectionContext(() => debouncedSignal(source, 200));
    const values: string[] = [];
    TestBed.runInInjectionContext(() => {
      effect(() => values.push(debounced()));
    });
    TestBed.flushEffects();

    source.set('temporary');
    TestBed.flushEffects();
    vi.advanceTimersByTime(200 / 2);
    source.set('settled');
    TestBed.flushEffects();
    vi.advanceTimersByTime(200);

    expect(values).toEqual(['settled']);
  });
});
