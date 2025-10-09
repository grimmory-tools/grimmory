import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {RxStompService} from './shared/websocket/rx-stomp.service';
import {BookService} from './book/service/book.service';
import {NotificationEventService} from './shared/websocket/notification-event.service';
import {parseLogNotification} from './shared/websocket/model/log-notification.model';
import {ConfirmDialog} from 'primeng/confirmdialog';
import {Toast} from 'primeng/toast';
import {RouterOutlet} from '@angular/router';
import {AuthInitializationService} from './auth-initialization-service';
import {AppConfigService} from './core/service/app-config.service';
import {MetadataBatchProgressNotification} from './core/model/metadata-batch-progress.model';
import {MetadataProgressService} from './core/service/metadata-progress-service';
import {BookdropFileNotification, BookdropFileService} from './bookdrop/bookdrop-file.service';
import {DuplicateFileNotification} from './shared/websocket/model/duplicate-file-notification.model';
import {DuplicateFileService} from './shared/websocket/duplicate-file.service';
import {Subscription} from 'rxjs';
import {DownloadProgressDialogComponent} from './shared/components/download-progress-dialog/download-progress-dialog.component';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  standalone: true,
  imports: [ConfirmDialog, Toast, RouterOutlet, DownloadProgressDialogComponent]
})
export class AppComponent implements OnInit, OnDestroy {

  loading = true;
  private subscriptions: Subscription[] = [];
  private subscriptionsInitialized = false; // Prevent multiple subscription setups
  private authInit = inject(AuthInitializationService);
  private bookService = inject(BookService);
  private rxStompService = inject(RxStompService);
  private notificationEventService = inject(NotificationEventService);
  private metadataProgressService = inject(MetadataProgressService);
  private bookdropFileService = inject(BookdropFileService);
  private duplicateFileService = inject(DuplicateFileService);
  private appConfigService = inject(AppConfigService); // Keep it here to ensure the service is initialized

  ngOnInit(): void {
    this.authInit.initialized$.subscribe(ready => {
      this.loading = !ready;
      if (ready && !this.subscriptionsInitialized) {
        this.setupWebSocketSubscriptions();
        this.subscriptionsInitialized = true;
      }
    });
  }

  private setupWebSocketSubscriptions(): void {
    this.subscriptions.push(
      this.rxStompService.watch('/user/queue/book-add').subscribe(msg =>
        this.bookService.handleNewlyCreatedBook(JSON.parse(msg.body))
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
      this.rxStompService.watch('/user/queue/duplicate-file').subscribe(msg =>
        this.duplicateFileService.addDuplicateFile(JSON.parse(msg.body) as DuplicateFileNotification)
      )
    );
    this.subscriptions.push(
      this.rxStompService.watch('/user/queue/bookdrop-file').subscribe(msg => {
        const notification = JSON.parse(msg.body) as BookdropFileNotification;
        this.bookdropFileService.handleIncomingFile(notification);
      })
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }
}
