import {ComponentFixture, TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it} from 'vitest';

import {getTranslocoModule} from '../../../../../../core/testing/transloco-testing';
import {STATS_CARD_TESTING_PROVIDER} from '../../../shared/stats-chart-testing';
import {ReadingSessionActivityComponent} from './reading-session-activity.component';

describe('ReadingSessionActivityComponent', () => {
  let fixture: ComponentFixture<ReadingSessionActivityComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReadingSessionActivityComponent, getTranslocoModule({preloadLangs: true})],
      providers: [STATS_CARD_TESTING_PROVIDER],
    }).compileComponents();

    fixture = TestBed.createComponent(ReadingSessionActivityComponent);
  });

  it('lays every day of the year out in Monday-first weeks', () => {
    fixture.componentRef.setInput('data', {
      year: 2026,
      rows: [{day: '2026-01-05', sessions: 3}],
      streaks: {current: 1, longest: 8, readingDays: 10},
    });
    const cells = fixture.componentInstance.cells();

    expect(cells).toHaveLength(365);
    expect(cells[0]).toEqual({week: 0, weekday: 3, date: '2026-01-01', count: 0});
    expect(cells[4]).toEqual({week: 1, weekday: 0, date: '2026-01-05', count: 3});
    expect(cells.at(-1)).toEqual({week: 52, weekday: 3, date: '2026-12-31', count: 0});
  });
});
