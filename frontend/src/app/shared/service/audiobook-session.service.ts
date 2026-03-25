import { inject, Injectable } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ReadingSessionApiService,
  CreateReadingSessionDto
} from './reading-session-api.service';

export interface AudiobookSession {
  bookId: number;
  bookFileId?: number;
  sessionStartTime: Date;
  totalListeningSeconds: number;
  startPositionMs: number;
  currentPositionMs: number;
  trackIndex?: number;
  playbackRate: number;
  lastPlayStartTime?: Date;
}

@Injectable({
  providedIn: 'root'
})
export class AudiobookSessionService {
  private readonly apiService = inject(ReadingSessionApiService);

  private currentSession: AudiobookSession | null = null;
  private periodicSaveInterval: ReturnType<typeof setInterval> | null = null;

  private readonly PERIODIC_SAVE_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
  private readonly MIN_SESSION_DURATION_SECONDS = 30;

  constructor() {
    this.setupBrowserLifecycleListeners();
  }

  private setupBrowserLifecycleListeners(): void {
    window.addEventListener('beforeunload', () => {
      if (this.currentSession) {
        this.endSessionSync();
      }
    });

    // Note: We intentionally do NOT pause on visibility change
    // Audiobooks play in the background
  }

  /**
   * Start a new listening session when playback begins
   */
  startSession(
    bookId: number,
    startPositionMs: number,
    playbackRate: number = 1,
    bookFileId?: number,
    trackIndex?: number
  ): void {
    // End any existing session first
    if (this.currentSession) {
      this.endSession();
    }

    this.currentSession = {
      bookId,
      bookFileId,
      sessionStartTime: new Date(),
      totalListeningSeconds: 0,
      startPositionMs,
      currentPositionMs: startPositionMs,
      trackIndex,
      playbackRate,
      lastPlayStartTime: new Date()
    };

    this.log('Audiobook session started', {
      bookId,
      startPosition: this.formatMs(startPositionMs),
      playbackRate
    });

    this.startPeriodicSave();
  }

  /**
   * Called when playback is paused - accumulates listening time
   */
  pauseSession(currentPositionMs: number): void {
    if (!this.currentSession || !this.currentSession.lastPlayStartTime) {
      return;
    }

    // Calculate listening time since last play
    const now = new Date();
    const playDurationMs = now.getTime() - this.currentSession.lastPlayStartTime.getTime();
    const adjustedDurationSeconds = (playDurationMs / 1000) * this.currentSession.playbackRate;

    this.currentSession.totalListeningSeconds += adjustedDurationSeconds;
    this.currentSession.currentPositionMs = currentPositionMs;
    this.currentSession.lastPlayStartTime = undefined;

    this.log('Audiobook session paused', {
      addedSeconds: Math.round(adjustedDurationSeconds),
      totalSeconds: Math.round(this.currentSession.totalListeningSeconds),
      position: this.formatMs(currentPositionMs)
    });
  }

  /**
   * Called when playback resumes after pause
   */
  resumeSession(currentPositionMs: number): void {
    if (!this.currentSession) {
      return;
    }

    this.currentSession.lastPlayStartTime = new Date();
    this.currentSession.currentPositionMs = currentPositionMs;

    this.log('Audiobook session resumed', {
      position: this.formatMs(currentPositionMs)
    });
  }

  /**
   * Update current position (called during playback)
   */
  updatePosition(currentPositionMs: number, trackIndex?: number): void {
    if (!this.currentSession) {
      return;
    }

    this.currentSession.currentPositionMs = currentPositionMs;
    if (trackIndex !== undefined) {
      this.currentSession.trackIndex = trackIndex;
    }
  }

  /**
   * Update playback rate
   */
  updatePlaybackRate(rate: number): void {
    if (!this.currentSession) {
      return;
    }

    // If currently playing, accumulate time at old rate before changing
    if (this.currentSession.lastPlayStartTime) {
      const now = new Date();
      const playDurationMs = now.getTime() - this.currentSession.lastPlayStartTime.getTime();
      const adjustedDurationSeconds = (playDurationMs / 1000) * this.currentSession.playbackRate;
      this.currentSession.totalListeningSeconds += adjustedDurationSeconds;
      this.currentSession.lastPlayStartTime = now;
    }

    this.currentSession.playbackRate = rate;
    this.log('Playback rate changed', { rate });
  }

  /**
   * End the session and send to backend
   */
  endSession(currentPositionMs?: number): void {
    if (!this.currentSession) {
      return;
    }

    this.stopPeriodicSave();

    // If still playing, accumulate final listening time
    if (this.currentSession.lastPlayStartTime) {
      const now = new Date();
      const playDurationMs = now.getTime() - this.currentSession.lastPlayStartTime.getTime();
      const adjustedDurationSeconds = (playDurationMs / 1000) * this.currentSession.playbackRate;
      this.currentSession.totalListeningSeconds += adjustedDurationSeconds;
    }

    if (currentPositionMs !== undefined) {
      this.currentSession.currentPositionMs = currentPositionMs;
    }

    const totalSeconds = Math.round(this.currentSession.totalListeningSeconds);

    if (totalSeconds >= this.MIN_SESSION_DURATION_SECONDS) {
      this.sendSessionToBackend(this.currentSession);
    } else {
      this.log('Session too short, discarding', { totalSeconds });
    }

    this.currentSession = null;
  }

  /**
   * Check if a session is currently active
   */
  isSessionActive(): boolean {
    return this.currentSession !== null;
  }

  /**
   * Check if currently in playing state (not paused)
   */
  isPlaying(): boolean {
    return this.currentSession?.lastPlayStartTime !== undefined;
  }

  /**
   * Get current accumulated listening time in seconds
   */
  getCurrentListeningTime(): number {
    if (!this.currentSession) {
      return 0;
    }

    let total = this.currentSession.totalListeningSeconds;

    // Add time since last play start if currently playing
    if (this.currentSession.lastPlayStartTime) {
      const now = new Date();
      const playDurationMs = now.getTime() - this.currentSession.lastPlayStartTime.getTime();
      total += (playDurationMs / 1000) * this.currentSession.playbackRate;
    }

    return Math.round(total);
  }

  private endSessionSync(): void {
    if (!this.currentSession) {
      return;
    }

    // Accumulate final listening time if playing
    if (this.currentSession.lastPlayStartTime) {
      const now = new Date();
      const playDurationMs = now.getTime() - this.currentSession.lastPlayStartTime.getTime();
      const adjustedDurationSeconds = (playDurationMs / 1000) * this.currentSession.playbackRate;
      this.currentSession.totalListeningSeconds += adjustedDurationSeconds;
    }

    const totalSeconds = Math.round(this.currentSession.totalListeningSeconds);

    if (totalSeconds < this.MIN_SESSION_DURATION_SECONDS) {
      this.cleanup();
      return;
    }

    const sessionData = this.buildSessionData(this.currentSession);

    this.log('Audiobook session ended (sync)', sessionData);

    const success = this.apiService.sendSessionBeacon(sessionData);
    if (!success) {
      this.logError('sendBeacon failed, request may not have been queued');
    }

    this.cleanup();
  }

  private startPeriodicSave(): void {
    this.stopPeriodicSave();

    this.periodicSaveInterval = setInterval(() => {
      if (this.currentSession && this.currentSession.lastPlayStartTime) {
        // Send intermediate session data
        this.sendIntermediateSession();
      }
    }, this.PERIODIC_SAVE_INTERVAL_MS);
  }

  private stopPeriodicSave(): void {
    if (this.periodicSaveInterval) {
      clearInterval(this.periodicSaveInterval);
      this.periodicSaveInterval = null;
    }
  }

  private sendIntermediateSession(): void {
    if (!this.currentSession) {
      return;
    }

    // Calculate current total without modifying state
    let totalSeconds = this.currentSession.totalListeningSeconds;
    if (this.currentSession.lastPlayStartTime) {
      const now = new Date();
      const playDurationMs = now.getTime() - this.currentSession.lastPlayStartTime.getTime();
      totalSeconds += (playDurationMs / 1000) * this.currentSession.playbackRate;
    }

    if (totalSeconds < this.MIN_SESSION_DURATION_SECONDS) {
      return;
    }

    const sessionData = this.buildSessionData(this.currentSession, Math.round(totalSeconds));

    this.log('Sending intermediate session', {
      listeningSeconds: Math.round(totalSeconds)
    });

    this.apiService.createSession(sessionData).subscribe({
      next: () => this.log('Intermediate session saved'),
      error: (err: HttpErrorResponse) => this.logError('Failed to save intermediate session', err)
    });

    // Reset accumulated time after sending (to avoid double-counting)
    // Keep lastPlayStartTime to continue tracking
    this.currentSession.totalListeningSeconds = 0;
    this.currentSession.lastPlayStartTime = new Date();
    this.currentSession.startPositionMs = this.currentSession.currentPositionMs;
  }

  private sendSessionToBackend(session: AudiobookSession): void {
    const sessionData = this.buildSessionData(session);

    this.log('Audiobook session completed', sessionData);

    this.apiService.createSession(sessionData).subscribe({
      next: () => this.log('Session saved to backend'),
      error: (err: HttpErrorResponse) => this.logError('Failed to save session', err)
    });
  }

  private buildSessionData(session: AudiobookSession, overrideDuration?: number): CreateReadingSessionDto {
    const durationSeconds = overrideDuration ?? Math.round(session.totalListeningSeconds);
    const now = new Date();

    // Calculate progress if we have position data
    // Note: Progress calculation would need total duration from the component
    // For now, we use position as location

    return {
      bookId: session.bookId,
      bookType: 'AUDIOBOOK',
      startTime: session.sessionStartTime.toISOString(),
      endTime: now.toISOString(),
      durationSeconds,
      durationFormatted: this.formatDuration(durationSeconds),
      startLocation: this.formatMs(session.startPositionMs),
      endLocation: this.formatMs(session.currentPositionMs)
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

  private formatMs(ms: number): string {
    const totalSeconds = Math.floor(ms / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    if (hours > 0) {
      return `${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    }
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  }

  private cleanup(): void {
    this.stopPeriodicSave();
    this.currentSession = null;
  }

  private log(message: string, data?: unknown): void {
    if (data) {
      console.log(`[AudiobookSession] ${message}`, data);
    } else {
      console.log(`[AudiobookSession] ${message}`);
    }
  }

  private logError(message: string, error?: unknown): void {
    if (error) {
      console.error(`[AudiobookSession] ${message}`, error);
    } else {
      console.error(`[AudiobookSession] ${message}`);
    }
  }
}
