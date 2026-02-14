import {Component, ElementRef, inject, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {CommonModule, Location} from '@angular/common';
import {ActivatedRoute} from '@angular/router';
import {FormsModule} from '@angular/forms';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';

import {Button} from 'primeng/button';
import {Slider, SliderChangeEvent} from 'primeng/slider';
import {ProgressSpinner} from 'primeng/progressspinner';
import {Tooltip} from 'primeng/tooltip';
import {MenuItem, MessageService} from 'primeng/api';
import {SelectButton} from 'primeng/selectbutton';
import {Menu} from 'primeng/menu';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

import {AudiobookService} from './audiobook.service';
import {AudiobookChapter, AudiobookInfo, AudiobookProgress, AudiobookTrack} from './audiobook.model';
import {BookService} from '../../book/service/book.service';
import {BookMark, BookMarkService, CreateBookMarkRequest} from '../../../shared/service/book-mark.service';
import {AudiobookSessionService} from '../../../shared/service/audiobook-session.service';
import {PageTitleService} from '../../../shared/service/page-title.service';
import {AuthService} from '../../../shared/service/auth.service';
import {API_CONFIG} from '../../../core/config/api-config';

@Component({
  selector: 'app-audiobook-player',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    Button,
    Slider,
    ProgressSpinner,
    Tooltip,
    SelectButton,
    Menu,
    TranslocoDirective
  ],
  templateUrl: './audiobook-player.component.html',
  styleUrls: ['./audiobook-player.component.scss']
})
export class AudiobookPlayerComponent implements OnInit, OnDestroy {
  @ViewChild('audioElement') audioElement!: ElementRef<HTMLAudioElement>;

  private destroy$ = new Subject<void>();
  private audiobookService = inject(AudiobookService);
  private bookService = inject(BookService);
  private bookMarkService = inject(BookMarkService);
  private authService = inject(AuthService);
  private route = inject(ActivatedRoute);
  private location = inject(Location);
  private messageService = inject(MessageService);
  private audiobookSessionService = inject(AudiobookSessionService);
  private pageTitle = inject(PageTitleService);
  private readonly t = inject(TranslocoService);

  isLoading = true;
  audioLoading = false;
  audioInitialized = false;

  bookId!: number;
  audiobookInfo!: AudiobookInfo;
  coverUrl?: string;
  bookCoverUrl?: string;

  isPlaying = false;
  currentTime = 0;
  duration = 0;
  volume = 1;
  previousVolume = 1;
  isMuted = false;
  playbackRate = 1;
  buffered = 0;

  private savedPosition = 0;

  currentTrackIndex = 0;
  audioSrc = '';

  showTrackList = false;
  showBookmarkList = false;

  sleepTimerActive = false;
  sleepTimerRemaining = 0;
  sleepTimerEndOfChapter = false;
  private sleepTimerInterval?: ReturnType<typeof setInterval>;
  private originalVolume = 1;

  sleepTimerOptions: MenuItem[] = [];

  bookmarks: BookMark[] = [];

  playbackRates = [
    {label: '0.5x', value: 0.5},
    {label: '0.75x', value: 0.75},
    {label: '1x', value: 1},
    {label: '1.25x', value: 1.25},
    {label: '1.5x', value: 1.5},
    {label: '2x', value: 2}
  ];

  private progressSaveInterval?: ReturnType<typeof setInterval>;

  private seekDebounceTimeout?: ReturnType<typeof setTimeout>;
  private isSeeking = false;

  ngOnInit(): void {
    this.sleepTimerOptions = [
      {label: this.t.translate('readerAudiobook.sleepTimerMenu.minutes15'), command: () => this.setSleepTimer(15)},
      {label: this.t.translate('readerAudiobook.sleepTimerMenu.minutes30'), command: () => this.setSleepTimer(30)},
      {label: this.t.translate('readerAudiobook.sleepTimerMenu.minutes45'), command: () => this.setSleepTimer(45)},
      {label: this.t.translate('readerAudiobook.sleepTimerMenu.minutes60'), command: () => this.setSleepTimer(60)},
      {label: this.t.translate('readerAudiobook.sleepTimerMenu.endOfChapter'), command: () => this.setSleepTimerEndOfChapter()},
      {separator: true},
      {id: 'cancel-timer', label: this.t.translate('readerAudiobook.sleepTimerMenu.cancelTimer'), command: () => this.cancelSleepTimer(), visible: false}
    ];

    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe(params => {
      this.bookId = +params.get('bookId')!;
      this.loadAudiobook();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();

    if (this.progressSaveInterval) {
      clearInterval(this.progressSaveInterval);
    }

    if (this.sleepTimerInterval) {
      clearInterval(this.sleepTimerInterval);
    }

    if (this.seekDebounceTimeout) {
      clearTimeout(this.seekDebounceTimeout);
    }

    this.saveProgress();

    if (this.audiobookSessionService.isSessionActive()) {
      this.audiobookSessionService.endSession(Math.round(this.currentTime * 1000));
    }
  }

  private loadAudiobook(): void {
    this.resetState();
    this.isLoading = true;

    this.audiobookService.getAudiobookInfo(this.bookId).subscribe({
      next: (info) => {
        this.audiobookInfo = info;
        if (info.folderBased && info.tracks && info.tracks.length > 0) {
          // Prepare the source URL but don't load it yet
          this.audioSrc = this.audiobookService.getTrackStreamUrl(this.bookId, 0);
          const track = info.tracks[0];
          if (track?.durationMs) {
            this.duration = track.durationMs / 1000;
          }
        } else {
          this.audioSrc = this.audiobookService.getStreamUrl(this.bookId);
          if (info.durationMs) {
            this.duration = info.durationMs / 1000;
          }
        }

        // Load cover URLs - prefer stored audiobook cover, fall back to embedded, then book cover
        const token = this.authService.getInternalAccessToken() || this.authService.getOidcAccessToken();
        this.bookCoverUrl = `${API_CONFIG.BASE_URL}/api/v1/media/book/${this.bookId}/cover?token=${encodeURIComponent(token || '')}`;
        this.coverUrl = `${API_CONFIG.BASE_URL}/api/v1/media/book/${this.bookId}/audiobook-cover?token=${encodeURIComponent(token || '')}`;

        this.isLoading = false;

        // Load book details in parallel (for progress, title, etc.) - non-blocking
        this.loadBookDetails(info);
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: this.t.translate('readerAudiobook.toast.loadFailed')
        });
        this.isLoading = false;
        this.audioLoading = false;
      }
    });
  }

  private loadBookDetails(info: AudiobookInfo): void {
    this.bookService.getBookByIdFromAPI(this.bookId, false)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (book) => {
          this.pageTitle.setBookPageTitle(book);

          if (book.audiobookProgress) {
            this.savedPosition = book.audiobookProgress.positionMs
              ? book.audiobookProgress.positionMs / 1000
              : 0;

            // If audio is already loaded, seek to saved position
            const audio = this.audioElement?.nativeElement;
            if (audio && audio.readyState >= 1 && this.savedPosition > 0) {
              audio.currentTime = this.savedPosition;
              this.currentTime = this.savedPosition;
            }

            // Handle track index for folder-based audiobooks
            if (info.folderBased && info.tracks && info.tracks.length > 0) {
              const trackIndex = book.audiobookProgress?.trackIndex ?? 0;
              if (trackIndex !== this.currentTrackIndex) {
                this.currentTrackIndex = trackIndex;
                this.loadTrack(trackIndex, false);
                const track = info.tracks[trackIndex];
                if (track?.durationMs) {
                  this.duration = track.durationMs / 1000;
                }
              }
            }
          }

          if (this.savedPosition > 0) {
            this.currentTime = this.savedPosition;
          }
        },
        error: () => {
          // Non-critical - audiobook can still play without book details
        }
      });

    this.loadBookmarks();
  }

  private loadTrack(index: number, showLoading = true): void {
    if (!this.audiobookInfo.tracks || index < 0 || index >= this.audiobookInfo.tracks.length) {
      return;
    }
    this.currentTrackIndex = index;
    this.audioSrc = this.audiobookService.getTrackStreamUrl(this.bookId, index);
    this.buffered = 0;
    const track = this.audiobookInfo.tracks[index];
    if (track?.durationMs) {
      this.duration = track.durationMs / 1000;
    }

    // Only set the audio source if already initialized
    if (this.audioInitialized) {
      this.audioLoading = showLoading;
      const audio = this.audioElement?.nativeElement;
      if (audio) {
        audio.src = this.audioSrc;
        audio.load();
      }
    }
  }

  private resetState(): void {
    this.stopProgressSaveInterval();

    this.isPlaying = false;
    this.currentTime = 0;
    this.duration = 0;
    this.buffered = 0;
    this.savedPosition = 0;
    this.currentTrackIndex = 0;
    this.audioSrc = '';
    this.audioLoading = false;
    this.audioInitialized = false;

    this.showTrackList = false;
    this.coverUrl = undefined;
    this.bookCoverUrl = undefined;
  }

  onAudioLoaded(): void {
    this.audioLoading = false;
    const audio = this.audioElement?.nativeElement;
    if (audio) {
      if (audio.duration && isFinite(audio.duration)) {
        this.duration = audio.duration;
      }
      audio.volume = this.volume;
      audio.playbackRate = this.playbackRate;

      if (this.savedPosition > 0 && this.savedPosition < this.duration) {
        audio.currentTime = this.savedPosition;
        this.currentTime = this.savedPosition;
        this.savedPosition = 0;
      }

      this.setupMediaSession();
    }
  }

  onTimeUpdate(): void {
    const audio = this.audioElement?.nativeElement;
    if (audio) {
      const previousChapterIndex = this.getCurrentChapterIndex();
      if (!this.isSeeking) {
        this.currentTime = audio.currentTime;
      }

      this.audiobookSessionService.updatePosition(
        Math.round(this.currentTime * 1000),
        this.audiobookInfo?.folderBased ? this.currentTrackIndex : undefined
      );

      if (Math.floor(this.currentTime) % 5 === 0) {
        this.updateMediaSessionPositionState();
      }

      if (!this.audiobookInfo.folderBased && this.getCurrentChapterIndex() !== previousChapterIndex) {
        this.updateMediaSessionMetadata();
      }

      this.checkSleepTimerEndOfChapter();
    }
  }

  onProgress(): void {
    const audio = this.audioElement?.nativeElement;
    if (audio && audio.buffered.length > 0) {
      this.buffered = audio.buffered.end(audio.buffered.length - 1);
    }
  }

  onAudioEnded(): void {
    if (this.audiobookInfo.folderBased && this.audiobookInfo.tracks) {
      if (this.currentTrackIndex < this.audiobookInfo.tracks.length - 1) {
        this.nextTrack();
      } else {
        this.isPlaying = false;
        this.stopProgressSaveInterval();
        this.saveProgress();
        this.updateMediaSessionPlaybackState();
        this.audiobookSessionService.pauseSession(Math.round(this.currentTime * 1000));
      }
    } else {
      this.isPlaying = false;
      this.stopProgressSaveInterval();
      this.saveProgress();
      this.updateMediaSessionPlaybackState();
      this.audiobookSessionService.pauseSession(Math.round(this.currentTime * 1000));
    }
  }

  onAudioError(): void {
    this.audioLoading = false;
    this.messageService.add({
      severity: 'error',
      summary: this.t.translate('common.error'),
      detail: this.t.translate('readerAudiobook.toast.audioLoadFailed')
    });
  }

  private setupMediaSession(): void {
    if (!('mediaSession' in navigator)) return;

    this.updateMediaSessionMetadata();

    navigator.mediaSession.setActionHandler('play', () => {
      if (!this.isPlaying) this.togglePlay();
    });
    navigator.mediaSession.setActionHandler('pause', () => {
      if (this.isPlaying) this.togglePlay();
    });
    navigator.mediaSession.setActionHandler('seekbackward', () => this.seekRelative(-30));
    navigator.mediaSession.setActionHandler('seekforward', () => this.seekRelative(30));
    navigator.mediaSession.setActionHandler('previoustrack', () => {
      this.audiobookInfo.folderBased ? this.previousTrack() : this.previousChapter();
    });
    navigator.mediaSession.setActionHandler('nexttrack', () => {
      this.audiobookInfo.folderBased ? this.nextTrack() : this.nextChapter();
    });
    navigator.mediaSession.setActionHandler('seekto', (details) => {
      if (details.seekTime !== undefined) {
        const audio = this.audioElement?.nativeElement;
        if (audio) {
          audio.currentTime = details.seekTime;
          this.currentTime = details.seekTime;
        }
      }
    });
  }

  private updateMediaSessionMetadata(): void {
    if (!('mediaSession' in navigator)) return;

    const title = this.audiobookInfo.folderBased
      ? this.currentTrack?.title
      : this.getCurrentChapter()?.title || this.audiobookInfo.title;

    navigator.mediaSession.metadata = new MediaMetadata({
      title: title || this.t.translate('readerAudiobook.untitled'),
      artist: this.audiobookInfo.author || this.t.translate('readerAudiobook.unknownAuthor'),
      album: this.audiobookInfo.title,
      artwork: this.coverUrl
        ? [{src: this.coverUrl, sizes: '512x512', type: 'image/png'}]
        : []
    });
  }

  private updateMediaSessionPlaybackState(): void {
    if ('mediaSession' in navigator) {
      navigator.mediaSession.playbackState = this.isPlaying ? 'playing' : 'paused';
    }
  }

  private updateMediaSessionPositionState(): void {
    if ('mediaSession' in navigator && 'setPositionState' in navigator.mediaSession) {
      try {
        navigator.mediaSession.setPositionState({
          duration: this.duration,
          playbackRate: this.playbackRate,
          position: this.currentTime
        });
      } catch {
      }
    }
  }

  togglePlay(): void {
    const audio = this.audioElement?.nativeElement;
    if (!audio) return;

    // Initialize audio on first play
    if (!this.audioInitialized) {
      this.audioLoading = true;
      this.audioInitialized = true;
      audio.src = this.audioSrc;
      audio.load();

      // Wait for enough data to play
      const playWhenReady = () => {
        audio.removeEventListener('canplay', playWhenReady);
        audio.play();
        this.isPlaying = true;
        this.startProgressSaveInterval();
        this.updateMediaSessionPlaybackState();
        if (this.audiobookSessionService.isSessionActive()) {
          this.audiobookSessionService.resumeSession(Math.round(this.currentTime * 1000));
        } else {
          this.audiobookSessionService.startSession(
            this.bookId,
            Math.round(this.currentTime * 1000),
            this.playbackRate,
            this.audiobookInfo?.bookFileId,
            this.audiobookInfo?.folderBased ? this.currentTrackIndex : undefined
          );
        }
      };
      audio.addEventListener('canplay', playWhenReady);
      return;
    }

    if (this.isPlaying) {
      audio.pause();
      this.stopProgressSaveInterval();
      this.saveProgress();
      this.audiobookSessionService.pauseSession(Math.round(this.currentTime * 1000));
    } else {
      audio.play();
      this.startProgressSaveInterval();
      if (this.audiobookSessionService.isSessionActive()) {
        this.audiobookSessionService.resumeSession(Math.round(this.currentTime * 1000));
      } else {
        this.audiobookSessionService.startSession(
          this.bookId,
          Math.round(this.currentTime * 1000),
          this.playbackRate,
          this.audiobookInfo?.bookFileId,
          this.audiobookInfo?.folderBased ? this.currentTrackIndex : undefined
        );
      }
    }
    this.isPlaying = !this.isPlaying;
    this.updateMediaSessionPlaybackState();
  }

  seek(event: SliderChangeEvent): void {
    if (this.duration > 0 && event.value !== undefined) {
      const seekTime = event.value as number;
      this.isSeeking = true;
      this.currentTime = seekTime;

      if (this.seekDebounceTimeout) {
        clearTimeout(this.seekDebounceTimeout);
      }
      this.seekDebounceTimeout = setTimeout(() => {
        const audio = this.audioElement?.nativeElement;
        if (audio) {
          audio.currentTime = seekTime;
        }
        this.isSeeking = false;
      }, 150);
    }
  }

  seekRelative(seconds: number): void {
    const audio = this.audioElement?.nativeElement;
    if (audio) {
      const newTime = Math.max(0, Math.min(this.duration, audio.currentTime + seconds));
      audio.currentTime = newTime;
      this.currentTime = newTime;
    }
  }

  setVolume(event: SliderChangeEvent): void {
    const audio = this.audioElement?.nativeElement;
    if (event.value !== undefined) {
      this.volume = event.value as number;
      this.isMuted = this.volume === 0;
      if (audio) {
        audio.volume = this.volume;
      }
    }
  }

  toggleMute(): void {
    const audio = this.audioElement?.nativeElement;
    if (!audio) return;

    if (this.isMuted) {
      this.volume = this.previousVolume || 0.5;
      audio.volume = this.volume;
      this.isMuted = false;
    } else {
      this.previousVolume = this.volume;
      this.volume = 0;
      audio.volume = 0;
      this.isMuted = true;
    }
  }

  setPlaybackRate(rate: number): void {
    if (rate === undefined || rate === null) return;
    const audio = this.audioElement?.nativeElement;
    this.playbackRate = rate;
    if (audio) {
      audio.playbackRate = rate;
    }
    this.updateMediaSessionPositionState();
    this.audiobookSessionService.updatePlaybackRate(rate);
  }

  previousTrack(): void {
    if (this.currentTrackIndex > 0) {
      this.loadTrack(this.currentTrackIndex - 1);
      this.currentTime = 0;
      this.savedPosition = 0;
      if (this.isPlaying) {
        setTimeout(() => {
          this.audioElement?.nativeElement?.play();
          this.updateMediaSessionMetadata();
        }, 100);
      }
    }
  }

  nextTrack(): void {
    if (this.audiobookInfo.tracks && this.currentTrackIndex < this.audiobookInfo.tracks.length - 1) {
      this.loadTrack(this.currentTrackIndex + 1);
      this.currentTime = 0;
      this.savedPosition = 0;
      if (this.isPlaying) {
        setTimeout(() => {
          this.audioElement?.nativeElement?.play();
          this.updateMediaSessionMetadata();
        }, 100);
      }
    }
  }

  selectTrack(track: AudiobookTrack): void {
    this.currentTrackIndex = track.index;
    this.audioSrc = this.audiobookService.getTrackStreamUrl(this.bookId, track.index);
    this.currentTime = 0;
    this.savedPosition = 0;
    this.buffered = 0;
    const trackInfo = this.audiobookInfo.tracks?.[track.index];
    if (trackInfo?.durationMs) {
      this.duration = trackInfo.durationMs / 1000;
    }
    this.showTrackList = false;

    const audio = this.audioElement?.nativeElement;
    if (audio) {
      this.audioLoading = true;
      this.audioInitialized = true;
      audio.src = this.audioSrc;
      audio.load();
    }

    setTimeout(() => {
      this.audioElement?.nativeElement?.play();
      this.isPlaying = true;
      this.startProgressSaveInterval();
      this.updateMediaSessionMetadata();
      this.updateMediaSessionPlaybackState();
      if (this.audiobookSessionService.isSessionActive()) {
        this.audiobookSessionService.resumeSession(0);
      } else {
        this.audiobookSessionService.startSession(
          this.bookId, 0, this.playbackRate,
          this.audiobookInfo?.bookFileId, track.index
        );
      }
    }, 100);
  }

  selectChapter(chapter: AudiobookChapter): void {
    const audio = this.audioElement?.nativeElement;
    if (!audio) return;

    // Initialize audio if not yet done
    if (!this.audioInitialized) {
      this.audioLoading = true;
      this.audioInitialized = true;
      audio.src = this.audioSrc;
      audio.load();

      const seekAndPlay = () => {
        audio.removeEventListener('canplay', seekAndPlay);
        audio.currentTime = chapter.startTimeMs / 1000;
        this.currentTime = chapter.startTimeMs / 1000;
        audio.play();
        this.isPlaying = true;
        this.startProgressSaveInterval();
        this.updateMediaSessionMetadata();
        this.updateMediaSessionPlaybackState();
        if (this.audiobookSessionService.isSessionActive()) {
          this.audiobookSessionService.resumeSession(chapter.startTimeMs);
        } else {
          this.audiobookSessionService.startSession(
            this.bookId, chapter.startTimeMs, this.playbackRate,
            this.audiobookInfo?.bookFileId
          );
        }
      };
      audio.addEventListener('canplay', seekAndPlay);
      this.showTrackList = false;
      return;
    }

    audio.currentTime = chapter.startTimeMs / 1000;
    this.currentTime = chapter.startTimeMs / 1000;
    this.showTrackList = false;
    this.updateMediaSessionMetadata();
    if (!this.isPlaying) {
      audio.play();
      this.isPlaying = true;
      this.startProgressSaveInterval();
      this.updateMediaSessionPlaybackState();
      if (this.audiobookSessionService.isSessionActive()) {
        this.audiobookSessionService.resumeSession(chapter.startTimeMs);
      } else {
        this.audiobookSessionService.startSession(
          this.bookId, chapter.startTimeMs, this.playbackRate,
          this.audiobookInfo?.bookFileId
        );
      }
    }
  }

  getCurrentChapter(): AudiobookChapter | undefined {
    if (!this.audiobookInfo?.chapters) return undefined;
    const currentMs = this.currentTime * 1000;
    return this.audiobookInfo.chapters.find(
      ch => currentMs >= ch.startTimeMs && currentMs < ch.endTimeMs
    );
  }

  getCurrentChapterIndex(): number {
    const chapter = this.getCurrentChapter();
    return chapter?.index ?? 0;
  }

  hasMultipleChapters(): boolean {
    return (this.audiobookInfo?.chapters?.length ?? 0) > 1;
  }

  canGoPreviousChapter(): boolean {
    return this.getCurrentChapterIndex() > 0;
  }

  canGoNextChapter(): boolean {
    const chapters = this.audiobookInfo?.chapters;
    if (!chapters) return false;
    return this.getCurrentChapterIndex() < chapters.length - 1;
  }

  previousChapter(): void {
    const chapters = this.audiobookInfo?.chapters;
    if (!chapters) return;

    const currentIndex = this.getCurrentChapterIndex();
    if (currentIndex > 0) {
      const prevChapter = chapters[currentIndex - 1];
      this.selectChapter(prevChapter);
    }
  }

  nextChapter(): void {
    const chapters = this.audiobookInfo?.chapters;
    if (!chapters) return;

    const currentIndex = this.getCurrentChapterIndex();
    if (currentIndex < chapters.length - 1) {
      const nextChapter = chapters[currentIndex + 1];
      this.selectChapter(nextChapter);
    }
  }

  private startProgressSaveInterval(): void {
    if (this.progressSaveInterval) return;

    this.progressSaveInterval = setInterval(() => {
      if (this.isPlaying) {
        this.saveProgress();
      }
    }, 5000);
  }

  private stopProgressSaveInterval(): void {
    if (this.progressSaveInterval) {
      clearInterval(this.progressSaveInterval);
      this.progressSaveInterval = undefined;
    }
  }

  private saveProgress(): void {
    if (!this.audiobookInfo) return;

    const totalDuration = this.getTotalDuration();
    const currentPosition = this.getCurrentTotalPosition();
    const percentage = totalDuration > 0 ? (currentPosition / totalDuration) * 100 : 0;

    const positionMs = this.audiobookInfo.folderBased
      ? Math.round(this.currentTime * 1000)
      : Math.round(currentPosition * 1000);

    const progress: AudiobookProgress = {
      positionMs: positionMs,
      trackIndex: this.audiobookInfo.folderBased ? this.currentTrackIndex : undefined,
      percentage: Math.round(percentage * 10) / 10
    };

    this.audiobookService.saveProgress(
      this.bookId,
      progress,
      this.audiobookInfo.bookFileId
    ).subscribe();
  }

  private getTotalDuration(): number {
    if (this.audiobookInfo.folderBased && this.audiobookInfo.tracks) {
      return this.audiobookInfo.tracks.reduce((sum, t) => sum + t.durationMs, 0) / 1000;
    }
    return this.duration;
  }

  private getCurrentTotalPosition(): number {
    if (this.audiobookInfo.folderBased && this.audiobookInfo.tracks) {
      const currentTrack = this.audiobookInfo.tracks[this.currentTrackIndex];
      return (currentTrack?.cumulativeStartMs || 0) / 1000 + this.currentTime;
    }
    return this.currentTime;
  }

  formatTime(seconds: number): string {
    if (!seconds || !isFinite(seconds)) return '0:00';
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = Math.floor(seconds % 60);

    if (h > 0) {
      return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
    }
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  formatDuration(ms: number): string {
    return this.formatTime(ms / 1000);
  }

  getProgressPercent(): number {
    return this.duration > 0 ? (this.currentTime / this.duration) * 100 : 0;
  }

  getBufferedPercent(): number {
    return this.duration > 0 ? (this.buffered / this.duration) * 100 : 0;
  }

  onCoverError(): void {
    // Fallback chain: stored audiobook cover -> embedded cover -> book cover
    const embeddedCoverUrl = this.audiobookService.getEmbeddedCoverUrl(this.bookId);
    if (this.coverUrl && !this.coverUrl.includes('/audiobook/') && this.coverUrl !== this.bookCoverUrl) {
      // First fallback: try embedded cover from file
      this.coverUrl = embeddedCoverUrl;
    } else if (this.coverUrl !== this.bookCoverUrl) {
      // Second fallback: try book cover
      this.coverUrl = this.bookCoverUrl;
    } else {
      this.coverUrl = undefined;
    }
  }

  toggleTrackList(): void {
    this.showTrackList = !this.showTrackList;
  }

  closeReader(): void {
    this.saveProgress();
    if (this.audiobookSessionService.isSessionActive()) {
      this.audiobookSessionService.endSession(Math.round(this.currentTime * 1000));
    }
    this.location.back();
  }

  getVolumeIcon(): string {
    if (this.isMuted || this.volume === 0) return 'pi pi-volume-off';
    if (this.volume < 0.5) return 'pi pi-volume-down';
    return 'pi pi-volume-up';
  }

  get currentTrack(): AudiobookTrack | undefined {
    return this.audiobookInfo?.tracks?.[this.currentTrackIndex];
  }

  setSleepTimer(minutes: number): void {
    this.cancelSleepTimer();
    this.sleepTimerRemaining = minutes * 60;
    this.sleepTimerActive = true;
    this.sleepTimerEndOfChapter = false;
    this.originalVolume = this.volume;
    this.updateSleepTimerMenuVisibility();

    this.sleepTimerInterval = setInterval(() => {
      this.sleepTimerRemaining--;

      if (this.sleepTimerRemaining <= 30 && this.sleepTimerRemaining > 0) {
        const fadeRatio = this.sleepTimerRemaining / 30;
        const audio = this.audioElement?.nativeElement;
        if (audio) {
          audio.volume = this.originalVolume * fadeRatio;
        }
      }

      if (this.sleepTimerRemaining <= 0) {
        this.triggerSleepTimerStop();
      }
    }, 1000);

    this.messageService.add({
      severity: 'info',
      summary: this.t.translate('readerAudiobook.extra.sleepTimer'),
      detail: this.t.translate('readerAudiobook.toast.sleepTimerSet', {minutes})
    });
  }

  setSleepTimerEndOfChapter(): void {
    this.cancelSleepTimer();
    this.sleepTimerActive = true;
    this.sleepTimerEndOfChapter = true;
    this.sleepTimerRemaining = 0;
    this.originalVolume = this.volume;
    this.updateSleepTimerMenuVisibility();

    this.messageService.add({
      severity: 'info',
      summary: this.t.translate('readerAudiobook.extra.sleepTimer'),
      detail: this.t.translate('readerAudiobook.toast.sleepTimerEndOfChapter')
    });
  }

  cancelSleepTimer(): void {
    if (this.sleepTimerInterval) {
      clearInterval(this.sleepTimerInterval);
      this.sleepTimerInterval = undefined;
    }

    if (this.sleepTimerActive && this.originalVolume > 0) {
      const audio = this.audioElement?.nativeElement;
      if (audio) {
        audio.volume = this.originalVolume;
        this.volume = this.originalVolume;
      }
    }

    this.sleepTimerActive = false;
    this.sleepTimerRemaining = 0;
    this.sleepTimerEndOfChapter = false;
    this.updateSleepTimerMenuVisibility();
  }

  private triggerSleepTimerStop(): void {
    const audio = this.audioElement?.nativeElement;
    if (audio) {
      audio.pause();
      audio.volume = this.originalVolume;
      this.volume = this.originalVolume;
    }
    this.isPlaying = false;
    this.stopProgressSaveInterval();
    this.saveProgress();
    this.cancelSleepTimer();
    this.updateMediaSessionPlaybackState();
    this.audiobookSessionService.pauseSession(Math.round(this.currentTime * 1000));

    this.messageService.add({
      severity: 'info',
      summary: this.t.translate('readerAudiobook.extra.sleepTimer'),
      detail: this.t.translate('readerAudiobook.toast.sleepTimerStopped')
    });
  }

  private updateSleepTimerMenuVisibility(): void {
    const cancelItem = this.sleepTimerOptions.find(item => item.id === 'cancel-timer');
    if (cancelItem) {
      cancelItem.visible = this.sleepTimerActive;
    }
  }

  formatSleepTimerRemaining(): string {
    if (this.sleepTimerEndOfChapter) {
      return this.t.translate('readerAudiobook.extra.endOfChapter');
    }
    const minutes = Math.floor(this.sleepTimerRemaining / 60);
    const seconds = this.sleepTimerRemaining % 60;
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  }

  private checkSleepTimerEndOfChapter(): void {
    if (!this.sleepTimerEndOfChapter || !this.sleepTimerActive) return;

    const currentChapter = this.getCurrentChapter();
    if (currentChapter) {
      const currentMs = this.currentTime * 1000;
      if (currentMs >= currentChapter.endTimeMs - 1000) {
        this.triggerSleepTimerStop();
      }
    }
  }

  loadBookmarks(): void {
    this.bookMarkService.getBookmarksForBook(this.bookId)
      .pipe(takeUntil(this.destroy$))
      .subscribe(bookmarks => {
        this.bookmarks = bookmarks;
      });
  }

  toggleBookmarkList(): void {
    this.showBookmarkList = !this.showBookmarkList;
    if (this.showBookmarkList && this.bookmarks.length === 0) {
      this.loadBookmarks();
    }
  }

  addBookmark(): void {
    const currentChapter = this.getCurrentChapter();
    const currentTrack = this.currentTrack;

    let title: string;
    if (this.audiobookInfo.folderBased && currentTrack) {
      title = `${currentTrack.title} - ${this.formatTime(this.currentTime)}`;
    } else if (currentChapter) {
      title = `${currentChapter.title} - ${this.formatTime(this.currentTime)}`;
    } else {
      title = this.t.translate('readerAudiobook.bookmarks.bookmarkAt', {time: this.formatTime(this.currentTime)});
    }

    const request: CreateBookMarkRequest = {
      bookId: this.bookId,
      title: title,
      positionMs: Math.round(this.currentTime * 1000),
      trackIndex: this.audiobookInfo.folderBased ? this.currentTrackIndex : undefined
    };

    this.bookMarkService.createBookmark(request)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (bookmark) => {
          this.bookmarks = [...this.bookmarks, bookmark];
          this.messageService.add({
            severity: 'success',
            summary: this.t.translate('readerAudiobook.toast.bookmarkAdded'),
            detail: title
          });
        },
        error: (err) => {
          const isDuplicate = err?.status === 409;
          this.messageService.add({
            severity: isDuplicate ? 'warn' : 'error',
            summary: isDuplicate ? this.t.translate('readerAudiobook.toast.bookmarkExists') : this.t.translate('common.error'),
            detail: isDuplicate ? this.t.translate('readerAudiobook.toast.bookmarkExistsDetail') : this.t.translate('readerAudiobook.toast.bookmarkFailed')
          });
        }
      });
  }

  goToBookmark(bookmark: BookMark): void {
    const audio = this.audioElement?.nativeElement;
    if (!audio) return;

    const targetPosition = (bookmark.positionMs || 0) / 1000;

    // Initialize audio if not yet done
    if (!this.audioInitialized) {
      if (this.audiobookInfo.folderBased && bookmark.trackIndex !== undefined && bookmark.trackIndex !== null) {
        this.currentTrackIndex = bookmark.trackIndex;
        this.audioSrc = this.audiobookService.getTrackStreamUrl(this.bookId, bookmark.trackIndex);
      }

      this.audioLoading = true;
      this.audioInitialized = true;
      audio.src = this.audioSrc;
      audio.load();

      const seekAndPlay = () => {
        audio.removeEventListener('canplay', seekAndPlay);
        audio.currentTime = targetPosition;
        this.currentTime = targetPosition;
        audio.play();
        this.isPlaying = true;
        this.startProgressSaveInterval();
        const positionMs = bookmark.positionMs || 0;
        if (this.audiobookSessionService.isSessionActive()) {
          this.audiobookSessionService.resumeSession(positionMs);
        } else {
          this.audiobookSessionService.startSession(
            this.bookId, positionMs, this.playbackRate,
            this.audiobookInfo?.bookFileId,
            this.audiobookInfo?.folderBased ? bookmark.trackIndex : undefined
          );
        }
      };
      audio.addEventListener('canplay', seekAndPlay);
      this.showBookmarkList = false;
      return;
    }

    if (this.audiobookInfo.folderBased && bookmark.trackIndex !== undefined && bookmark.trackIndex !== null) {
      if (bookmark.trackIndex !== this.currentTrackIndex) {
        this.loadTrack(bookmark.trackIndex);
        this.savedPosition = targetPosition;
      } else {
        audio.currentTime = targetPosition;
        this.currentTime = targetPosition;
      }
    } else {
      audio.currentTime = targetPosition;
      this.currentTime = targetPosition;
    }

    this.showBookmarkList = false;

    if (!this.isPlaying) {
      setTimeout(() => {
        this.audioElement?.nativeElement?.play();
        this.isPlaying = true;
        this.startProgressSaveInterval();
        const positionMs = bookmark.positionMs || 0;
        if (this.audiobookSessionService.isSessionActive()) {
          this.audiobookSessionService.resumeSession(positionMs);
        } else {
          this.audiobookSessionService.startSession(
            this.bookId, positionMs, this.playbackRate,
            this.audiobookInfo?.bookFileId,
            this.audiobookInfo?.folderBased ? bookmark.trackIndex : undefined
          );
        }
      }, 100);
    }
  }

  deleteBookmark(event: MouseEvent, bookmarkId: number): void {
    event.stopPropagation();
    this.bookMarkService.deleteBookmark(bookmarkId)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.bookmarks = this.bookmarks.filter(b => b.id !== bookmarkId);
        this.messageService.add({
          severity: 'info',
          summary: this.t.translate('readerAudiobook.toast.bookmarkDeleted')
        });
      });
  }

  formatBookmarkPosition(bookmark: BookMark): string {
    if (bookmark.positionMs) {
      return this.formatTime(bookmark.positionMs / 1000);
    }
    return '';
  }
}
