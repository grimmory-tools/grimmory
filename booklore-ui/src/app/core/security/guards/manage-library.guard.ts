import {inject} from '@angular/core';
import {CanActivateFn, Router} from '@angular/router';
import {UserService} from '../../../features/settings/user-management/user.service';
import {map} from 'rxjs/operators';

export const ManageLibraryGuard: CanActivateFn = () => {
  const userService = inject(UserService);
  const router = inject(Router);

  return userService.userState$.pipe(
    map(state => {
      const user = state.user;
      if (user && (user.permissions.admin || user.permissions.canManipulateLibrary)) {
        return true;
      }
      router.navigate(['/dashboard']);
      return false;
    })
  );
};
