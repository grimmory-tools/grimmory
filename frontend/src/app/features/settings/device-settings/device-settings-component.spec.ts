import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around the nested device-settings shell and
// child standalone panels so device preference composition can be asserted without the full
// settings page runtime.
describe.skip('DeviceSettingsComponent', () => {
  it('needs composition seams to verify KOReader, email, and send-to-device panel inclusion', () => {
    // TODO(seam): Cover child-panel composition after the shell template is isolated from the broader settings runtime and its lazy imports.
  });
});
