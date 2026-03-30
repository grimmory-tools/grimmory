import {FormControl, FormGroup} from '@angular/forms';
import {describe, expect, it, vi} from 'vitest';

import {BookMetadata} from '../../features/book/model/book.model';
import {MetadataUtilsService} from './metadata-utils.service';

describe('MetadataUtilsService', () => {
  const service = new MetadataUtilsService();

  function createForm(): FormGroup {
    return new FormGroup({
      title: new FormControl(''),
      titleLocked: new FormControl(false),
      authors: new FormControl<string[]>([]),
      authorsLocked: new FormControl(false),
      seriesName: new FormControl('Existing Series'),
      seriesNameLocked: new FormControl(true),
      pageCount: new FormControl<number | null>(null),
      pageCountLocked: new FormControl(false),
      description: new FormControl(''),
      descriptionLocked: new FormControl(false),
    });
  }

  it('copies a non-empty fetched field into the form and marks it as copied', () => {
    const form = createForm();
    const copiedFields: Record<string, boolean> = {};
    const metadata = {title: 'Hyperion'} as BookMetadata;

    const copied = service.copyFieldToForm('title', metadata, form, copiedFields);

    expect(copied).toBe(true);
    expect(form.get('title')?.value).toBe('Hyperion');
    expect(copiedFields['title']).toBe(true);
  });

  it('does not copy empty fetched values into the form', () => {
    const form = createForm();
    const copiedFields: Record<string, boolean> = {};
    const metadata = {title: ''} as BookMetadata;

    const copied = service.copyFieldToForm('title', metadata, form, copiedFields);

    expect(copied).toBe(false);
    expect(form.get('title')?.value).toBe('');
    expect(copiedFields['title']).toBeUndefined();
  });

  it('copies only unlocked empty fields that have fetched values', () => {
    const form = createForm();
    const copyCallback = vi.fn();
    const metadata = {
      title: 'Hyperion',
      authors: ['Dan Simmons'],
      seriesName: 'Should Stay Locked',
      pageCount: null,
    } as BookMetadata;

    service.copyMissingFields(metadata, form, {}, copyCallback);

    expect(copyCallback).toHaveBeenCalledTimes(2);
    expect(copyCallback).toHaveBeenCalledWith('title');
    expect(copyCallback).toHaveBeenCalledWith('authors');
  });

  it('copies all unlocked non-empty fields except excluded ones', () => {
    const form = createForm();
    const copyCallback = vi.fn();
    const metadata = {
      title: 'Hyperion',
      authors: ['Dan Simmons'],
      seriesName: 'Locked Series',
      thumbnailUrl: 'https://example.test/cover.jpg',
    } as BookMetadata;

    service.copyAllFields(metadata, form, copyCallback);

    expect(copyCallback).toHaveBeenCalledTimes(2);
    expect(copyCallback).toHaveBeenCalledWith('title');
    expect(copyCallback).toHaveBeenCalledWith('authors');
  });

  it('detects empty values consistently', () => {
    expect(service.isValueEmpty(null)).toBe(true);
    expect(service.isValueEmpty(undefined)).toBe(true);
    expect(service.isValueEmpty('')).toBe(true);
    expect(service.isValueEmpty([])).toBe(true);
    expect(service.isValueEmpty(['value'])).toBe(false);
    expect(service.isValueEmpty('value')).toBe(false);
  });

  it('normalizes arrays for comparison without caring about order', () => {
    expect(service.areFieldsEqual(['b', 'a'], ['a', 'b'])).toBe(true);
    expect(service.areFieldsEqual('3', 3)).toBe(true);
    expect(service.areFieldsEqual(['a'], ['a', 'b'])).toBe(false);
  });

  it('tracks when form values differ from the original or fetched metadata', () => {
    const form = createForm();
    form.get('authors')?.setValue(['beta', 'alpha']);
    form.get('title')?.setValue('Edited Title');

    const original = {
      authors: ['alpha', 'beta'],
      title: 'Original Title',
    } as BookMetadata;
    const fetched = {
      authors: ['other'],
      title: 'Fetched Title',
    } as BookMetadata;

    expect(service.isValueChanged('authors', form, original)).toBe(false);
    expect(service.isValueChanged('title', form, original)).toBe(true);
    expect(service.isFetchedDifferent('authors', form, fetched)).toBe(true);
    expect(service.isFetchedDifferent('title', form, fetched)).toBe(true);
  });

  it('resets a field to the original value and clears copied and hovered flags', () => {
    const form = createForm();
    form.get('title')?.setValue('Edited Title');

    const copiedFields = {title: true};
    const hoveredFields = {title: true};
    const original = {title: 'Original Title'} as BookMetadata;

    service.resetField('title', form, original, copiedFields, hoveredFields);

    expect(form.get('title')?.value).toBe('Original Title');
    expect(copiedFields['title']).toBe(false);
    expect(hoveredFields['title']).toBe(false);
  });

  it('patches metadata arrays as sorted values and applies lock defaults', () => {
    const form = new FormGroup({
      authors: new FormControl<string[]>([]),
      authorsLocked: new FormControl(false),
      title: new FormControl(''),
      titleLocked: new FormControl(false),
    });
    const metadata = {
      authors: ['zeta', 'alpha'],
      authorsLocked: true,
      title: 'Patched Title',
      titleLocked: false,
    } as BookMetadata;

    service.patchMetadataToForm(metadata, form, [
      {controlName: 'authors', lockedKey: 'authorsLocked', type: 'array'},
      {controlName: 'title', lockedKey: 'titleLocked', type: 'string'},
    ]);

    expect(form.get('authors')?.value).toEqual(['alpha', 'zeta']);
    expect(form.get('authorsLocked')?.value).toBe(true);
    expect(form.get('title')?.value).toBe('Patched Title');
    expect(form.get('titleLocked')?.value).toBe(false);
  });
});
