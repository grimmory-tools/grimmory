import { provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';
import { ErrorHandler, inject, isDevMode, provideAppInitializer, provideZonelessChangeDetection } from '@angular/core';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { ConfirmationService, MessageService } from '@openng/optimus-ui/api';
import { RxStompService } from './app/shared/websocket/rx-stomp.service';
import { rxStompServiceFactory } from './app/shared/websocket/rx-stomp-service-factory';
import { provideRouter, RouteReuseStrategy } from '@angular/router';
import { CustomReuseStrategy } from './app/core/custom-reuse-strategy';
import { provideOptimus } from '@openng/optimus-ui/config';
import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import Aura from './app/shared/layout/theme/theme-palette-extend';
import { routes } from './app/app.routes';
import { AuthInterceptorService } from './app/core/security/auth-interceptor.service';
import { AuthService } from './app/shared/service/auth.service';
import { GlobalErrorHandler } from './app/core/errors/global-error-handler';
import { PwaUpdateService } from './app/core/services/pwa-update.service';
import { initializeAuthFactory } from './app/core/security/auth-initializer';
import { StartupService } from './app/shared/service/startup.service';
import { provideServiceWorker } from '@angular/service-worker';
import { provideTransloco } from '@jsverse/transloco';
import { AVAILABLE_LANGS, TranslocoInlineLoader } from './app/core/config/transloco-loader';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { provideLucideConfig } from '@lucide/angular';

document.addEventListener('touchstart', () => undefined, { passive: true });

bootstrapApplication(AppComponent, {
  providers: [
    provideZonelessChangeDetection(),
    provideLucideConfig({ size: '1em', strokeWidth: 2 }),
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
      const initializeAuth = initializeAuthFactory();
      const startup = inject(StartupService);
      return Promise.resolve(initializeAuth()).then(() => startup.load());
    }),
    provideHttpClient(withXhr(), withInterceptors([AuthInterceptorService])),
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
    provideOptimus({
      theme: {
        preset: Aura,
        options: {
          darkModeSelector: '.dark',
          cssLayer: { name: 'optimus', order: 'theme, base, optimus, components, utilities' }
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
