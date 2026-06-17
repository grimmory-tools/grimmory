import {DOCUMENT} from '@angular/common';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {AppUiFontService} from './app-ui-font.service';

describe('AppUiFontService', () => {
  let service: AppUiFontService;
  let document: Document;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    document = TestBed.inject(DOCUMENT);
  });

  afterEach(() => {
    delete document.documentElement.dataset['uiFont'];
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('applies an accessible UI font to the document root', () => {
    service = TestBed.inject(AppUiFontService);

    service.applyUiFont('atkinson');

    expect(service.uiFont()).toBe('atkinson');
    expect(document.documentElement.dataset['uiFont']).toBe('atkinson');
    expect(localStorage.getItem('appUiFont')).toBe('atkinson');
  });

  it('falls back to the default UI font for unsupported values', () => {
    service = TestBed.inject(AppUiFontService);

    service.applyUiFont('unsupported');

    expect(service.uiFont()).toBe('default');
    expect(document.documentElement.dataset['uiFont']).toBeUndefined();
    expect(localStorage.getItem('appUiFont')).toBe('default');
  });

  it('loads a stored accessible UI font on creation', () => {
    localStorage.setItem('appUiFont', 'atkinson');

    service = TestBed.inject(AppUiFontService);

    expect(service.uiFont()).toBe('atkinson');
    expect(document.documentElement.dataset['uiFont']).toBe('atkinson');
    expect(localStorage.getItem('appUiFont')).toBe('atkinson');
  });

  it('normalizes an unsupported stored UI font on creation', () => {
    localStorage.setItem('appUiFont', 'unsupported');

    service = TestBed.inject(AppUiFontService);

    expect(service.uiFont()).toBe('default');
    expect(document.documentElement.dataset['uiFont']).toBeUndefined();
    expect(localStorage.getItem('appUiFont')).toBe('default');
  });
});
