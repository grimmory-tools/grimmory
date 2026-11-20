import {ComponentFixture, TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it} from 'vitest';

import {getTranslocoModule} from '../../../../../../core/testing/transloco-testing';
import {type StatsWeekSession} from '../../../../data/stats-response.models';
import {STATS_CARD_TESTING_PROVIDER} from '../../../shared/stats-chart-testing';
import {ReadingSessionTimelineComponent} from './reading-session-timeline.component';

function session(title: string, start: Date, minutes: number): StatsWeekSession {
  return {bookId: 12, title, format: 'EPUB', start: start.toISOString(), end: new Date(start.getTime() + minutes * 60_000).toISOString()};
}

describe('ReadingSessionTimelineComponent', () => {
  let fixture: ComponentFixture<ReadingSessionTimelineComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReadingSessionTimelineComponent, getTranslocoModule({preloadLangs: true})],
      providers: [STATS_CARD_TESTING_PROVIDER],
    }).compileComponents();

    fixture = TestBed.createComponent(ReadingSessionTimelineComponent);
  });

  it('splits sessions at local midnight and clips them to the week', () => {
    fixture.componentRef.setInput('data', {
      year: 2026,
      week: 2,
      sessions: {
        rows: [
          session('Before', new Date(2026, 0, 4, 23), 90),
          session('Across', new Date(2026, 0, 7, 22), 180),
          session('After', new Date(2026, 0, 11, 23, 30), 60),
        ],
        truncated: false,
      },
    });

    expect(fixture.componentInstance.segments().map(({bookTitle, dayIndex, startHour, endHour, durationMinutes}) =>
      [bookTitle, dayIndex, startHour, endHour, durationMinutes])).toEqual([
      ['Before', 0, 0, 0.5, 30],
      ['Across', 2, 22, 24, 120],
      ['Across', 3, 0, 1, 60],
      ['After', 6, 23.5, 24, 30],
    ]);
  });

  it('starts the hours at the earliest session when the quiet hours span midnight', async () => {
    fixture.componentRef.setInput('data', {
      year: 2026,
      week: 2,
      sessions: {
        rows: [session('Morning', new Date(2026, 0, 9, 9, 50), 30), session('Afternoon', new Date(2026, 0, 9, 14), 60)],
        truncated: false,
      },
    });
    await fixture.whenStable();

    const labels = [...(fixture.nativeElement as HTMLElement).querySelectorAll('svg text')].map((text) => text.textContent);
    expect(labels.filter((label) => /[AP]M$/.test(label ?? ''))).toEqual(['9 AM', '10 AM', '11 AM', '12 PM', '1 PM', '2 PM', '3 PM']);
  });
});
