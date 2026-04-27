import {Pipe, PipeTransform} from '@angular/core';

/**
 * Appends the `ngsw-bypass=true` query parameter so the Angular service worker
 * does not intercept the request. Useful for cross-origin image URLs whose
 * default SW passthrough produces responses that browsers refuse to render.
 */
@Pipe({name: 'ngswBypass', standalone: true, pure: true})
export class NgswBypassPipe implements PipeTransform {
  transform(url: string | null | undefined): string | null {
    if (!url) return null;
    return url + (url.includes('?') ? '&' : '?') + 'ngsw-bypass=true';
  }
}
