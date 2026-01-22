import {Component, inject, Input, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {catchError} from 'rxjs/operators';
import {of} from 'rxjs';
import {Select} from 'primeng/select';
import {FormsModule} from '@angular/forms';
import {Tooltip} from 'primeng/tooltip';
import {
  addWeeks,
  endOfISOWeek,
  getISOWeek,
  getISOWeeksInYear,
  getISOWeekYear,
  setISOWeek,
  setISOWeekYear,
  startOfISOWeek
} from 'date-fns';
import {BookType} from '../../../../../book/model/book.model';
import {ReadingSessionTimelineResponse, UserStatsService} from '../../../../../settings/user-management/user-stats.service';
import {UrlHelperService} from '../../../../../../shared/service/url-helper.service';

interface ReadingSession {
  startTime: Date;
  endTime: Date;
  duration: number;
  bookTitle?: string;
  bookId: number;
  bookType: BookType;
}

interface TimelineSession {
  startHour: number;
  startMinute: number;
  endHour: number;
  endMinute: number;
  duration: number;
  left: number;
  width: number;
  bookTitle?: string;
  bookId: number;
  bookType: BookType;
  level: number;
  totalLevels: number;
}

interface DayTimeline {
  day: string;
  dayOfWeek: number;
  sessions: TimelineSession[];
}

@Component({
  selector: 'app-reading-session-timeline',
  standalone: true,
  imports: [CommonModule, Select, FormsModule, Tooltip],
  templateUrl: './reading-session-timeline.component.html',
  styleUrls: ['./reading-session-timeline.component.scss']
})
export class ReadingSessionTimelineComponent implements OnInit {
  @Input() initialYear: number = new Date().getFullYear();
  @Input() weekNumber: number = getISOWeek(new Date());

  private userStatsService = inject(UserStatsService);
  private urlHelperService = inject(UrlHelperService);

  public daysOfWeek = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  public hourLabels: string[] = [];
  public timelineData: DayTimeline[] = [];
  public currentYear: number = new Date().getFullYear();
  public currentWeek: number = getISOWeek(new Date());
  private currentDate: Date = new Date();

  public yearOptions: { label: string; value: number }[] = [];
  public weekOptions: { label: string; value: number }[] = [];

  ngOnInit(): void {
    this.currentYear = this.initialYear;
    this.currentWeek = this.weekNumber;
    this.updateDateFromYearAndWeek();
    this.initializeYearOptions();
    this.ensureYearInOptions();
    this.updateWeekOptions();
    this.initializeHourLabels();
    this.loadReadingSessions();
  }

  private initializeYearOptions(): void {
    const currentYear = new Date().getFullYear();
    this.yearOptions = [];
    for (let year = currentYear; year >= currentYear - 10; year--) {
      this.yearOptions.push({label: year.toString(), value: year});
    }
  }

  private updateWeekOptions(): void {
    const weeksInYear = getISOWeeksInYear(this.currentDate);
    this.weekOptions = [];
    for (let week = 1; week <= weeksInYear; week++) {
      this.weekOptions.push({label: `Week ${week}`, value: week});
    }
  }

  public onYearChange(): void {
    this.updateDateFromYearAndWeek();
    const maxWeeks = getISOWeeksInYear(this.currentDate);
    if (this.currentWeek > maxWeeks) {
      this.currentWeek = maxWeeks;
      this.updateDateFromYearAndWeek();
    }
    this.updateWeekOptions();
    this.loadReadingSessions();
  }

  public onWeekChange(): void {
    this.updateDateFromYearAndWeek();
    this.loadReadingSessions();
  }

  private initializeHourLabels(): void {
    for (let i = 0; i < 24; i++) {
      const hour = i === 0 ? 12 : i > 12 ? i - 12 : i;
      const period = i < 12 ? 'AM' : 'PM';
      this.hourLabels.push(`${hour} ${period}`);
    }
  }

  private loadReadingSessions(): void {
    this.userStatsService.getTimelineForWeek(this.currentYear, this.currentWeek)
      .pipe(
        catchError((error) => {
          console.error('Error loading reading sessions:', error);
          return of([]);
        })
      )
      .subscribe({
        next: (response) => {
          const sessions = this.convertResponseToSessions(response);
          this.processSessionData(sessions);
        }
      });
  }

  private convertResponseToSessions(response: ReadingSessionTimelineResponse[]): ReadingSession[] {
    const sessions: ReadingSession[] = [];

    response.forEach((item) => {
      const startTime = new Date(item.startDate);
      const duration = item.totalDurationSeconds / 60;
      const endTime = new Date(startTime.getTime() + item.totalDurationSeconds * 1000);

      sessions.push({
        startTime,
        endTime,
        duration,
        bookId: item.bookId,
        bookTitle: item.bookTitle,
        bookType: item.bookType
      });
    });

    return sessions.sort((a, b) => a.startTime.getTime() - b.startTime.getTime());
  }

  public changeWeek(delta: number): void {
    this.currentDate = addWeeks(this.currentDate, delta);
    this.currentYear = getISOWeekYear(this.currentDate);
    this.currentWeek = getISOWeek(this.currentDate);

    this.ensureYearInOptions();
    this.updateWeekOptions();
    this.loadReadingSessions();
  }

  private ensureYearInOptions(): void {
    if (!this.yearOptions.some(option => option.value === this.currentYear)) {
      this.yearOptions.unshift({label: this.currentYear.toString(), value: this.currentYear});
      this.yearOptions.sort((a, b) => b.value - a.value);
    }
  }

  private updateDateFromYearAndWeek(): void {
    this.currentDate = setISOWeek(setISOWeekYear(new Date(), this.currentYear), this.currentWeek);
  }

  public getWeekDateRange(): string {
    const weekStart = startOfISOWeek(this.currentDate);
    const weekEnd = endOfISOWeek(this.currentDate);

    const formatDate = (date: Date) => {
      const month = date.toLocaleDateString('en-US', {month: 'short'});
      const day = date.getDate();
      return `${month} ${day}`;
    };

    return `${formatDate(weekStart)} - ${formatDate(weekEnd)}`;
  }

  private processSessionData(sessions: ReadingSession[]): void {
    const dayMap = new Map<number, ReadingSession[]>();

    sessions.forEach(session => {
      const sessionStart = new Date(session.startTime);
      const sessionEnd = new Date(session.endTime);

      if (sessionStart.getDate() === sessionEnd.getDate()) {
        const dayOfWeek = sessionStart.getDay();
        if (!dayMap.has(dayOfWeek)) {
          dayMap.set(dayOfWeek, []);
        }
        dayMap.get(dayOfWeek)!.push(session);
      } else {
        let currentStart = new Date(sessionStart);

        while (currentStart < sessionEnd) {
          const dayOfWeek = currentStart.getDay();
          const endOfDay = new Date(currentStart);
          endOfDay.setHours(23, 59, 59, 999);

          const segmentEnd = sessionEnd < endOfDay ? sessionEnd : endOfDay;
          const segmentDuration = Math.floor((segmentEnd.getTime() - currentStart.getTime()) / (1000 * 60));

          if (!dayMap.has(dayOfWeek)) {
            dayMap.set(dayOfWeek, []);
          }

          dayMap.get(dayOfWeek)!.push({
            startTime: new Date(currentStart),
            endTime: new Date(segmentEnd),
            duration: segmentDuration,
            bookTitle: session.bookTitle,
            bookId: session.bookId,
            bookType: session.bookType
          });

          currentStart = new Date(segmentEnd);
          currentStart.setDate(currentStart.getDate() + 1);
          currentStart.setHours(0, 0, 0, 0);
        }
      }
    });

    this.timelineData = [];
    const displayOrder = [1, 2, 3, 4, 5, 6, 0];
    for (let i = 0; i < 7; i++) {
      const dayOfWeek = displayOrder[i];
      const sessionsForDay = dayMap.get(dayOfWeek) || [];
      const timelineSessions = this.layoutSessionsForDay(sessionsForDay);

      this.timelineData.push({
        day: this.daysOfWeek[i],
        dayOfWeek: dayOfWeek,
        sessions: timelineSessions
      });
    }
  }

  private layoutSessionsForDay(sessions: ReadingSession[]): TimelineSession[] {
    if (sessions.length === 0) {
      return [];
    }

    sessions.sort((a, b) => {
      if (a.startTime.getTime() !== b.startTime.getTime()) {
        return a.startTime.getTime() - b.startTime.getTime();
      }
      return b.endTime.getTime() - a.endTime.getTime();
    });

    const tracks: ReadingSession[][] = [];

    sessions.forEach(session => {
      let placed = false;
      for (let i = 0; i < tracks.length; i++) {
        const lastSessionInTrack = tracks[i][tracks[i].length - 1];
        if (session.startTime >= lastSessionInTrack.endTime) {
          tracks[i].push(session);
          placed = true;
          break;
        }
      }
      if (!placed) {
        tracks.push([session]);
      }
    });

    const totalLevels = tracks.length;
    const timelineSessions: TimelineSession[] = [];

    tracks.forEach((track, level) => {
      track.forEach(session => {
        timelineSessions.push(this.convertToTimelineSession(session, level, totalLevels));
      });
    });

    return timelineSessions;
  }

  private convertToTimelineSession(session: ReadingSession, level: number, totalLevels: number): TimelineSession {
    const startHour = session.startTime.getHours();
    const startMinute = session.startTime.getMinutes();
    const endHour = session.endTime.getHours();
    const endMinute = session.endTime.getMinutes();

    const startDecimal = startHour + startMinute / 60;
    const endDecimal = endHour + endMinute / 60;

    const left = (startDecimal / 24) * 100;
    let width = ((endDecimal - startDecimal) / 24) * 100;

    if (width < 0.5) {
      width = 0.5;
    }

    return {
      startHour,
      startMinute,
      endHour,
      endMinute,
      duration: session.duration,
      left,
      width,
      bookTitle: session.bookTitle,
      bookId: session.bookId,
      bookType: session.bookType,
      level,
      totalLevels
    };
  }

  public formatTime(hour: number, minute: number): string {
    const displayHour = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour;
    const period = hour < 12 ? 'AM' : 'PM';
    const displayMinute = minute.toString().padStart(2, '0');
    return `${displayHour}:${displayMinute} ${period}`;
  }

  public formatDuration(minutes: number): string {
    const totalSeconds = Math.round(minutes * 60);
    const hours = Math.floor(totalSeconds / 3600);
    const mins = Math.floor((totalSeconds % 3600) / 60);
    const secs = totalSeconds % 60;

    const parts: string[] = [];
    if (hours) parts.push(`${hours}h`);
    if (mins || hours) parts.push(`${mins}m`);
    parts.push(`${secs}s`);

    return parts.join(' ');
  }

  public formatDurationCompact(minutes: number): string {
    const totalSeconds = Math.round(minutes * 60);
    const hours = Math.floor(totalSeconds / 3600);
    const mins = Math.floor((totalSeconds % 3600) / 60);
    const secs = totalSeconds % 60;

    if (hours > 0) return `${hours}h${mins > 0 ? mins + 'm' : ''}`;
    if (mins > 0) return `${mins}m${secs > 0 ? secs + 's' : ''}`;
    return `${secs}s`;
  }

  public isDurationGreaterThanOneHour(minutes: number): boolean {
    return minutes >= 60;
  }

  public getCoverUrl(bookId: number): string {
    return this.urlHelperService.getThumbnailUrl1(bookId);
  }

  public getTooltipContent(session: TimelineSession): string {
    return `
      <div class="session-tooltip-content">
        <div class="session-tooltip-cover">
          <img src="${this.getCoverUrl(session.bookId)}" alt="Book Cover">
        </div>
        <div class="session-tooltip-details">
          <div class="session-tooltip-header">
            <i class="pi pi-book"></i>
            <span class="session-tooltip-title">${session.bookTitle || 'Reading Session'}</span>
          </div>
          <div class="session-tooltip-divider"></div>
          <div class="session-tooltip-body">
            <div class="session-tooltip-row">
              <i class="pi pi-clock"></i>
              <span class="session-tooltip-label">Time:</span>
              <span class="session-tooltip-value">
                ${this.formatTime(session.startHour, session.startMinute)} - ${this.formatTime(session.endHour, session.endMinute)}
              </span>
            </div>
            <div class="session-tooltip-row">
              <i class="pi pi-hourglass"></i>
              <span class="session-tooltip-label">Duration:</span>
              <span class="session-tooltip-value">${this.formatDuration(session.duration)}</span>
            </div>
            <div class="session-tooltip-row">
              <i class="pi pi-file"></i>
              <span class="session-tooltip-label">Format:</span>
              <span class="session-tooltip-value">${session.bookType || 'Unknown'}</span>
            </div>
          </div>
        </div>
      </div>
    `;
  }
}
