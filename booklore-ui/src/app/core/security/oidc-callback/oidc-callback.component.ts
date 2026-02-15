import {Component, inject, OnInit} from '@angular/core';
import {Router} from '@angular/router';
import {OAuthService} from 'angular-oauth2-oidc';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-oidc-callback',
  templateUrl: './oidc-callback.component.html',
  styleUrls: ['./oidc-callback.component.scss']
})
export class OidcCallbackComponent implements OnInit {
  private router = inject(Router);
  private oauthService = inject(OAuthService);
  private messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);

  async ngOnInit(): Promise<void> {
    try {
      await this.oauthService.tryLoginCodeFlow();
      if (this.oauthService.hasValidAccessToken()) {
        this.router.navigate(['/dashboard']);
      } else {
        this.router.navigate(['/login']);
      }
    } catch (e) {
      console.error('[OIDC Callback] Login failed', e);
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('auth.oidc.loginFailedSummary'),
        detail: this.t.translate('auth.oidc.redirectingDetail'),
        life: 3000
      });
      setTimeout(() => {
        this.router.navigate(['/login']);
      }, 3000);
    }
  }
}
