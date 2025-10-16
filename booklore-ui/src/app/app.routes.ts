import {Routes} from '@angular/router';
import {BookBrowserComponent} from './features/book/components/book-browser/book-browser.component';
import {AppLayoutComponent} from './shared/layout/component/layout-main/app.layout.component';
import {LoginComponent} from './shared/components/login/login.component';
import {AuthGuard} from './core/security/auth.guard';
import {SettingsComponent} from './features/settings/settings.component';
import {ChangePasswordComponent} from './shared/components/change-password/change-password.component';
import {BookMetadataCenterComponent} from './features/metadata/component/book-metadata-center/book-metadata-center.component';
import {SetupComponent} from './shared/components/setup/setup.component';
import {SetupGuard} from './shared/components/setup/setup.guard';
import {SetupRedirectGuard} from './shared/components/setup/setup-redirect.guard';
import {EmptyComponent} from './shared/components/empty/empty.component';
import {LoginGuard} from './shared/components/setup/ login.guard';
import {OidcCallbackComponent} from './core/security/oidc-callback/oidc-callback.component';
import {CbxReaderComponent} from './features/readers/cbx-reader/cbx-reader.component';
import {MainDashboardComponent} from './features/dashboard/components/main-dashboard/main-dashboard.component';
import {SeriesPageComponent} from './features/book/components/series-page/series-page.component';
import {MetadataManagerComponent} from './features/metadata/component/metadata-manager/metadata-manager.component';
import {StatsComponent} from './features/stats/component/stats-component';
import {EpubReaderComponent} from './features/readers/epub-reader/component/epub-reader.component';
import {PdfReaderComponent} from './features/readers/pdf-reader/pdf-reader.component';
import {BookdropFileReviewComponent} from './features/bookdrop/component/bookdrop-file-review/bookdrop-file-review.component';
import {ManageLibraryGuard} from './core/security/guards/manage-library.guard';

export const routes: Routes = [
  {
    path: '',
    canActivate: [SetupRedirectGuard],
    pathMatch: 'full',
    component: EmptyComponent
  },
  {
    path: 'setup',
    component: SetupComponent,
    canActivate: [SetupGuard]
  },
  {path: 'oauth2-callback', component: OidcCallbackComponent},
  {
    path: '',
    component: AppLayoutComponent,
    children: [
      {path: 'dashboard', component: MainDashboardComponent, canActivate: [AuthGuard]},
      {path: 'all-books', component: BookBrowserComponent, canActivate: [AuthGuard]},
      {path: 'settings', component: SettingsComponent, canActivate: [AuthGuard]},
      {path: 'library/:libraryId/books', component: BookBrowserComponent, canActivate: [AuthGuard]},
      {path: 'shelf/:shelfId/books', component: BookBrowserComponent, canActivate: [AuthGuard]},
      {path: 'unshelved-books', component: BookBrowserComponent, canActivate: [AuthGuard]},
      {path: 'series/:seriesName', component: SeriesPageComponent, canActivate: [AuthGuard]},
      {path: 'magic-shelf/:magicShelfId/books', component: BookBrowserComponent, canActivate: [AuthGuard]},
      {path: 'book/:bookId', component: BookMetadataCenterComponent, canActivate: [AuthGuard]},
      {path: 'bookdrop', component: BookdropFileReviewComponent, canActivate: [ManageLibraryGuard]},
      {path: 'metadata-manager', component: MetadataManagerComponent, canActivate: [ManageLibraryGuard]},
      {path: 'stats', component: StatsComponent, canActivate: [AuthGuard]},
    ]
  },
  {
    path: 'pdf-reader/book/:bookId',
    component: PdfReaderComponent,
    canActivate: [AuthGuard]
  },
  {
    path: 'epub-reader/book/:bookId',
    component: EpubReaderComponent,
    canActivate: [AuthGuard]
  },
  {
    path: 'cbx-reader/book/:bookId',
    component: CbxReaderComponent,
    canActivate: [AuthGuard]
  },
  {
    path: 'login',
    component: LoginComponent,
    canActivate: [LoginGuard]
  },
  {
    path: 'change-password',
    component: ChangePasswordComponent
  },
  {
    path: '**',
    redirectTo: 'login',
    pathMatch: 'full'
  }
];
