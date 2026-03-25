import {inject} from '@angular/core';
import {CanActivateFn, Router} from '@angular/router';
import {AuthService} from '../../shared/service/auth.service';

export const AuthGuard: CanActivateFn = (route, state) => {
  const router = inject(Router);
  const authService = inject(AuthService);

  const internalAccessToken = authService.getInternalAccessToken();

  if (internalAccessToken) {
    try {
      const payload = JSON.parse(atob(internalAccessToken.split('.')[1]));
      if (payload.exp && payload.exp * 1000 < Date.now()) {
        localStorage.removeItem('accessToken_Internal');
        return router.createUrlTree(['/login']);
      }
      if (payload.isDefaultPassword) {
        router.navigate(['/change-password']);
        return false;
      }
      return true;
    } catch (e) {
      localStorage.removeItem('accessToken_Internal');
      router.navigate(['/login']);
      return false;
    }
  }

  router.navigate(['/login']);
  return false;
};
