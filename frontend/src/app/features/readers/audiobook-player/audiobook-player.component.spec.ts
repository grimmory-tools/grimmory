import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around the HTMLAudioElement lifecycle,
// sleep-timer intervals, route-driven loading, and bookmark/session browser behavior so the
// player can be tested honestly without booting the full audio runtime in Vitest.
describe.skip('AudiobookPlayerComponent', () => {
  it('needs an audio-element seam to verify playback, seeking, buffering, and track switching', () => {
    // TODO(seam): Cover player controls once the audio element and timer workflow are abstracted behind testable adapters.
  });

  it('needs a session-and-bookmark seam to verify progress saving and bookmark actions without browser media side effects', () => {
    // TODO(seam): Cover session lifecycle, bookmark menus, and route changes after extracting the runtime audio/session dependencies.
  });
});
