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
import {OidcCallbackComponent} from './core/security/oidc-callback/oidc-callback.component';
import {CbxReaderComponent} from './features/readers/cbx-reader/cbx-reader.component';
import {MainDashboardComponent} from './features/dashboard/components/main-dashboard/main-dashboard.component';
import {SeriesPageComponent} from './features/book/components/series-page/series-page.component';
import {MetadataManagerComponent} from './features/metadata/component/metadata-manager/metadata-manager.component';
import {PdfReaderComponent} from './features/readers/pdf-reader/pdf-reader.component';
import {BookdropFileReviewComponent} from './features/bookdrop/component/bookdrop-file-review/bookdrop-file-review.component';
import {LoginGuard} from './shared/components/setup/login.guard';
import {UserStatsComponent} from './features/stats/component/user-stats/user-stats.component';
import {BookdropGuard} from './core/security/guards/bookdrop.guard';
import {LibraryStatsGuard} from './core/security/guards/library-stats.guard';
import {UserStatsGuard} from './core/security/guards/user-stats.guard';
import {EditMetadataGuard} from './core/security/guards/edit-metdata.guard';
import {EbookReaderComponent} from './features/readers/ebook-reader';
import {LibraryStatsComponent} from './features/stats/component/library-stats/library-stats.component';

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
      {path: 'bookdrop', component: BookdropFileReviewComponent, canActivate: [BookdropGuard]},
      {path: 'metadata-manager', component: MetadataManagerComponent, canActivate: [EditMetadataGuard]},
      {path: 'library-stats', component: LibraryStatsComponent, canActivate: [LibraryStatsGuard]},
      {path: 'reading-stats', component: UserStatsComponent, canActivate: [UserStatsGuard]},
    ]
  },
  {
    path: 'pdf-reader/book/:bookId',
    component: PdfReaderComponent,
    canActivate: [AuthGuard]
  },
  {
    path: 'ebook-reader/book/:bookId',
    component: EbookReaderComponent,
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
