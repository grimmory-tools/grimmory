import {computed, signal} from '@angular/core';

export type DeferredRenderMode = 'reset' | 'refresh';

export class DeferredRenderState<T> {
  private readonly _value = signal<T | undefined>(undefined);
  readonly value = this._value.asReadonly();

  private readonly _isRefreshing = signal(false);
  readonly isRefreshing = this._isRefreshing.asReadonly();

  readonly hasValue = computed(() => this._value() !== undefined);

  private activeRequestId = 0;

  begin(mode: DeferredRenderMode): number {
    this.activeRequestId += 1;

    if (mode === 'reset') {
      this._value.set(undefined);
      this._isRefreshing.set(false);
    } else {
      this._isRefreshing.set(this._value() !== undefined);
    }

    return this.activeRequestId;
  }

  commit(requestId: number, value: T): boolean {
    if (requestId !== this.activeRequestId) {
      return false;
    }

    this._value.set(value);
    this._isRefreshing.set(false);
    return true;
  }

  cancel(requestId: number): void {
    if (requestId !== this.activeRequestId) {
      return;
    }

    this._isRefreshing.set(false);
  }
}
