import {Component, inject, OnInit} from '@angular/core';
import {AuthService} from '../../service/auth.service';
import {Router} from '@angular/router';
import {FormsModule} from '@angular/forms';
import {NgIf} from '@angular/common';
import {Card} from 'primeng/card';
import {Password} from 'primeng/password';
import {Button} from 'primeng/button';
import {Message} from 'primeng/message';
import {PrimeTemplate} from 'primeng/api';
import {InputText} from 'primeng/inputtext';
import {OAuthService} from 'angular-oauth2-oidc';
import {Divider} from 'primeng/divider';
import {AppSettingsService} from '../../service/app-settings.service';
import {AppSettings} from '../../model/app-settings.model';
import {Observable} from 'rxjs';
import {filter, take} from 'rxjs/operators';

@Component({
  selector: 'app-login',
  imports: [
    FormsModule,
    NgIf,
    Card,
    Password,
    Button,
    Message,
    PrimeTemplate,
    InputText,
    Divider
  ],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit {
  username = '';
  password = '';
  errorMessage = '';
  oidcEnabled = false;

  private authService = inject(AuthService);
  private oAuthService = inject(OAuthService);
  private appSettingsService = inject(AppSettingsService);
  private router = inject(Router);

  appSettings$: Observable<AppSettings | null> = this.appSettingsService.appSettings$;

  ngOnInit(): void {
    this.appSettings$
      .pipe(
        filter(settings => settings != null),
        take(1)
      )
      .subscribe(settings => {
        this.oidcEnabled = settings?.oidcEnabled;
      });
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
          this.errorMessage = 'Cannot connect to the server. Please check your connection and try again.';
        } else {
          this.errorMessage = error?.error?.message || 'An unexpected error occurred. Please try again.';
        }
      }
    });
  }

  loginWithOidc(): void {
    this.oAuthService.initImplicitFlow();
  }
}
