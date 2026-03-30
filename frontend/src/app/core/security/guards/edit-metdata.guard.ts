import {inject} from '@angular/core';
import {ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot} from '@angular/router';
import {UserService} from '../../../features/settings/user-management/user.service';

export const EditMetadataGuard: CanActivateFn = (route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => {
  void route;
  void state;
  const userService = inject(UserService);
  const router = inject(Router);
  const user = userService.currentUser();

  if (user && (user.permissions.admin || user.permissions.canEditMetadata)) {
    return true;
  }
  router.navigate(['/dashboard']);
  return false;
};
