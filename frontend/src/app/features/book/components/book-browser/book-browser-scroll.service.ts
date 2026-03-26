import {Injectable} from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class BookBrowserScrollService {
  private scrollPositions = new Map<string, number>();

  savePosition(key: string, position: number): void {
    this.scrollPositions.set(key, position);
  }

  getPosition(key: string): number | undefined {
    return this.scrollPositions.get(key);
  }

  clearPosition(key: string): void {
    this.scrollPositions.delete(key);
  }

  createKey(path: string, params: Record<string, string>): string {
    const paramValues = Object.values(params).join('-');
    return paramValues ? `${path}:${paramValues}` : path;
  }
}
