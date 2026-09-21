import {Injector, Signal} from '@angular/core';
import {toObservable, toSignal} from '@angular/core/rxjs-interop';
import {debounceTime, distinctUntilChanged} from 'rxjs';

export function debouncedSignal<T>(source: Signal<T>, ms: number, injector?: Injector): Signal<T> {
  return toSignal(
    toObservable(source, {injector}).pipe(debounceTime(ms), distinctUntilChanged()),
    {initialValue: source(), injector},
  );
}

