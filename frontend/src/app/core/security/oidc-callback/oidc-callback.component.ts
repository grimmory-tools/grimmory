import {Component, inject, OnInit} from '@angular/core';
import {Router} from '@angular/router';
import {TranslocoPipe} from '@jsverse/transloco';
import {OidcService} from '../oidc.service';
import {AuthService} from '../../../shared/service/auth.service';

@Component({
  selector: 'app-oidc-callback',
  templateUrl: './oidc-callback.component.html',
  styleUrls: ['./oidc-callback.component.scss'],
  imports: [TranslocoPipe]
})
export class OidcCallbackComponent implements OnInit {
  private router = inject(Router);
  private oidcService = inject(OidcService);
  private authService = inject(AuthService);

  ngOnInit(): void {
    const params = new URLSearchParams(window.location.search);

    const error = params.get('error');
    if (error) {
      const description = params.get('error_description') || error;
      console.error('[OIDC Callback] Provider returned error:', error, description);
      this.router.navigate(['/login'], {queryParams: {oidcError: description}});
      return;
    }

    const code = params.get('code');
    const returnedState = params.get('state');

    if (!code || !returnedState) {
      console.error('[OIDC Callback] Missing code or state');
      this.router.navigate(['/login'], {queryParams: {oidcError: 'missing_code'}});
      return;
    }

    const pkceState = this.oidcService.retrievePkceState(returnedState);
    if (!pkceState) {
      console.error('[OIDC Callback] No PKCE state found for state parameter');
      this.router.navigate(['/login'], {queryParams: {oidcError: 'missing_pkce_state'}});
      return;
    }

    if (returnedState !== pkceState.state) {
      console.error('[OIDC Callback] State mismatch');
      this.router.navigate(['/login'], {queryParams: {oidcError: 'state_mismatch'}});
      return;
    }

    this.oidcService.exchangeCode(code, pkceState.codeVerifier, pkceState.nonce, pkceState.state).subscribe({
      next: (response) => {
        sessionStorage.removeItem('oidc_redirect_count');
        this.authService.saveInternalTokens(response.accessToken, response.refreshToken);
        this.authService.initializeWebSocketConnection();
        if (response.isDefaultPassword === 'true') {
          this.router.navigate(['/change-password']);
        } else {
          this.router.navigate(['/dashboard']);
        }
      },
      error: (err) => {
        console.error('[OIDC Callback] Token exchange failed', err);
        const errorMsg = err.error?.message || 'exchange_failed';
        this.router.navigate(['/login'], {queryParams: {oidcError: errorMsg}});
      }
    });
  }
}
