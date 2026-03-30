export interface AudiobookInfo {
  bookId: number;
  bookFileId: number;
  title?: string;
  author?: string;
  narrator?: string;
  durationMs: number;
  bitrate?: number;
  codec?: string;
  sampleRate?: number;
  channels?: number;
  folderBased: boolean;
  chapters?: AudiobookChapter[];
  tracks?: AudiobookTrack[];
}

export interface AudiobookChapter {
  index: number;
  title: string;
  startTimeMs: number;
  endTimeMs: number;
  durationMs: number;
}

export interface AudiobookTrack {
  index: number;
  fileName: string;
  title: string;
  durationMs: number;
  fileSizeBytes: number;
  cumulativeStartMs: number;
}

export interface AudiobookProgress {
  positionMs: number;
  trackIndex?: number;
  trackPositionMs?: number;
  percentage: number;
}
