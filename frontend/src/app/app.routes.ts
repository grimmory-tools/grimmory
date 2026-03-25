import {Routes} from '@angular/router';
import {BookBrowserComponent} from './features/book/components/book-browser/book-browser.component';
import {AppLayoutComponent} from './shared/layout/component/layout-main/app.layout.component';
import {LoginComponent} from './shared/components/login/login.component';
import {AuthGuard} from './core/security/auth.guard';
import {ChangePasswordComponent} from './shared/components/change-password/change-password.component';
import {SetupComponent} from './shared/components/setup/setup.component';
import {SetupGuard} from './shared/components/setup/setup.guard';
import {SetupRedirectGuard} from './shared/components/setup/setup-redirect.guard';
import {EmptyComponent} from './shared/components/empty/empty.component';
import {OidcCallbackComponent} from './core/security/oidc-callback/oidc-callback.component';
import {MainDashboardComponent} from './features/dashboard/components/main-dashboard/main-dashboard.component';
import {LoginGuard} from './shared/components/setup/login.guard';
import {BookdropGuard} from './core/security/guards/bookdrop.guard';
import {LibraryStatsGuard} from './core/security/guards/library-stats.guard';
import {UserStatsGuard} from './core/security/guards/user-stats.guard';
import {EditMetadataGuard} from './core/security/guards/edit-metdata.guard';

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
      {path: 'settings', loadComponent: () => import('./features/settings/settings.component').then(m => m.SettingsComponent), canActivate: [AuthGuard]},
      {path: 'library/:libraryId/books', component: BookBrowserComponent, canActivate: [AuthGuard]},
      {path: 'shelf/:shelfId/books', component: BookBrowserComponent, canActivate: [AuthGuard]},
      {path: 'unshelved-books', component: BookBrowserComponent, canActivate: [AuthGuard]},
      {path: 'series', loadComponent: () => import('./features/series-browser/components/series-browser/series-browser.component').then(m => m.SeriesBrowserComponent), canActivate: [AuthGuard]},
      {path: 'series/:seriesName', loadComponent: () => import('./features/book/components/series-page/series-page.component').then(m => m.SeriesPageComponent), canActivate: [AuthGuard]},
      {path: 'authors', loadComponent: () => import('./features/author-browser/components/author-browser/author-browser.component').then(m => m.AuthorBrowserComponent), canActivate: [AuthGuard]},
      {path: 'author/:authorId', loadComponent: () => import('./features/author-browser/components/author-detail/author-detail.component').then(m => m.AuthorDetailComponent), canActivate: [AuthGuard]},
      {path: 'magic-shelf/:magicShelfId/books', component: BookBrowserComponent, canActivate: [AuthGuard]},
      {path: 'book/:bookId', loadComponent: () => import('./features/metadata/component/book-metadata-center/book-metadata-center.component').then(m => m.BookMetadataCenterComponent), canActivate: [AuthGuard]},
      {path: 'bookdrop', loadComponent: () => import('./features/bookdrop/component/bookdrop-file-review/bookdrop-file-review.component').then(m => m.BookdropFileReviewComponent), canActivate: [BookdropGuard]},
      {path: 'metadata-manager', loadComponent: () => import('./features/metadata/component/metadata-manager/metadata-manager.component').then(m => m.MetadataManagerComponent), canActivate: [EditMetadataGuard]},
      {path: 'library-stats', loadComponent: () => import('./features/stats/component/library-stats/library-stats.component').then(m => m.LibraryStatsComponent), canActivate: [LibraryStatsGuard]},
      {path: 'reading-stats', loadComponent: () => import('./features/stats/component/user-stats/user-stats.component').then(m => m.UserStatsComponent), canActivate: [UserStatsGuard]},
      {path: 'notebook', loadComponent: () => import('./features/notebook/components/notebook/notebook.component').then(m => m.NotebookComponent), canActivate: [AuthGuard]},
    ]
  },
  {
    path: 'pdf-reader/book/:bookId',
    loadComponent: () => import('./features/readers/pdf-reader/pdf-reader.component').then(m => m.PdfReaderComponent),
    canActivate: [AuthGuard]
  },
  {
    path: 'ebook-reader/book/:bookId',
    loadComponent: () => import('./features/readers/ebook-reader/ebook-reader.component').then(m => m.EbookReaderComponent),
    canActivate: [AuthGuard]
  },
  {
    path: 'cbx-reader/book/:bookId',
    loadComponent: () => import('./features/readers/cbx-reader/cbx-reader.component').then(m => m.CbxReaderComponent),
    canActivate: [AuthGuard]
  },
  {
    path: 'audiobook-player/book/:bookId',
    loadComponent: () => import('./features/readers/audiobook-player/audiobook-player.component').then(m => m.AudiobookPlayerComponent),
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
