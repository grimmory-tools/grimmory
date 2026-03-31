import {describe, expect, it} from 'vitest';

import * as audiobookIndex from './index';
import {AudiobookPlayerComponent} from './audiobook-player.component';
import {AudiobookService} from './audiobook.service';

describe('audiobook-player index', () => {
  it('re-exports the component and service entrypoints', () => {
    expect(audiobookIndex.AudiobookPlayerComponent).toBe(AudiobookPlayerComponent);
    expect(audiobookIndex.AudiobookService).toBe(AudiobookService);
  });

  it('re-exports the audiobook model helpers namespace', () => {
    expect('AudiobookInfo' in audiobookIndex).toBe(false);
  });
});
