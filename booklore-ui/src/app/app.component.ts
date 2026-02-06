import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {RxStompService} from './shared/websocket/rx-stomp.service';
import {BookService} from './features/book/service/book.service';
import {NotificationEventService} from './shared/websocket/notification-event.service';
import {parseLogNotification} from './shared/websocket/model/log-notification.model';
import {ConfirmDialog} from 'primeng/confirmdialog';
import {Toast} from 'primeng/toast';
import {RouterOutlet} from '@angular/router';
import {AuthInitializationService} from './core/security/auth-initialization-service';
import {AppConfigService} from './shared/service/app-config.service';
import {MetadataBatchProgressNotification} from './shared/model/metadata-batch-progress.model';
import {MetadataProgressService} from './shared/service/metadata-progress.service';
import {BookdropFileNotification, BookdropFileService} from './features/bookdrop/service/bookdrop-file.service';
import {Subscription} from 'rxjs';
import {TaskProgressPayload, TaskService} from './features/settings/task-management/task.service';
import {LibraryService} from './features/book/service/library.service';
import {LibraryLoadingService} from './features/library-creator/library-loading.service';
import {scan, withLatestFrom} from 'rxjs/operators';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  standalone: true,
  imports: [ConfirmDialog, Toast, RouterOutlet]
})
export class AppComponent implements OnInit, OnDestroy {

  loading = true;
  offline = !navigator.onLine;
  private subscriptions: Subscription[] = [];
  private subscriptionsInitialized = false;

  private appConfigService = inject(AppConfigService); // DO NOT REMOVE: Used to initialize app config on startup
  private authInit = inject(AuthInitializationService);
  private bookService = inject(BookService);
  private rxStompService = inject(RxStompService);
  private notificationEventService = inject(NotificationEventService);
  private metadataProgressService = inject(MetadataProgressService);
  private bookdropFileService = inject(BookdropFileService);
  private taskService = inject(TaskService);
  private libraryService = inject(LibraryService);
  private libraryLoadingService = inject(LibraryLoadingService);

  ngOnInit(): void {
    window.addEventListener('online', this.onOnline);
    window.addEventListener('offline', this.onOffline);

    this.authInit.initialized$.subscribe(ready => {
      this.loading = !ready;
      if (ready && !this.subscriptionsInitialized) {
        this.setupWebSocketSubscriptions();
        this.subscriptionsInitialized = true;
      }
    });
  }

  private onOnline = () => {
    this.offline = false;
  };

  private onOffline = () => {
    this.offline = true;
  };

  reload(): void {
    window.location.reload();
  }

  private setupWebSocketSubscriptions(): void {
    this.subscriptions.push(
      this.rxStompService.watch('/user/queue/book-add').pipe(
        withLatestFrom(this.libraryService.largeLibraryLoading$),
        scan((acc, [msg, loadingState]) => {
          const book = JSON.parse(msg.body);
          if (loadingState.isLoading) {
            const newCount = acc.count + 1;
            this.libraryLoadingService.showBookLoadingProgress(book.metadata?.title || 'Unknown Book', newCount, loadingState.expectedCount);
            this.bookService.handleNewlyCreatedBook(book);
            if (newCount >= loadingState.expectedCount) {
              this.libraryService.setLargeLibraryLoading(false, 0);
              return {count: 0};
            }
            return {count: newCount};
          } else {
            this.bookService.handleNewlyCreatedBook(book);
            return {count: 0};
          }
        }, {count: 0})
      ).subscribe()
    );
    this.subscriptions.push(
      this.rxStompService.watch('/user/queue/book-update').subscribe(msg =>
        this.bookService.handleBookUpdate(JSON.parse(msg.body))
      )
    );
    this.subscriptions.push(
      this.rxStompService.watch('/user/queue/books-cover-update').subscribe(msg =>
        this.bookService.handleMultipleBookCoverPatches(JSON.parse(msg.body))
      )
    );
    this.subscriptions.push(
      this.rxStompService.watch('/user/queue/books-remove').subscribe(msg =>
        this.bookService.handleRemovedBookIds(JSON.parse(msg.body))
      )
    );
    this.subscriptions.push(
      this.rxStompService.watch('/user/queue/book-metadata-update').subscribe(msg =>
        this.bookService.handleBookUpdate(JSON.parse(msg.body))
      )
    );
    this.subscriptions.push(
      this.rxStompService.watch('/user/queue/book-metadata-batch-update').subscribe(msg =>
        this.bookService.handleMultipleBookUpdates(JSON.parse(msg.body))
      )
    );
    this.subscriptions.push(
      this.rxStompService.watch('/user/queue/book-metadata-batch-progress').subscribe(msg =>
        this.metadataProgressService.handleIncomingProgress(JSON.parse(msg.body) as MetadataBatchProgressNotification)
      )
    );
    this.subscriptions.push(
      this.rxStompService.watch('/user/queue/log').subscribe(msg => {
        const logNotification = parseLogNotification(msg.body);
        this.notificationEventService.handleNewNotification(logNotification);
      })
    );
    this.subscriptions.push(
      this.rxStompService.watch('/user/queue/bookdrop-file').subscribe(msg => {
        const notification = JSON.parse(msg.body) as BookdropFileNotification;
        this.bookdropFileService.handleIncomingFile(notification);
      })
    );
    this.subscriptions.push(
      this.rxStompService.watch('/user/queue/task-progress').subscribe(msg => {
        const progress = JSON.parse(msg.body) as TaskProgressPayload;
        this.taskService.handleTaskProgress(progress);
      })
    );
  }

  ngOnDestroy(): void {
    window.removeEventListener('online', this.onOnline);
    window.removeEventListener('offline', this.onOffline);
    this.subscriptions.forEach(sub => sub.unsubscribe());
    this.libraryLoadingService.hide();
  }
}
