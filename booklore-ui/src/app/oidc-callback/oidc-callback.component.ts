import {Component, inject, OnInit} from '@angular/core';
import {Router} from '@angular/router';
import {OAuthService} from 'angular-oauth2-oidc';
import {AuthService} from '../core/service/auth.service';

@Component({
  selector: 'app-oidc-callback',
  templateUrl: './oidc-callback.component.html',
  styleUrls: ['./oidc-callback.component.scss']
})
export class OidcCallbackComponent implements OnInit {

  private router = inject(Router);
  private oAuthService = inject(OAuthService);
  private authService = inject(AuthService);

  ngOnInit(): void {
    this.oAuthService.loadDiscoveryDocumentAndTryLogin().then(() => {
      if (this.oAuthService.hasValidAccessToken()) {
        this.authService.saveOidcTokens(
          this.oAuthService.getAccessToken(),
          this.oAuthService.getRefreshToken()
        );
        this.authService.getRxStompService().activate();
        this.router.navigate(['/dashboard']);
      } else {
        console.error('Login failed or no valid tokens');
        this.router.navigate(['/login']);
      }
    }).catch(err => {
      console.error('OIDC error during login:', err);
      this.router.navigate(['/login']);
    });
  }
}
