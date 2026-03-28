import 'zone.js';
import 'zone.js/testing';
import {TestBed} from '@angular/core/testing';
import {BrowserDynamicTestingModule, platformBrowserDynamicTesting} from '@angular/platform-browser-dynamic/testing';

declare global {
  var __ANGULAR_TESTBED_INITIALIZED__: boolean | undefined;
}

// Only initialize if not already initialized
if (!globalThis.__ANGULAR_TESTBED_INITIALIZED__) {
  globalThis.__ANGULAR_TESTBED_INITIALIZED__ = true;
  TestBed.initTestEnvironment(
    BrowserDynamicTestingModule,
    platformBrowserDynamicTesting(),
    {teardown: {destroyAfterEach: true}}
  );
}
