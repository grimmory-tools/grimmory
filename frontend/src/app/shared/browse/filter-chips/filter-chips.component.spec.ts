import {TestBed} from '@angular/core/testing';
import {describe, expect, it} from 'vitest';

import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {browseFilterChips, type BrowseFacetDefinitions} from '../facets';
import {BrowseFilterChipsComponent} from './filter-chips.component';

const definitions: BrowseFacetDefinitions<'has_photo'> = {
  order: ['has_photo'],
  isKey: (key): key is 'has_photo' => key === 'has_photo',
  field: () => ({labelKey: 'author.fields.hasPhoto', kind: 'flag'}),
};

describe('BrowseFilterChipsComponent', () => {
  it('labels a Metadata selection by its field and leaves other values plain', () => {
    TestBed.configureTestingModule({imports: [BrowseFilterChipsComponent, getTranslocoModule()]});
    const fixture = TestBed.createComponent(BrowseFilterChipsComponent);
    fixture.componentRef.setInput('chips', browseFilterChips([], definitions, {'-has_photo': ['true', 'false']}));
    fixture.detectChanges();

    const tags = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('app-tag'));
    expect(tags.map(tag => tag.textContent!.replace(/\s+/g, ''))).toEqual(['Metadata:Photo', 'Photo:false']);
  });
});
