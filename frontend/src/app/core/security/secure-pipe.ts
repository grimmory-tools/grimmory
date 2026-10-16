import {inject, Pipe, PipeTransform} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

@Pipe({
  name: 'secure',
  pure: true
})
export class SecurePipe implements PipeTransform {
  private readonly http = inject(HttpClient);
  private readonly sanitizer = inject(DomSanitizer);

  transform(url: string): Observable<SafeUrl> {
    return this.http.get(url, { responseType: 'blob' }).pipe(
      map(blob => this.sanitizer.bypassSecurityTrustUrl(URL.createObjectURL(blob)))
    );
  }
}
