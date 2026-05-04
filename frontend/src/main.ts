import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ErrorHandler, inject, isDevMode, provideAppInitializer, provideZonelessChangeDetection } from '@angular/core';
import { DialogService } from 'primeng/dynamicdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RxStompService } from './app/shared/websocket/rx-stomp.service';
import { rxStompServiceFactory } from './app/shared/websocket/rx-stomp-service-factory';
import { PreloadAllModules, provideRouter, RouteReuseStrategy, withPreloading } from '@angular/router';
import { CustomReuseStrategy } from './app/core/custom-reuse-strategy';
import { providePrimeNG } from 'primeng/config';
import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import Aura from './app/shared/layout/theme-palette-extend';
import { routes } from './app/app.routes';
import { AuthInterceptorService } from './app/core/security/auth-interceptor.service';
import { AuthService, websocketInitializer } from './app/shared/service/auth.service';
import { GlobalErrorHandler } from './app/core/errors/global-error-handler';
import { PwaUpdateService } from './app/core/services/pwa-update.service';
import { initializeAuthFactory } from './app/core/security/auth-initializer';
import { StartupService } from './app/shared/service/startup.service';
import { provideServiceWorker } from '@angular/service-worker';
import { provideTransloco } from '@jsverse/transloco';
import { AVAILABLE_LANGS, TranslocoInlineLoader } from './app/core/config/transloco-loader';
import { initializeLanguage } from './app/core/config/language-initializer';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

bootstrapApplication(AppComponent, {
  providers: [
    provideZonelessChangeDetection(),
    provideTanStackQuery(new QueryClient({
      defaultOptions: {
        queries: {
          staleTime: Infinity,
          retry: 2,
          refetchOnWindowFocus: true,
        },
      },
    })),
    provideAppInitializer(() => {
      const authService = inject(AuthService);
      return websocketInitializer(authService)();
    }),
    provideAppInitializer(() => {
      const startup = inject(StartupService);
      return startup.load();
    }),
    provideHttpClient(withInterceptors([AuthInterceptorService])),
    provideAppInitializer(initializeAuthFactory()),
    provideRouter(routes, withPreloading(PreloadAllModules)),
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
    ...provideTransloco({
      config: {
        availableLangs: AVAILABLE_LANGS,
        defaultLang: 'en',
        fallbackLang: 'en',
        reRenderOnLangChange: true,
        prodMode: !isDevMode(),
      },
      loader: TranslocoInlineLoader,
    }),
    provideAppInitializer(initializeLanguage()),
    providePrimeNG({
      theme: {
        preset: Aura,
        options: {
          darkModeSelector: '.p-dark'
        }
      }
    }), provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000'
    }),
    { provide: ErrorHandler, useClass: GlobalErrorHandler },
    provideAppInitializer(() => {
      inject(PwaUpdateService);
    })
  ]
}).catch(err => console.error(err));
