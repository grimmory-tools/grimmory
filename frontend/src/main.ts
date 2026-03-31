import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { DialogService } from 'primeng/dynamicdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RxStompService } from './app/shared/websocket/rx-stomp.service';
import { rxStompServiceFactory } from './app/shared/websocket/rx-stomp-service-factory';
import { provideRouter, RouteReuseStrategy } from '@angular/router';
import { CustomReuseStrategy } from './app/core/custom-reuse-strategy';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { providePrimeNG } from 'primeng/config';
import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import Aura from './app/shared/layout/theme-palette-extend';
import { routes } from './app/app.routes';
import { AuthInterceptorService } from './app/core/security/auth-interceptor.service';
import { AuthService, websocketInitializer } from './app/shared/service/auth.service';
import { inject, isDevMode, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { initializeAuthFactory } from './app/core/security/auth-initializer';
import { StartupService } from './app/shared/service/startup.service';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';
import ChartDataLabels from 'chartjs-plugin-datalabels';
import { provideServiceWorker } from '@angular/service-worker';
import { provideTransloco } from '@jsverse/transloco';
import { AVAILABLE_LANGS, TranslocoInlineLoader } from './app/core/config/transloco-loader';
import { initializeLanguage } from './app/core/config/language-initializer';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

bootstrapApplication(AppComponent, {
  providers: [
    provideZoneChangeDetection(),
    provideCharts(withDefaultRegisterables(), ChartDataLabels),
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
    provideAppInitializer(initializeLanguage()),
    provideAnimationsAsync(),
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
    })
  ]
}).catch(err => console.error(err));
