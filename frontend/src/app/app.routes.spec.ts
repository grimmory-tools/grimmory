import {describe, expect, it} from 'vitest';

import {routes} from './app.routes';
import {AuthGuard} from './core/security/auth.guard';
import {BookdropGuard} from './core/security/guards/bookdrop.guard';
import {EditMetadataGuard} from './core/security/guards/edit-metdata.guard';
import {LibraryStatsGuard} from './core/security/guards/library-stats.guard';
import {UserStatsGuard} from './core/security/guards/user-stats.guard';
import {LoginGuard} from './shared/components/setup/login.guard';
import {SetupGuard} from './shared/components/setup/setup.guard';
import {SetupRedirectGuard} from './shared/components/setup/setup-redirect.guard';

describe('app routes', () => {
  it('defines the setup and auth entry routes', () => {
    const rootRedirect = routes.find(route => route.path === '' && route.pathMatch === 'full');
    const setupRoute = routes.find(route => route.path === 'setup');
    const loginRoute = routes.find(route => route.path === 'login');
    const wildcardRoute = routes.find(route => route.path === '**');

    expect(rootRedirect?.canActivate).toEqual([SetupRedirectGuard]);
    expect(setupRoute?.canActivate).toEqual([SetupGuard]);
    expect(loginRoute?.canActivate).toEqual([LoginGuard]);
    expect(wildcardRoute?.redirectTo).toBe('login');
  });

  it('defines the authenticated shell routes behind the auth guard', () => {
    const shellRoute = routes.find(route => route.path === '' && Array.isArray(route.children));
    const children = shellRoute?.children ?? [];

    expect(children).toHaveLength(17);
    expect(children.find(route => route.path === 'dashboard')?.canActivate).toEqual([AuthGuard]);
    expect(children.find(route => route.path === 'all-books')?.canActivate).toEqual([AuthGuard]);
    expect(children.find(route => route.path === 'magic-shelf/:magicShelfId/books')?.canActivate).toEqual([AuthGuard]);
    expect(children.find(route => route.path === 'notebook')?.canActivate).toEqual([AuthGuard]);
    expect(typeof children.find(route => route.path === 'all-books')?.loadComponent).toBe('function');
    expect(typeof children.find(route => route.path === 'library/:libraryId/books')?.loadComponent).toBe('function');
    expect(typeof children.find(route => route.path === 'shelf/:shelfId/books')?.loadComponent).toBe('function');
    expect(typeof children.find(route => route.path === 'unshelved-books')?.loadComponent).toBe('function');
    expect(typeof children.find(route => route.path === 'magic-shelf/:magicShelfId/books')?.loadComponent).toBe('function');
  });

  it('defines guarded lazy routes for metadata, stats, and bookdrop', () => {
    const shellRoute = routes.find(route => route.path === '' && Array.isArray(route.children));
    const children = shellRoute?.children ?? [];

    expect(children.find(route => route.path === 'bookdrop')?.canActivate).toEqual([BookdropGuard]);
    expect(children.find(route => route.path === 'metadata-manager')?.canActivate).toEqual([EditMetadataGuard]);
    expect(children.find(route => route.path === 'library-stats')?.canActivate).toEqual([LibraryStatsGuard]);
    expect(children.find(route => route.path === 'reading-stats')?.canActivate).toEqual([UserStatsGuard]);

    expect(typeof children.find(route => route.path === 'settings')?.loadComponent).toBe('function');
    expect(typeof children.find(route => route.path === 'series')?.loadComponent).toBe('function');
    expect(typeof children.find(route => route.path === 'book/:bookId')?.loadComponent).toBe('function');
  });

  it('defines the dedicated reader routes outside the shell', () => {
    expect(routes.find(route => route.path === 'pdf-reader/book/:bookId')?.canActivate).toEqual([AuthGuard]);
    expect(routes.find(route => route.path === 'ebook-reader/book/:bookId')?.canActivate).toEqual([AuthGuard]);
    expect(routes.find(route => route.path === 'cbx-reader/book/:bookId')?.canActivate).toEqual([AuthGuard]);
    expect(routes.find(route => route.path === 'audiobook-player/book/:bookId')?.canActivate).toEqual([AuthGuard]);
  });

  it('keeps the callback and password routes outside the authenticated shell', () => {
    expect(routes.find(route => route.path === 'oauth2-callback')?.component).toBeDefined();
    expect(routes.find(route => route.path === 'change-password')?.component).toBeDefined();
    expect(routes.find(route => route.path === 'change-password')?.canActivate).toBeUndefined();
  });
});
