import {Injectable} from '@angular/core';
import {SafeHtml} from '@angular/platform-browser';
import {BehaviorSubject} from 'rxjs';

interface CachedIcon {
  content: string;
  sanitized: SafeHtml;
}

@Injectable({
  providedIn: 'root'
})
export class IconCacheService {
  private cache = new Map<string, CachedIcon>();

  private cacheUpdate$ = new BehaviorSubject<string | null>(null);

  getCachedSanitized(iconName: string): SafeHtml | null {
    const cached = this.cache.get(iconName);

    if (!cached) {
      return null;
    }

    return cached.sanitized;
  }

  cacheIcon(iconName: string, content: string, sanitized: SafeHtml): void {
    this.cache.set(iconName, {
      content,
      sanitized
    });
    this.cacheUpdate$.next(iconName);
  }

  removeIcon(iconName: string): boolean {
    return this.cache.delete(iconName);
  }

  getAllIconNames(): string[] {
    return Array.from(this.cache.keys()).sort();
  }
}
