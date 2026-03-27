import { inject, Injectable } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { fromEvent, merge, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { BookType } from '../../features/book/model/book.model';
import {
  ReadingSessionApiService,
  CreateReadingSessionDto
} from './reading-session-api.service';

export interface ReadingSession {
  bookId: number;
  bookType: BookType;
  startTime: Date;
  endTime?: Date;
  durationSeconds?: number;
  startLocation?: string;
  endLocation?: string;
  startProgress?: number;
  endProgress?: number;
  progressDelta?: number;
}

@Injectable({
  providedIn: 'root'
})
export class ReadingSessionService {
  private readonly apiService = inject(ReadingSessionApiService);

  private currentSession: ReadingSession | null = null;
  private idleTimer: ReturnType<typeof setTimeout> | null = null;
  private activitySubscription: Subscription | null = null;
  private hiddenAt: number | null = null;

  private readonly IDLE_TIMEOUT_MS = 5 * 60 * 1000;
  private readonly MIN_SESSION_DURATION_SECONDS = 30;
  private readonly ACTIVITY_DEBOUNCE_MS = 1000;

  constructor() {
    this.setupBrowserLifecycleListeners();
  }

  private setupBrowserLifecycleListeners(): void {
    window.addEventListener('beforeunload', () => {
      if (this.currentSession) {
        this.endSessionSync();
      }
    });

    document.addEventListener('visibilitychange', () => {
      if (document.hidden && this.currentSession) {
        this.log('Tab hidden, pausing session');
        this.hiddenAt = Date.now();
        this.pauseIdleDetection();
      } else if (!document.hidden && this.currentSession) {
        if (this.hiddenAt && Date.now() - this.hiddenAt > this.IDLE_TIMEOUT_MS) {
          this.log('Tab was hidden too long, ending session');
          this.stopIdleDetection();
          const cappedEndTime = new Date(this.hiddenAt + this.IDLE_TIMEOUT_MS);
          this.currentSession.endTime = cappedEndTime;
          const durationMs = cappedEndTime.getTime() - this.currentSession.startTime.getTime();
          this.currentSession.durationSeconds = Math.floor(durationMs / 1000);
          if (this.currentSession.startProgress != null && this.currentSession.endProgress != null) {
            this.currentSession.progressDelta = this.currentSession.endProgress - this.currentSession.startProgress;
          }
          if (this.currentSession.durationSeconds >= this.MIN_SESSION_DURATION_SECONDS) {
            this.sendSessionToBackend(this.currentSession);
          }
          this.currentSession = null;
        } else {
          this.log('Tab visible, resuming session');
          this.resumeIdleDetection();
        }
        this.hiddenAt = null;
      }
    });
  }

  startSession(bookId: number, bookType: BookType, startLocation?: string, startProgress?: number): void {
    if (this.currentSession) {
      this.endSession();
    }

    this.currentSession = {
      bookId,
      bookType,
      startTime: new Date(),
      startLocation,
      startProgress
    };

    this.log('Reading session started', {
      bookId,
      startTime: this.currentSession.startTime.toISOString(),
      startLocation,
      startProgress: startProgress != null ? `${startProgress.toFixed(1)}%` : 'N/A'
    });

    this.startIdleDetection();
  }

  updateProgress(currentLocation?: string, currentProgress?: number): void {
    if (!this.currentSession) {
      return;
    }

    this.currentSession.endLocation = currentLocation;
    this.currentSession.endProgress = currentProgress;
    this.resetIdleTimer();
  }

  endSession(endLocation?: string, endProgress?: number): void {
    if (!this.currentSession) {
      return;
    }

    this.stopIdleDetection();

    this.currentSession.endTime = new Date();
    this.currentSession.endLocation = endLocation ?? this.currentSession.endLocation;
    this.currentSession.endProgress = endProgress ?? this.currentSession.endProgress;

    const durationMs = this.currentSession.endTime.getTime() - this.currentSession.startTime.getTime();
    this.currentSession.durationSeconds = Math.floor(durationMs / 1000);

    if (this.currentSession.startProgress != null && this.currentSession.endProgress != null) {
      this.currentSession.progressDelta = this.currentSession.endProgress - this.currentSession.startProgress;
    }

    if (this.currentSession.durationSeconds >= this.MIN_SESSION_DURATION_SECONDS) {
      this.sendSessionToBackend(this.currentSession);
    } else {
      this.log('Session too short, discarding', {
        durationSeconds: this.currentSession.durationSeconds
      });
    }

    this.currentSession = null;
  }

  private endSessionSync(): void {
    if (!this.currentSession) {
      return;
    }

    let endTime = new Date();
    if (this.hiddenAt && endTime.getTime() - this.hiddenAt > this.IDLE_TIMEOUT_MS) {
      endTime = new Date(this.hiddenAt + this.IDLE_TIMEOUT_MS);
    }
    const durationMs = endTime.getTime() - this.currentSession.startTime.getTime();
    const durationSeconds = Math.floor(durationMs / 1000);

    if (durationSeconds < this.MIN_SESSION_DURATION_SECONDS) {
      this.cleanup();
      return;
    }

    const sessionData = this.buildSessionData(
      this.currentSession,
      endTime,
      durationSeconds
    );

    this.log('Reading session ended (sync)', sessionData);

    const success = this.apiService.sendSessionBeacon(sessionData);
    if (!success) {
      this.logError('sendBeacon failed, request may not have been queued');
    }

    this.cleanup();
  }

  private sendSessionToBackend(session: ReadingSession): void {
    if (!session.endTime || session.durationSeconds == null) {
      this.logError('Invalid session data, missing endTime or duration');
      return;
    }

    const sessionData = this.buildSessionData(
      session,
      session.endTime,
      session.durationSeconds
    );

    this.log('Reading session completed', sessionData);

    this.apiService.createSession(sessionData).subscribe({
      next: () => this.log('Session saved to backend'),
      error: (err: HttpErrorResponse) => this.logError('Failed to save session', err)
    });
  }

  private buildSessionData(
    session: ReadingSession,
    endTime: Date,
    durationSeconds: number
  ): CreateReadingSessionDto {
    return {
      bookId: session.bookId,
      bookType: session.bookType,
      startTime: session.startTime.toISOString(),
      endTime: endTime.toISOString(),
      durationSeconds,
      durationFormatted: this.formatDuration(durationSeconds),
      startProgress: session.startProgress != null ? Math.round(session.startProgress * 100) / 100 : undefined,
      endProgress: session.endProgress != null ? Math.round(session.endProgress * 100) / 100 : undefined,
      progressDelta: session.progressDelta != null ? Math.round(session.progressDelta * 100) / 100 : undefined,
      startLocation: session.startLocation,
      endLocation: session.endLocation
    };
  }

  private formatDuration(seconds: number): string {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;

    if (hours > 0) {
      return `${hours}h ${minutes}m ${secs}s`;
    } else if (minutes > 0) {
      return `${minutes}m ${secs}s`;
    }
    return `${secs}s`;
  }

  private startIdleDetection(): void {
    this.stopIdleDetection();

    const activity$ = merge(
      fromEvent(document, 'mousemove'),
      fromEvent(document, 'mousedown'),
      fromEvent(document, 'keypress'),
      fromEvent(document, 'scroll'),
      fromEvent(document, 'touchstart')
    ).pipe(
      debounceTime(this.ACTIVITY_DEBOUNCE_MS)
    );

    this.activitySubscription = activity$.subscribe(() => {
      this.resetIdleTimer();
    });

    this.resetIdleTimer();
  }

  private pauseIdleDetection(): void {
    if (this.idleTimer) {
      clearTimeout(this.idleTimer);
      this.idleTimer = null;
    }
    if (this.activitySubscription) {
      this.activitySubscription.unsubscribe();
      this.activitySubscription = null;
    }
  }

  private resumeIdleDetection(): void {
    if (this.currentSession) {
      this.startIdleDetection();
    }
  }

  private resetIdleTimer(): void {
    if (this.idleTimer) {
      clearTimeout(this.idleTimer);
    }

    this.idleTimer = setTimeout(() => {
      this.log('User idle detected, ending session');
      this.endSession();
    }, this.IDLE_TIMEOUT_MS);
  }

  private stopIdleDetection(): void {
    if (this.idleTimer) {
      clearTimeout(this.idleTimer);
      this.idleTimer = null;
    }
    if (this.activitySubscription) {
      this.activitySubscription.unsubscribe();
      this.activitySubscription = null;
    }
  }

  private cleanup(): void {
    this.stopIdleDetection();
    this.currentSession = null;
  }

  isSessionActive(): boolean {
    return this.currentSession !== null;
  }

  private log(message: string, data?: unknown): void {
    if (data) {
      console.log(`[ReadingSession] ${message}`, data);
    } else {
      console.log(`[ReadingSession] ${message}`);
    }
  }

  private logError(message: string, error?: unknown): void {
    if (error) {
      console.error(`[ReadingSession] ${message}`, error);
    } else {
      console.error(`[ReadingSession] ${message}`);
    }
  }
}
