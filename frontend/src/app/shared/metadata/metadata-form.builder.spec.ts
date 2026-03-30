import {FormControl, FormGroup} from '@angular/forms';
import {describe, expect, it} from 'vitest';

import {MetadataFormBuilder} from './metadata-form.builder';

describe('MetadataFormBuilder', () => {
  const builder = new MetadataFormBuilder();

  it('builds controls with defaults for requested, audiobook, comic, and cover fields', () => {
    const form = builder.buildForm(true, [
      {label: 'Title', controlName: 'title', lockedKey: 'titleLocked', fetchedKey: 'title', type: 'string'},
      {label: 'Authors', controlName: 'authors', lockedKey: 'authorsLocked', fetchedKey: 'authors', type: 'array'},
      {label: 'Series #', controlName: 'seriesNumber', lockedKey: 'seriesNumberLocked', fetchedKey: 'seriesNumber', type: 'number'},
    ]);

    expect(form.get('title')?.value).toBe('');
    expect(form.get('authors')?.value).toEqual([]);
    expect(form.get('seriesNumber')?.value).toBeNull();
    expect(form.get('titleLocked')?.value).toBe(false);
    expect(form.get('narrator')?.value).toBe('');
    expect(form.get('abridged')?.value).toBeNull();
    expect(form.get('comicIssueNumber')?.value).toBe('');
    expect(form.get('comicCharacters')?.value).toEqual([]);
    expect(form.get('thumbnailUrl')?.value).toBe('');
    expect(form.get('coverLocked')?.value).toBe(false);
    expect(form.get('audiobookCoverLocked')?.value).toBe(false);
  });

  it('omits locked controls when the caller disables them', () => {
    const form = builder.buildForm(false, [
      {label: 'Title', controlName: 'title', lockedKey: 'titleLocked', fetchedKey: 'title', type: 'string'},
    ]);

    expect(form.get('title')).toBeTruthy();
    expect(form.get('titleLocked')).toBeNull();
    expect(form.get('coverLocked')).toBeNull();
    expect(form.get('audiobookCoverLocked')).toBeNull();
  });

  it('applies lock states after re-enabling unlocked controls first', () => {
    const form = new FormGroup({
      title: new FormControl('Title'),
      titleLocked: new FormControl(false),
      authors: new FormControl<string[]>(['A']),
      authorsLocked: new FormControl(false),
      narrator: new FormControl('Narrator'),
      narratorLocked: new FormControl(false),
      comicIssueNumber: new FormControl('7'),
      comicIssueNumberLocked: new FormControl(false),
    });

    form.get('title')?.disable();
    form.get('authors')?.disable();

    builder.applyLockStates(form, {
      titleLocked: true,
      narratorLocked: true,
      comicIssueNumberLocked: false,
    });

    expect(form.get('title')?.disabled).toBe(true);
    expect(form.get('authors')?.enabled).toBe(true);
    expect(form.get('narrator')?.disabled).toBe(true);
    expect(form.get('comicIssueNumber')?.enabled).toBe(true);
  });

  it('toggles all locked fields and matching control enabled state together', () => {
    const form = new FormGroup({
      title: new FormControl('Title'),
      titleLocked: new FormControl(false),
      authors: new FormControl<string[]>(['A']),
      authorsLocked: new FormControl(false),
    });

    builder.setAllFieldsLocked(form, true);
    expect(form.get('titleLocked')?.value).toBe(true);
    expect(form.get('authorsLocked')?.value).toBe(true);
    expect(form.get('title')?.disabled).toBe(true);
    expect(form.get('authors')?.disabled).toBe(true);

    builder.setAllFieldsLocked(form, false);
    expect(form.get('titleLocked')?.value).toBe(false);
    expect(form.get('authorsLocked')?.value).toBe(false);
    expect(form.get('title')?.enabled).toBe(true);
    expect(form.get('authors')?.enabled).toBe(true);
  });
});
