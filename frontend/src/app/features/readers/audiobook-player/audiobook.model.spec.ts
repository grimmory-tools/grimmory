import {describe, expect, expectTypeOf, it} from 'vitest';

import {
  AudiobookChapter,
  AudiobookInfo,
  AudiobookProgress,
  AudiobookTrack
} from './audiobook.model';

describe('audiobook.model', () => {
  it('supports audiobook metadata, chapters, and tracks', () => {
    const chapter: AudiobookChapter = {
      index: 1,
      title: 'Chapter 1',
      startTimeMs: 0,
      endTimeMs: 60000,
      durationMs: 60000
    };
    const track: AudiobookTrack = {
      index: 0,
      fileName: 'track-01.mp3',
      title: 'Track 1',
      durationMs: 60000,
      fileSizeBytes: 1024,
      cumulativeStartMs: 0
    };
    const info: AudiobookInfo = {
      bookId: 1,
      bookFileId: 2,
      durationMs: 60000,
      folderBased: false,
      chapters: [chapter],
      tracks: [track]
    };

    expect(info.chapters?.[0].title).toBe('Chapter 1');
    expect(info.tracks?.[0].fileName).toBe('track-01.mp3');
  });

  it('keeps audiobook progress numerically typed', () => {
    const progress: AudiobookProgress = {
      positionMs: 5000,
      trackIndex: 0,
      trackPositionMs: 5000,
      percentage: 8.3
    };

    expect(progress.percentage).toBeCloseTo(8.3);
    expectTypeOf(progress.trackIndex).toEqualTypeOf<number | undefined>();
  });
});
