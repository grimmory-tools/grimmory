import {Injectable} from '@angular/core';
import {BehaviorSubject, Observable} from 'rxjs';
import {filter} from 'rxjs/operators';
import {LogNotification} from './model/log-notification.model';

@Injectable({
  providedIn: 'root',
})
export class NotificationEventService {
  private latestNotificationSubject = new BehaviorSubject<LogNotification | null>(null);

  latestNotification$: Observable<LogNotification> = this.latestNotificationSubject.asObservable().pipe(
    filter((event): event is LogNotification => event !== null)
  );

  private notificationHighlightSubject = new BehaviorSubject<boolean>(false);
  notificationHighlight$ = this.notificationHighlightSubject.asObservable();

  private highlightTimeout: ReturnType<typeof setTimeout> | undefined;

  handleNewNotification(notification: LogNotification): void {
    this.latestNotificationSubject.next(notification);
    this.notificationHighlightSubject.next(true);

    if (this.highlightTimeout) {
      clearTimeout(this.highlightTimeout);
    }

    this.highlightTimeout = setTimeout(() => {
      this.notificationHighlightSubject.next(false);
    }, 7500);

    setTimeout(() => {
      if (!this.notificationHighlightSubject.value) {
        this.latestNotificationSubject.next(null);
      }
    }, 20000);
  }
}
