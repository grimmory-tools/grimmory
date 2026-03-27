import {Injectable, inject} from '@angular/core';
import {AuthService} from './auth.service';
import {UserService} from '../../features/settings/user-management/user.service';
import {QueryClient} from '@tanstack/angular-query-experimental';

@Injectable({providedIn: 'root'})
export class StartupService {
  private authService = inject(AuthService);
  private userService = inject(UserService);
  private queryClient = inject(QueryClient);

  load(): Promise<void> {
    if (this.authService.token()) {
      return this.queryClient.fetchQuery(this.userService.getUserQueryOptions()).then(() => undefined);
    }

    return Promise.resolve();
  }
}
