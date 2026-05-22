import {TestBed} from '@angular/core/testing';
import {afterEach, describe, expect, it} from 'vitest';

import {LibraryImportProgressService, LibraryImportProgressState} from './library-import-progress.service';

describe('LibraryImportProgressService', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('tracks progress from start through completion and clear', () => {
    TestBed.configureTestingModule({providers: [LibraryImportProgressService]});
    const service = TestBed.inject(LibraryImportProgressService);
    let latest: LibraryImportProgressState | undefined;
    service.state$.subscribe(state => {
      latest = state;
    });

    service.start('Main Library', 2);
    service.attachLibrary(7);
    service.recordBookAdded('First');

    expect(latest).toEqual({
      active: true,
      libraryId: 7,
      libraryName: 'Main Library',
      expectedCount: 2,
      processedCount: 1,
      currentBookTitle: 'First',
      status: 'IN_PROGRESS',
    });

    service.fail();

    expect(latest?.status).toBe('ERROR');

    service.start('Main Library', 2);
    service.recordBookAdded('First');
    service.recordBookAdded('Second');

    expect(latest?.status).toBe('COMPLETED');

    service.clear();

    expect(latest?.active).toBe(false);
  });
});
