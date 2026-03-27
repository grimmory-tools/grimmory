import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {AppComponent} from './app.component';
import {AuthInitializationService} from './core/security/auth-initialization-service';
import {of} from 'rxjs';
import {RxStompService} from './shared/websocket/rx-stomp.service';
import {BookService} from './features/book/service/book.service';
import {NotificationEventService} from './shared/websocket/notification-event.service';
import {AppConfigService} from './shared/service/app-config.service';
import {MetadataProgressService} from './shared/service/metadata-progress.service';
import {BookdropFileService} from './features/bookdrop/service/bookdrop-file.service';
import {TaskService} from './features/settings/task-management/task.service';
import {LibraryService} from './features/book/service/library.service';
import {LibraryHealthService} from './features/book/service/library-health.service';
import {LibraryLoadingService} from './features/library-creator/library-loading.service';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {AuthService} from './shared/service/auth.service';

describe('AppComponent offline detection', () => {
  let fixture: ComponentFixture<AppComponent>;
  let component: AppComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        {provide: AuthInitializationService, useValue: {initialized: signal(false)}},
        {provide: RxStompService, useValue: {watch: vi.fn(() => of())}},
        {provide: BookService, useValue: {}},
        {provide: NotificationEventService, useValue: {}},
        {provide: AppConfigService, useValue: {}},
        {provide: MetadataProgressService, useValue: {}},
        {provide: BookdropFileService, useValue: {}},
        {provide: TaskService, useValue: {}},
        {provide: LibraryService, useValue: {largeLibraryLoading: signal({isLoading: false, expectedCount: 0})}},
        {provide: LibraryHealthService, useValue: {initialize: vi.fn()}},
        {provide: LibraryLoadingService, useValue: {hide: vi.fn()}},
        {provide: AuthService, useValue: {token: signal(null)}},
      ]
    });

    fixture = TestBed.createComponent(AppComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should initialize with offline set to false', () => {
    expect(component.offline).toBe(false);
  });

  it('should set offline to false when online event fires', () => {
    component.offline = true;
    window.dispatchEvent(new Event('online'));
    expect(component.offline).toBe(false);
  });

  it('should not show offline when server is reachable despite browser offline event', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, {status: 200}));

    window.dispatchEvent(new Event('offline'));

    await vi.waitFor(() => {
      expect(component.offline).toBe(false);
    });
  });

  it('should show offline when server is unreachable on browser offline event', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));

    window.dispatchEvent(new Event('offline'));

    await vi.waitFor(() => {
      expect(component.offline).toBe(true);
    });
  });

  it('should ping server with HEAD method and no-store cache', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, {status: 200}));

    window.dispatchEvent(new Event('offline'));

    await vi.waitFor(() => {
      expect(fetchSpy).toHaveBeenCalledWith('/api/public/settings', {method: 'HEAD', cache: 'no-store'});
    });
  });

  it('should treat server errors as reachable', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, {status: 500}));

    window.dispatchEvent(new Event('offline'));

    await vi.waitFor(() => {
      expect(component.offline).toBe(false);
    });
  });

  it('should treat network timeout as unreachable', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('network timeout'));

    window.dispatchEvent(new Event('offline'));

    await vi.waitFor(() => {
      expect(component.offline).toBe(true);
    });
  });
});
