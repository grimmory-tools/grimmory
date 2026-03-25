import { Directive, ElementRef, Input, OnChanges, OnDestroy, inject, SimpleChanges } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subscription } from 'rxjs';

@Directive({
  selector: '[appSecureSrc]',
  standalone: true
})
export class SecureSrcDirective implements OnChanges, OnDestroy {
  @Input('appSecureSrc') src!: string;

  private currentUrl: string | null = null;
  private subscription: Subscription | null = null;

  private el = inject(ElementRef);
  private http = inject(HttpClient);

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['src']) {
      this.loadImage();
    }
  }

  private loadImage(): void {
    this.cleanup();

    if (!this.src) {
      return;
    }

    this.subscription = this.http.get(this.src, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        this.currentUrl = URL.createObjectURL(blob);
        this.el.nativeElement.src = this.currentUrl;
      },
      error: (err) => {
        // Only log non-CORS errors to avoid console spam
        // CORS errors are expected in development and will be resolved by backend CORS config
        if (err.status !== 0 && err.status !== 403) {
          console.error('Error loading secure image:', err);
        }
        // Set a fallback placeholder image if available
        const fallbackSrc = this.el.nativeElement.getAttribute('data-fallback-src') || 'assets/images/missing-cover.jpg';
        this.el.nativeElement.src = fallbackSrc;
      }
    });
  }

  private cleanup(): void {
    if (this.subscription) {
      this.subscription.unsubscribe();
      this.subscription = null;
    }
    if (this.currentUrl) {
      URL.revokeObjectURL(this.currentUrl);
      this.currentUrl = null;
    }
  }

  ngOnDestroy(): void {
    this.cleanup();
  }
}
