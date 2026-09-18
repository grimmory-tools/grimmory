import {TestBed} from '@angular/core/testing';
import {BehaviorSubject} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {TranslocoService} from '@jsverse/transloco';
import {LanguageResolverService} from './language-resolver.service';

describe('LanguageResolverService', () => {
  const langChanges$ = new BehaviorSubject<string>('en');

  beforeEach(() => {
    langChanges$.next('en');

    TestBed.configureTestingModule({
      providers: [
        {provide: TranslocoService, useValue: {langChanges$, getActiveLang: () => langChanges$.getValue()}},
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  function createService(): LanguageResolverService {
    return TestBed.inject(LanguageResolverService);
  }

  it('returns null for missing or blank values', () => {
    const service = createService();

    expect(service.resolve(null)).toBeNull();
    expect(service.resolve(undefined)).toBeNull();
    expect(service.resolve('')).toBeNull();
    expect(service.resolve('   ')).toBeNull();
  });

  it('resolves a plain base language', () => {
    const service = createService();

    expect(service.resolve('en')).toEqual({
      raw: 'en',
      tag: 'en',
      base: 'en',
      languageName: 'English',
      regionName: null,
      displayName: 'English',
      recognized: true,
    });
  });

  it('canonicalizes casing and underscore separators and splits out the region name', () => {
    const service = createService();

    expect(service.resolve(' EN_ca ')).toEqual({
      raw: 'EN_ca',
      tag: 'en-CA',
      base: 'en',
      languageName: 'English',
      regionName: 'Canada',
      displayName: 'English (Canada)',
      recognized: true,
    });
  });

  it('folds ISO 639-2 codes and English language names into the base code', () => {
    const service = createService();

    expect(service.resolve('eng')?.tag).toBe('en');
    expect(service.resolve(' English ')?.tag).toBe('en');
    expect(service.resolve('spa')?.languageName).toBe('Spanish');
    expect(service.resolve('eng-CA')).toMatchObject({
      tag: 'en-CA',
      base: 'en',
      regionName: 'Canada',
    });
  });

  it('resolves script subtags into the secondary name', () => {
    const service = createService();

    expect(service.resolve('zh-hant')).toMatchObject({
      tag: 'zh-Hant',
      base: 'zh',
      languageName: 'Chinese',
      regionName: 'Traditional',
    });
  });

  it('falls back to the capitalized raw value for unrecognized languages', () => {
    const service = createService();

    expect(service.resolve('klingon')).toEqual({
      raw: 'klingon',
      tag: 'klingon',
      base: 'klingon',
      languageName: 'Klingon',
      regionName: null,
      displayName: 'Klingon',
      recognized: false,
    });
  });

  it('keeps structurally invalid values as their own group without throwing', () => {
    const service = createService();

    expect(service.resolve('not a language!')).toMatchObject({
      tag: 'not a language!',
      base: 'not a language!',
      languageName: 'Not a language!',
      recognized: false,
    });
  });

  it('localizes names to the active UI locale, capitalized, and reacts to locale changes', () => {
    const service = createService();

    expect(service.resolve('en-CA')?.languageName).toBe('English');

    langChanges$.next('fr');

    expect(service.resolve('en-CA')).toMatchObject({
      languageName: 'Anglais',
      regionName: 'Canada',
      displayName: 'Anglais (Canada)',
    });
  });

  it('falls back to English display names when the active locale is invalid', () => {
    const service = createService();

    langChanges$.next('not-a-real-locale-!!');

    expect(service.resolve('en-CA')?.languageName).toBe('English');
  });
});
