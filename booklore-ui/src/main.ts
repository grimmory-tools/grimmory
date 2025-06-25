import {provideHttpClient, withInterceptors} from '@angular/common/http';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, MessageService} from 'primeng/api';
import {RxStompService} from './app/shared/websocket/rx-stomp.service';
import {rxStompServiceFactory} from './app/shared/websocket/rx-stomp-service-factory';
import {provideRouter, RouteReuseStrategy} from '@angular/router';
import {CustomReuseStrategy} from './app/custom-reuse-strategy';
import {provideAnimationsAsync} from '@angular/platform-browser/animations/async';
import {providePrimeNG} from 'primeng/config';
import {bootstrapApplication} from '@angular/platform-browser';
import {AppComponent} from './app/app.component';
import Aura from '@primeng/themes/aura';
import {routes} from './app/app.routes';
import {AuthInterceptorService} from './app/auth-interceptor.service';
import {AuthService, websocketInitializer} from './app/core/service/auth.service';
import {provideOAuthClient} from 'angular-oauth2-oidc';
import {APP_INITIALIZER, provideAppInitializer} from '@angular/core';
import {initializeAuthFactory} from './app/auth-initializer';

bootstrapApplication(AppComponent, {
  providers: [
    {
      provide: APP_INITIALIZER,
      useFactory: themeInitializer,
      multi: true
    },
    {
      provide: APP_INITIALIZER,
      useFactory: websocketInitializer,
      deps: [AuthService],
      multi: true
    },
    provideHttpClient(withInterceptors([AuthInterceptorService])),
    provideOAuthClient(),
    provideAppInitializer(initializeAuthFactory()),
    provideRouter(routes),
    DialogService,
    MessageService,
    ConfirmationService,
    {
      provide: RxStompService,
      useFactory: rxStompServiceFactory,
      deps: [AuthService],
    },
    {
      provide: RouteReuseStrategy,
      useClass: CustomReuseStrategy
    },
    provideAnimationsAsync(),
    providePrimeNG({
      theme: {
        preset: Aura,
        options: {
          darkModeSelector: '.p-dark'
        }
      }
    })
  ]
}).catch(err => console.error(err));

export function themeInitializer(): () => void {
  return () => {
    document.documentElement.classList.add('p-dark');
    document.body.classList.add('p-dark');
  };
}
