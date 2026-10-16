import {Service, inject, Injector} from '@angular/core';
import {defer, from, Observable} from 'rxjs';
import {StartupService} from '../../shared/service/startup.service';

@Service()
export class PostLoginInitializerService {
  private readonly injector = inject(Injector);

  initialize(): Observable<void> {
    return defer(() => from(this.injector.get(StartupService).load()));
  }
}
