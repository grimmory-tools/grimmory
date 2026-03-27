import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {UserService} from '../user-management/user.service';
import {ReaderPreferences} from './reader-preferences.component';

describe('ReaderPreferences', () => {
  beforeEach(() => {
    const currentUser = signal({
      userSettings: {
        theme: 'sepia',
      },
      permissions: {
        admin: false,
        canManageFonts: true,
      },
    });

    TestBed.configureTestingModule({
      providers: [
        {provide: UserService, useValue: {currentUser}},
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('hydrates user settings and font management permission from the current user signal', () => {
    const component = TestBed.runInInjectionContext(() => new ReaderPreferences());
    TestBed.flushEffects();

    expect(component.userSettings).toEqual({theme: 'sepia'});
    expect(component.hasFontManagementPermission).toBe(true);
  });
});
