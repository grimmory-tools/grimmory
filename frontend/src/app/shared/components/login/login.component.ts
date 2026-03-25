import {Component, effect, inject, OnInit} from '@angular/core';
import {AuthService} from '../../service/auth.service';
import {ActivatedRoute, Router} from '@angular/router';
import {FormsModule} from '@angular/forms';
import {Password} from 'primeng/password';
import {Button} from 'primeng/button';
import {Message} from 'primeng/message';
import {InputText} from 'primeng/inputtext';
import {take} from 'rxjs/operators';
import {AppSettingsService} from '../../service/app-settings.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {OidcService} from '../../../core/security/oidc.service';

@Component({
  selector: 'app-login',
  imports: [
    FormsModule,
    Password,
    Button,
    Message,
    InputText,
    TranslocoDirective
  ],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit {
  username = '';
  password = '';
  errorMessage = '';
  infoMessage = '';
  oidcEnabled = false;
  oidcName = 'OIDC';
  isOidcLoginInProgress = false;
  showLocalLogin = true;
  private oidcOnlyAutoRedirect = false;
  private authService = inject(AuthService);
  private oidcService = inject(OidcService);
  private appSettingsService = inject(AppSettingsService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private translocoService = inject(TranslocoService);

  private readonly initPublicSettingsEffect = effect(() => {
    const publicSettings = this.appSettingsService.publicAppSettings();
    if (!publicSettings) return;

    this.oidcEnabled = publicSettings.oidcEnabled;
    this.oidcName = publicSettings.oidcProviderDetails?.providerName || 'OIDC';

    if (publicSettings.oidcForceOnlyMode) {
      this.route.queryParams.pipe(take(1)).subscribe(params => {
        const isLocalMode = params['local'] === 'true';
        const hasOidcError = !!params['oidcError'];

        if (!isLocalMode && !hasOidcError) {
          this.handleAutoRedirect();
        }
      });
    }
  });

  private static readonly OIDC_ERROR_KEYS: Record<string, string> = {
    'state_mismatch': 'auth.login.oidcErrors.stateMismatch',
    'missing_code': 'auth.login.oidcErrors.stateMismatch',
    'exchange_failed': 'auth.login.oidcErrors.exchangeFailed'
  };

  private static readonly MAX_REDIRECT_COUNT = 3;

  ngOnInit(): void {
    this.authService.clearSessionOnLoginPage();

    this.route.queryParams.pipe(take(1)).subscribe(params => {
      if (params['reason'] === 'session_revoked') {
        this.infoMessage = this.translocoService.translate('auth.login.sessionRevoked');
      }

      const oidcError = params['oidcError'];
      if (oidcError) {
        this.errorMessage = this.resolveOidcError(oidcError);
      }
    });

  }

  private handleAutoRedirect(): void {
    const countStr = sessionStorage.getItem('oidc_redirect_count');
    const count = countStr ? parseInt(countStr, 10) : 0;

    if (count >= LoginComponent.MAX_REDIRECT_COUNT) {
      sessionStorage.removeItem('oidc_redirect_count');
      this.errorMessage = this.translocoService.translate('auth.login.oidcOnlyRedirectFailed');
      this.showLocalLogin = false;
      return;
    }

    this.oidcOnlyAutoRedirect = true;
    sessionStorage.setItem('oidc_redirect_count', String(count + 1));
    this.showLocalLogin = false;
    this.loginWithOidc();
  }

  private resolveOidcError(error: string): string {
    if (error.startsWith('OIDC user')) {
      return this.translocoService.translate('auth.login.oidcErrors.userNotProvisioned');
    }
    if (error.startsWith('Cannot reach')) {
      return this.translocoService.translate('auth.login.oidcErrors.providerUnreachable');
    }
    if (error.startsWith('Invalid token') || error.startsWith('No token')) {
      return this.translocoService.translate('auth.login.oidcErrors.invalidToken');
    }
    if (error.startsWith('Failed to exchange') || error.includes('token exchange')) {
      return this.translocoService.translate('auth.login.oidcErrors.exchangeFailed');
    }

    const key = LoginComponent.OIDC_ERROR_KEYS[error];
    if (key) {
      return this.translocoService.translate(key);
    }

    return this.translocoService.translate('auth.login.oidcErrors.unknown');
  }

  login(): void {
    this.authService.internalLogin({username: this.username, password: this.password}).subscribe({
      next: (response) => {
        if (response.isDefaultPassword === 'true') {
          this.router.navigate(['/change-password']);
        } else {
          this.router.navigate(['/dashboard']);
        }
      },
      error: (error) => {
        if (error.status === 0) {
          this.errorMessage = this.translocoService.translate('auth.login.connectionError');
        } else if (error.status === 429) {
          this.errorMessage = this.translocoService.translate('auth.login.rateLimited');
        } else {
          this.errorMessage = error?.error?.message || this.translocoService.translate('auth.login.unexpectedError');
        }
      }
    });
  }

  async loginWithOidc(): Promise<void> {
    if (this.isOidcLoginInProgress) {
      return;
    }

    this.isOidcLoginInProgress = true;
    this.errorMessage = '';

    try {
      const publicSettings = this.appSettingsService.publicAppSettings();
      if (!publicSettings?.oidcProviderDetails) {
        this.errorMessage = this.translocoService.translate('auth.login.oidcInitError');
        this.isOidcLoginInProgress = false;
        return;
      }

      const details = publicSettings.oidcProviderDetails;
      const pkce = await this.oidcService.generatePkce();
      const state = await this.oidcService.fetchState();
      const nonce = this.oidcService.generateRandomString();

      this.oidcService.storePkceState({codeVerifier: pkce.codeVerifier, state, nonce});

      const authUrl = await this.oidcService.buildAuthUrl(
        details.issuerUri,
        details.clientId,
        pkce.codeChallenge,
        state,
        nonce,
        undefined,
        details.scopes
      );

      window.location.href = authUrl;
    } catch (error) {
      console.error('OIDC login initiation failed:', error);
      sessionStorage.removeItem('oidc_redirect_count');
      this.errorMessage = this.translocoService.translate('auth.login.oidcErrors.providerUnreachable');
      this.showLocalLogin = !this.oidcOnlyAutoRedirect;
      this.isOidcLoginInProgress = false;
    }
  }

}
