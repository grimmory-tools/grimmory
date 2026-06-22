import {Injectable} from '@angular/core';
import {BehaviorSubject} from 'rxjs';

interface CachedIcon {
  content: string;
  sanitized: string;
}

@Injectable({
  providedIn: 'root'
})
export class CustomSvgCacheService {
  private readonly cache = new Map<string, CachedIcon>();

  private readonly cacheUpdate$ = new BehaviorSubject<string | null>(null);

  getCachedSanitized(iconName: string): string | null {
    const cached = this.cache.get(iconName);

    if (!cached) {
      return null;
    }

    return cached.sanitized;
  }

  cacheIcon(iconName: string, content: string, sanitized: string): void {
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
    return Array.from(this.cache.keys()).sort((left, right) => left.localeCompare(right));
  }
}
