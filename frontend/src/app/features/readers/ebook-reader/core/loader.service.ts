import {Injectable} from '@angular/core';
import {defer, from, Observable, of} from 'rxjs';
import {switchMap} from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class ReaderLoaderService {
  private scriptLoaded = false;

  loadFoliateScript(): Observable<void> {
    if (this.scriptLoaded || customElements.get('foliate-view')) {
      return of(undefined);
    }

    return defer(() => new Observable<void>(observer => {
      const script = document.createElement('script');
      script.type = 'module';
      script.src = '/assets/foliate/view.js';
      script.onload = () => {
        this.scriptLoaded = true;
        setTimeout(() => {
          observer.next();
          observer.complete();
        }, 100);
      };
      script.onerror = () => observer.error(new Error('Failed to load foliate.js'));
      document.head.appendChild(script);
    }));
  }

  waitForCustomElement(): Observable<void> {
    return defer(() => from(customElements.whenDefined('foliate-view'))).pipe(
      switchMap(() => of(undefined))
    );
  }
}
