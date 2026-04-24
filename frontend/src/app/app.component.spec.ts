import { signal } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { TranslocoTestingModule } from "@jsverse/transloco";
import { Subject } from "rxjs";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { AppComponent } from "./app.component";
import { AuthInitializationService } from "./core/security/auth-initialization-service";
import { RxStompService } from "./shared/websocket/rx-stomp.service";
import { BookService } from "./features/book/service/book.service";
import { NotificationEventService } from "./shared/websocket/notification-event.service";
import { AppConfigService } from "./shared/service/app-config.service";
import { MetadataProgressService } from "./shared/service/metadata-progress.service";
import { BookdropFileService } from "./features/bookdrop/service/bookdrop-file.service";
import { TaskService } from "./features/settings/task-management/task.service";
import { LibraryService } from "./features/book/service/library.service";
import { LibraryHealthService } from "./features/book/service/library-health.service";
import { LibraryLoadingService } from "./features/library-creator/library-loading.service";
import { AuthService } from "./shared/service/auth.service";
import { ConfirmationService } from "primeng/api";
import { MessageService } from "primeng/api";
import { CommandPaletteService } from "./features/command-palette/command-palette.service";

interface StompMessage {
  body: string;
}

describe("AppComponent", () => {
  let fixture: ComponentFixture<AppComponent>;
  let component: AppComponent;
  let topics: Map<string, Subject<StompMessage>>;
  let rxStompService: { watch: ReturnType<typeof vi.fn> };
  let bookService: {
    handleNewlyCreatedBook: ReturnType<typeof vi.fn>;
    handleBookUpdate: ReturnType<typeof vi.fn>;
    handleMultipleBookCoverPatches: ReturnType<typeof vi.fn>;
    handleRemovedBookIds: ReturnType<typeof vi.fn>;
    handleMultipleBookUpdates: ReturnType<typeof vi.fn>;
  };
  let notificationEventService: {
    handleNewNotification: ReturnType<typeof vi.fn>;
  };
  let metadataProgressService: {
    handleIncomingProgress: ReturnType<typeof vi.fn>;
  };
  let bookdropFileService: { handleIncomingFile: ReturnType<typeof vi.fn> };
  let taskService: { handleTaskProgress: ReturnType<typeof vi.fn> };
  let libraryHealthService: { initWebsocket: ReturnType<typeof vi.fn>, fetchHealth: ReturnType<typeof vi.fn> };
  let libraryLoadingService: {
    showBookLoadingProgress: ReturnType<typeof vi.fn>;
    hide: ReturnType<typeof vi.fn>;
  };
  let authService: { forceLogout: ReturnType<typeof vi.fn>, isAuthenticated: ReturnType<typeof signal> };
  let libraryService: {
    largeLibraryLoading: ReturnType<typeof signal>;
    setLargeLibraryLoading: ReturnType<typeof vi.fn>;
  };
  let commandPaletteService: {
    toggle: ReturnType<typeof vi.fn>;
    open: ReturnType<typeof vi.fn>;
    hide: ReturnType<typeof vi.fn>;
    registerOverlayController: ReturnType<typeof vi.fn>;
    isOpen: ReturnType<typeof signal>;
    query: ReturnType<typeof signal>;
    visibleItems: ReturnType<typeof signal>;
    groups: ReturnType<typeof signal>;
  };

  function createTopicStream(topic: string): Subject<StompMessage> {
    const stream = new Subject<StompMessage>();
    topics.set(topic, stream);
    return stream;
  }

  function configureComponent(
    auth = { ready: true, authenticated: true },
    largeLibraryLoading = { isLoading: false, expectedCount: 0 },
  ): void {
    topics = new Map();
    rxStompService = {
      watch: vi.fn((topic: string) =>
        (topics.get(topic) ?? createTopicStream(topic)).asObservable(),
      ),
    };
    bookService = {
      handleNewlyCreatedBook: vi.fn(),
      handleBookUpdate: vi.fn(),
      handleMultipleBookCoverPatches: vi.fn(),
      handleRemovedBookIds: vi.fn(),
      handleMultipleBookUpdates: vi.fn(),
    };
    notificationEventService = { handleNewNotification: vi.fn() };
    metadataProgressService = { handleIncomingProgress: vi.fn() };
    bookdropFileService = { handleIncomingFile: vi.fn() };
    taskService = { handleTaskProgress: vi.fn() };
    libraryHealthService = { initWebsocket: vi.fn(), fetchHealth: vi.fn() };
    libraryLoadingService = {
      showBookLoadingProgress: vi.fn(),
      hide: vi.fn(),
    };
    authService = { forceLogout: vi.fn(), isAuthenticated: signal(auth.authenticated) };
    libraryService = {
      largeLibraryLoading: signal(largeLibraryLoading),
      setLargeLibraryLoading: vi.fn(),
    };
    commandPaletteService = {
      toggle: vi.fn(),
      open: vi.fn(),
      hide: vi.fn(),
      registerOverlayController: vi.fn(() => vi.fn()),
      isOpen: signal(false),
      query: signal(''),
      visibleItems: signal([]),
      groups: signal([]),
    };

    TestBed.configureTestingModule({
      imports: [TranslocoTestingModule.forRoot({ langs: {} })],
      providers: [
        {
          provide: AuthInitializationService,
          useValue: { initialized: signal(auth.ready) },
        },
        { provide: RxStompService, useValue: rxStompService },
        { provide: BookService, useValue: bookService },
        {
          provide: NotificationEventService,
          useValue: notificationEventService,
        },
        { provide: AppConfigService, useValue: {} },
        { provide: MetadataProgressService, useValue: metadataProgressService },
        { provide: BookdropFileService, useValue: bookdropFileService },
        { provide: TaskService, useValue: taskService },
        { provide: LibraryService, useValue: libraryService },
        { provide: LibraryHealthService, useValue: libraryHealthService },
        { provide: LibraryLoadingService, useValue: libraryLoadingService },
        { provide: AuthService, useValue: authService },
        { provide: CommandPaletteService, useValue: commandPaletteService },
        ConfirmationService,
        MessageService],
    });

    fixture = TestBed.createComponent(AppComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it("boots the websocket wiring when auth initialization is ready", () => {
    configureComponent();

    expect(component.loading()).toBe(false);
    expect(libraryHealthService.initWebsocket).toHaveBeenCalledOnce();
    expect(libraryHealthService.fetchHealth).toHaveBeenCalledOnce();
    expect(rxStompService.watch).toHaveBeenCalledWith("/user/queue/book-add");
    expect(rxStompService.watch).toHaveBeenCalledWith(
      "/user/queue/session-revoked",
    );
  });

  it("boots the websocket wiring but don't fetch library health when not authenticated", () => {
    configureComponent({ ready: true, authenticated: false });

    expect(component.loading()).toBe(false);
    expect(libraryHealthService.initWebsocket).toHaveBeenCalledOnce();
    expect(libraryHealthService.fetchHealth).not.toHaveBeenCalled();
  });

  it("routes websocket book notifications through the large library loading branch", () => {
    configureComponent({ ready: true, authenticated: true }, { isLoading: true, expectedCount: 2 });

    topics
      .get("/user/queue/book-add")
      ?.next({ body: JSON.stringify({ metadata: { title: "First" } }) });
    topics
      .get("/user/queue/book-add")
      ?.next({ body: JSON.stringify({ metadata: { title: "Second" } }) });

    expect(bookService.handleNewlyCreatedBook).toHaveBeenCalledTimes(2);
    expect(
      libraryLoadingService.showBookLoadingProgress,
    ).toHaveBeenNthCalledWith(1, "First", 1, 2);
    expect(
      libraryLoadingService.showBookLoadingProgress,
    ).toHaveBeenNthCalledWith(2, "Second", 2, 2);
    expect(libraryService.setLargeLibraryLoading).toHaveBeenCalledWith(
      false,
      0,
    );
  });

  it("forwards websocket updates to the matching root services", () => {
    configureComponent();

    topics
      .get("/user/queue/book-update")
      ?.next({ body: JSON.stringify({ id: 1 }) });
    topics
      .get("/user/queue/books-cover-update")
      ?.next({ body: JSON.stringify([{ id: 1 }]) });
    topics
      .get("/user/queue/books-remove")
      ?.next({ body: JSON.stringify([1, 2]) });
    topics
      .get("/user/queue/book-metadata-update")
      ?.next({ body: JSON.stringify({ id: 2 }) });
    topics
      .get("/user/queue/book-metadata-batch-update")
      ?.next({ body: JSON.stringify([{ id: 3 }]) });
    topics
      .get("/user/queue/book-metadata-batch-progress")
      ?.next({ body: JSON.stringify({ taskId: "task-1" }) });
    topics
      .get("/user/queue/log")
      ?.next({ body: JSON.stringify({ message: "info", severity: "INFO" }) });
    topics
      .get("/user/queue/bookdrop-file")
      ?.next({ body: JSON.stringify({ pendingCount: 1, totalCount: 2 }) });
    topics
      .get("/user/queue/task-progress")
      ?.next({ body: JSON.stringify({ taskId: "task-2" }) });

    expect(bookService.handleBookUpdate).toHaveBeenCalledWith({ id: 1 });
    expect(bookService.handleMultipleBookCoverPatches).toHaveBeenCalledWith([
      { id: 1 }]);
    expect(bookService.handleRemovedBookIds).toHaveBeenCalledWith([1, 2]);
    expect(bookService.handleMultipleBookUpdates).toHaveBeenCalledWith([
      { id: 3 }]);
    expect(metadataProgressService.handleIncomingProgress).toHaveBeenCalledWith(
      { taskId: "task-1" },
    );
    expect(notificationEventService.handleNewNotification).toHaveBeenCalledWith(
      {
        message: "info",
        severity: "INFO",
      },
    );
    expect(bookdropFileService.handleIncomingFile).toHaveBeenCalledWith({
      pendingCount: 1,
      totalCount: 2,
    });
    expect(taskService.handleTaskProgress).toHaveBeenCalledWith({
      taskId: "task-2",
    });
  });

  it("forces logout when the session is revoked", () => {
    configureComponent();

    topics.get("/user/queue/session-revoked")?.next({ body: "" });

    expect(authService.forceLogout).toHaveBeenCalledWith("session_revoked");
  });

  it("unsubscribes and hides the loading overlay when destroyed", () => {
    configureComponent();

    component.ngOnDestroy();

    expect(libraryLoadingService.hide).toHaveBeenCalledOnce();
    topics
      .get("/user/queue/book-update")
      ?.next({ body: JSON.stringify({ id: 99 }) });
    expect(bookService.handleBookUpdate).not.toHaveBeenCalled();
  });
});
