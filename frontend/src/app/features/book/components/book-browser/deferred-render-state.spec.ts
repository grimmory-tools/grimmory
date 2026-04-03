import {describe, expect, it} from 'vitest';

import {DeferredRenderState} from './deferred-render-state';

describe('DeferredRenderState', () => {
  it('clears the current value for reset runs', () => {
    const state = new DeferredRenderState<number[]>();

    const initialRequest = state.begin('reset');
    state.commit(initialRequest, [1, 2, 3]);

    expect(state.value()).toEqual([1, 2, 3]);
    expect(state.hasValue()).toBe(true);

    state.begin('reset');

    expect(state.value()).toBeUndefined();
    expect(state.hasValue()).toBe(false);
    expect(state.isRefreshing()).toBe(false);
  });

  it('keeps the current value visible during refresh runs', () => {
    const state = new DeferredRenderState<number[]>();

    const initialRequest = state.begin('reset');
    state.commit(initialRequest, [1, 2, 3]);

    const refreshRequest = state.begin('refresh');

    expect(state.value()).toEqual([1, 2, 3]);
    expect(state.isRefreshing()).toBe(true);

    state.commit(refreshRequest, [4, 5]);

    expect(state.value()).toEqual([4, 5]);
    expect(state.isRefreshing()).toBe(false);
  });

  it('ignores stale commits from older requests', () => {
    const state = new DeferredRenderState<number[]>();

    const initialRequest = state.begin('reset');
    state.commit(initialRequest, [1]);

    const staleRefresh = state.begin('refresh');
    const latestRefresh = state.begin('refresh');

    expect(state.commit(staleRefresh, [2])).toBe(false);
    expect(state.value()).toEqual([1]);
    expect(state.isRefreshing()).toBe(true);

    expect(state.commit(latestRefresh, [3])).toBe(true);
    expect(state.value()).toEqual([3]);
    expect(state.isRefreshing()).toBe(false);
  });

  it('ignores cancel with a stale request id', () => {
    const state = new DeferredRenderState<number[]>();

    const first = state.begin('reset');
    state.commit(first, [1]);

    const stale = state.begin('refresh');
    const current = state.begin('refresh');

    state.cancel(stale);

    // Cancel was a no-op — isRefreshing still true from the current request
    expect(state.isRefreshing()).toBe(true);
    expect(state.value()).toEqual([1]);

    state.commit(current, [2]);
    expect(state.isRefreshing()).toBe(false);
    expect(state.value()).toEqual([2]);
  });

  it('cancel during a reset leaves value undefined', () => {
    const state = new DeferredRenderState<number[]>();

    const request = state.begin('reset');

    expect(state.value()).toBeUndefined();
    expect(state.isRefreshing()).toBe(false);

    state.cancel(request);

    expect(state.value()).toBeUndefined();
    expect(state.isRefreshing()).toBe(false);
  });

  it('does not set isRefreshing when no value exists yet', () => {
    const state = new DeferredRenderState<number[]>();

    const request = state.begin('refresh');

    // No prior value, so nothing to "refresh" — isRefreshing stays false
    expect(state.isRefreshing()).toBe(false);
    expect(state.value()).toBeUndefined();
    expect(state.hasValue()).toBe(false);

    state.commit(request, [1]);
    expect(state.value()).toEqual([1]);
    expect(state.isRefreshing()).toBe(false);
  });
});
