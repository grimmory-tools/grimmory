import {Injectable} from '@angular/core';
import {Observable, of} from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class PostLoginInitializerService {

  initialize(): Observable<void> {
    return of(undefined);
  }
}
