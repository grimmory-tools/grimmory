import {TestBed} from '@angular/core/testing';
import {afterEach, describe, expect, it} from 'vitest';

import {LibraryImportProgressService} from './library-import-progress.service';

describe('LibraryImportProgressService', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('tracks progress from start through completion and clear', () => {
    TestBed.configureTestingModule({providers: [LibraryImportProgressService]});
    const service = TestBed.inject(LibraryImportProgressService);

    service.start('Main Library', 2);
    service.attachLibrary(7);
    service.recordBookAdded('First');

    expect(service.state()).toEqual({
      active: true,
      libraryId: 7,
      libraryName: 'Main Library',
      expectedCount: 2,
      processedCount: 1,
      currentBookTitle: 'First',
      status: 'IN_PROGRESS',
    });

    service.fail();

    expect(service.state().status).toBe('ERROR');

    service.start('Main Library', 2);
    service.recordBookAdded('First');
    service.recordBookAdded('Second');

    expect(service.state().status).toBe('COMPLETED');

    service.clear();

    expect(service.hasActiveImport()).toBe(false);
  });
});
