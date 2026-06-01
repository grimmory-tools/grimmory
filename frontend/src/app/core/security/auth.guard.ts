import {inject} from '@angular/core';
import {ActivatedRouteSnapshot, CanActivateChildFn, CanActivateFn, Router, RouterStateSnapshot} from '@angular/router';
import {AuthService} from '../../shared/service/auth.service';
import {map} from 'rxjs/operators';

const authenticateRoute = (route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => {
  void route;
  void state;
  const router = inject(Router);
  const authService = inject(AuthService);

  return authService.ensureAuthenticated().pipe(
    map(isAuthenticated => {
      if (!isAuthenticated) {
        return router.createUrlTree(['/login']);
      }

      const isDefaultPassword = authService.getIsDefaultPassword();
      if (isDefaultPassword) {
        return router.createUrlTree(['/change-password']);
      }

      return true;
    })
  );
};

export const AuthGuard: CanActivateFn = authenticateRoute;
export const AuthChildGuard: CanActivateChildFn = authenticateRoute;
