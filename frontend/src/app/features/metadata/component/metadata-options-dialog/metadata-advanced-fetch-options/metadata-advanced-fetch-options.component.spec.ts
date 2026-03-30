import {SimpleChange} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';

import {FieldOptions, MetadataRefreshOptions} from '../../../model/request/metadata-refresh-options.model';
import {MetadataAdvancedFetchOptionsComponent} from './metadata-advanced-fetch-options.component';

describe('MetadataAdvancedFetchOptionsComponent', () => {
  const add = vi.fn();
  const translate = vi.fn((key: string) => `translated:${key}`);

  beforeEach(() => {
    add.mockClear();
    translate.mockClear();

    TestBed.configureTestingModule({
      providers: [
        {provide: MessageService, useValue: {add}},
        {provide: TranslocoService, useValue: {translate}},
      ]
    });
  });

  function createComponent() {
    return TestBed.runInInjectionContext(() => new MetadataAdvancedFetchOptionsComponent());
  }

  function createOptions(): MetadataRefreshOptions {
    return {
      libraryId: null,
      refreshCovers: true,
      mergeCategories: true,
      reviewBeforeApply: true,
      replaceMode: 'REPLACE_ALL',
      fieldOptions: {
        title: {p1: 'Google', p2: null, p3: null, p4: undefined},
        goodreadsId: {p1: null, p2: null, p3: null, p4: null},
      } as unknown as FieldOptions,
      enabledFields: {
        title: true,
        goodreadsId: false,
      } as Record<keyof FieldOptions, boolean>,
    };
  }

  it('hydrates flags, field options, and enabled fields from input changes', () => {
    const component = createComponent();
    component.currentMetadataOptions = createOptions();

    component.ngOnChanges({
      currentMetadataOptions: new SimpleChange(null, component.currentMetadataOptions, true),
    });

    expect(component.refreshCovers).toBe(true);
    expect(component.mergeCategories).toBe(true);
    expect(component.reviewBeforeApply).toBe(true);
    expect(component.replaceMode).toBe('REPLACE_ALL');
    expect(component.fieldOptions.title).toEqual({p1: 'Google', p2: null, p3: null, p4: null});
    expect(component.fieldOptions.goodreadsId).toEqual({p1: null, p2: null, p3: null, p4: null});
    expect(component.enabledFields.title).toBe(true);
    expect(component.enabledFields.goodreadsId).toBe(false);
  });

  it('emits metadata options when all enabled non-provider fields have a provider', () => {
    const component = createComponent();
    const emit = vi.spyOn(component.metadataOptionsSubmitted, 'emit');

    for (const field of component.fields) {
      component.enabledFields[field] = false;
    }
    component.fieldOptions.title.p1 = 'Google';
    component.enabledFields.title = true;
    component.enabledFields.goodreadsId = true;
    component.fieldOptions.goodreadsId = {p1: null, p2: null, p3: null, p4: null};
    component.refreshCovers = true;
    component.mergeCategories = false;
    component.reviewBeforeApply = true;
    component.replaceMode = 'REPLACE_WHEN_PROVIDED';

    component.submit();

    expect(emit).toHaveBeenCalledWith(expect.objectContaining({
      refreshCovers: true,
      mergeCategories: false,
      reviewBeforeApply: true,
      replaceMode: 'REPLACE_WHEN_PROVIDED',
      fieldOptions: component.fieldOptions,
      enabledFields: component.enabledFields,
    }));
    expect(add).not.toHaveBeenCalled();
  });

  it('shows an error when an enabled non-provider field has no provider priority set', () => {
    const component = createComponent();
    component.enabledFields.title = true;
    component.fieldOptions.title = {p1: null, p2: null, p3: null, p4: null};

    component.submit();

    expect(add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'translated:metadata.advancedFetchOptions.toast.providerRequiredSummary',
      detail: 'translated:metadata.advancedFetchOptions.toast.providerRequiredDetail',
      life: 5000,
    });
  });

  it('applies bulk providers only to enabled non-provider fields and clears the selector', () => {
    const component = createComponent();
    component.enabledFields.title = true;
    component.enabledFields.subtitle = false;
    component.enabledFields.goodreadsId = true;
    component.bulkP2 = 'Amazon';

    component.setBulkProvider('p2', 'Google');

    expect(component.fieldOptions.title.p2).toBe('Google');
    expect(component.fieldOptions.subtitle.p2).toBeNull();
    expect(component.fieldOptions.goodreadsId.p2).toBeNull();
    expect(component.bulkP2).toBeNull();
  });

  it('resets field options, enabled fields, replace mode, and bulk selectors', () => {
    const component = createComponent();
    component.fieldOptions.title = {p1: 'Google', p2: 'Amazon', p3: null, p4: null};
    component.enabledFields.title = false;
    component.replaceMode = 'REPLACE_ALL';
    component.bulkP1 = 'Google';
    component.bulkP2 = 'Amazon';
    component.bulkP3 = 'GoodReads';
    component.bulkP4 = 'Hardcover';

    component.reset();

    expect(component.fieldOptions.title).toEqual({p1: null, p2: null, p3: null, p4: null});
    expect(component.enabledFields.title).toBe(true);
    expect(component.replaceMode).toBe('REPLACE_MISSING');
    expect(component.bulkP1).toBeNull();
    expect(component.bulkP2).toBeNull();
    expect(component.bulkP3).toBeNull();
    expect(component.bulkP4).toBeNull();
  });
});
