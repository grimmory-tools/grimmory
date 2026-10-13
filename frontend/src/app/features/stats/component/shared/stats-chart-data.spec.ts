import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {runningActivity} from './stats-chart-data';

describe('runningActivity', () => {
  beforeEach(() => {
    vi.useFakeTimers({toFake: ['Date']});
    vi.setSystemTime(new Date(2026, 4, 20, 12));
  });

  afterEach(() => vi.useRealTimers());

  const rows = [
    {month: '2025-12', added: 0, finished: 2, finishedPages: 500, backlogChange: 0},
    {month: '2026-01', added: 3, finished: 0, finishedPages: 0, backlogChange: 3},
    {month: '2026-03', added: 2, finished: 1, finishedPages: 300, backlogChange: -1},
  ];

  it('runs every month from the first to the current one, with the backlog from each month\'s change', () => {
    expect(runningActivity(rows)).toMatchObject([
      {month: '2025-12', added: 0, finished: 2, totalAdded: 0, totalFinished: 2, backlog: 0},
      {month: '2026-01', added: 3, finished: 0, totalAdded: 3, totalFinished: 2, backlog: 3},
      {month: '2026-02', added: 0, finished: 0, totalAdded: 3, totalFinished: 2, backlog: 3},
      {month: '2026-03', added: 2, finished: 1, totalAdded: 5, totalFinished: 3, backlog: 2},
      {month: '2026-04', added: 0, finished: 0, totalAdded: 5, totalFinished: 3, backlog: 2},
      {month: '2026-05', added: 0, finished: 0, totalAdded: 5, totalFinished: 3, backlog: 2},
    ]);
  });
});
