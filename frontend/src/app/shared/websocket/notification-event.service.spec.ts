import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';

import {Severity} from './model/log-notification.model';
import {NotificationEventService} from './notification-event.service';

describe('NotificationEventService', () => {
  let service: NotificationEventService;

  beforeEach(() => {
    vi.useFakeTimers();
    service = new NotificationEventService();
  });

  afterEach(() => {
    vi.runOnlyPendingTimers();
    vi.useRealTimers();
  });

  it('emits the latest notification and highlights it immediately', () => {
    const notifications: string[] = [];
    const highlights: boolean[] = [];

    service.latestNotification$.subscribe(notification => notifications.push(notification.message));
    service.notificationHighlight$.subscribe(value => highlights.push(value));

    service.handleNewNotification({message: 'Index updated', severity: Severity.INFO});

    expect(notifications).toEqual(['Index updated']);
    expect(highlights.at(-1)).toBe(true);
  });

  it('turns off the highlight after the timeout elapses', () => {
    const highlights: boolean[] = [];

    service.notificationHighlight$.subscribe(value => highlights.push(value));
    service.handleNewNotification({message: 'Index updated', severity: Severity.INFO});

    vi.advanceTimersByTime(7500);

    expect(highlights).toEqual([false, true, false]);
  });

  it('restarts the highlight timeout when a new notification arrives', () => {
    const highlights: boolean[] = [];

    service.notificationHighlight$.subscribe(value => highlights.push(value));
    service.handleNewNotification({message: 'First', severity: Severity.INFO});
    vi.advanceTimersByTime(5000);

    service.handleNewNotification({message: 'Second', severity: Severity.WARN});
    vi.advanceTimersByTime(7499);

    expect(highlights.at(-1)).toBe(true);

    vi.advanceTimersByTime(1);

    expect(highlights.at(-1)).toBe(false);
  });
});
