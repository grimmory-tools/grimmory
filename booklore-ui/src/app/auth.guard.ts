import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { Router } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';

export const AuthGuard: CanActivateFn = (route, state) => {
  const router = inject(Router);

  const legacyToken = localStorage.getItem('accessToken_Internal');

  if (legacyToken) {
    try {
      const payload = JSON.parse(atob(legacyToken.split('.')[1]));
      if (payload.isDefaultPassword) {
        router.navigate(['/change-password']);
        return false;
      }
      return true;
    } catch (e) {
      console.error('Invalid legacy token:', e);
      localStorage.removeItem('accessToken');
      router.navigate(['/login']);
      return false;
    }
  }

  const oidcToken = localStorage.getItem('accessToken_OIDC');

  if (oidcToken) {
    return true;
  }

  router.navigate(['/login']);
  return false;
};
