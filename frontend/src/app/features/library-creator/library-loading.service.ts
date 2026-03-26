import {Injectable, ComponentRef, ApplicationRef, createComponent, EmbeddedViewRef, EnvironmentInjector, inject} from '@angular/core';
import { LibraryLoadingComponent } from './library-loading/library-loading.component';

@Injectable({
  providedIn: 'root'
})
export class LibraryLoadingService {
  private appRef = inject(ApplicationRef);
  private injector = inject(EnvironmentInjector);
  private componentRef: ComponentRef<LibraryLoadingComponent> | null = null;

  showBookLoadingProgress(bookTitle: string, current: number, total: number): void {
    if (this.componentRef) {
      this.updateProgress(bookTitle, current, total);
      return;
    }

    this.componentRef = createComponent(LibraryLoadingComponent, {
      environmentInjector: this.injector
    });

    this.componentRef.instance.updateProgress(bookTitle, current, total);

    this.appRef.attachView(this.componentRef.hostView);
    const domElem = (this.componentRef.hostView as EmbeddedViewRef<unknown>).rootNodes[0] as HTMLElement;
    document.body.appendChild(domElem);
    document.body.style.overflow = 'hidden';
  }

  updateProgress(bookTitle: string, current: number, total: number): void {
    if (this.componentRef) {
      this.componentRef.instance.updateProgress(bookTitle, current, total);
    }
  }

  hide(): void {
    if (this.componentRef) {
      this.appRef.detachView(this.componentRef.hostView);
      this.componentRef.destroy();
      this.componentRef = null;
      document.body.style.overflow = '';
    }
  }
}
