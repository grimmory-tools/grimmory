import {Component, inject} from '@angular/core';
import {NotificationEventService} from '../../../shared/websocket/notification-event.service';
import {LogNotification} from '../../../shared/websocket/model/log-notification.model';

@Component({
  selector: 'app-live-notification-box',
  standalone: true,
  templateUrl: './live-notification-box.component.html',
  styleUrls: ['./live-notification-box.component.scss'],
  host: {
    class: 'config-panel'
  },
})
export class LiveNotificationBoxComponent {
  latestNotification: LogNotification = {message: 'No recent notifications...'};

  private notificationService = inject(NotificationEventService);

  constructor() {
    this.notificationService.latestNotification$.subscribe(notification => {
      this.latestNotification = notification;
    });
  }
}
