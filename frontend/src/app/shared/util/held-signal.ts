import {linkedSignal, type Signal} from '@angular/core';

export function heldSignal<S>(value: () => S, hold: () => boolean): Signal<S> {
  return linkedSignal<{stale: boolean; value: S}, S>({
    source: () => ({stale: hold(), value: value()}),
    computation: (source, previous) => (source.stale && previous ? previous.value : source.value),
  });
}
