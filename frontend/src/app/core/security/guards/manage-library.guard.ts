import {inject} from '@angular/core';
import {CanActivateFn, Router} from '@angular/router';
import {UserService} from '../../../features/settings/user-management/user.service';

export const ManageLibraryGuard: CanActivateFn = () => {
  const userService = inject(UserService);
  const router = inject(Router);
  const user = userService.currentUser();

  if (user && (user.permissions.admin || user.permissions.canManageLibrary)) {
    return true;
  }
  router.navigate(['/dashboard']);
  return false;
};
