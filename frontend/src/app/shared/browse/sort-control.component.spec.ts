import {ComponentFixture, TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../core/testing/transloco-testing';
import {DialogLauncherService} from '../services/dialog-launcher.service';
import {BrowseSortControlComponent} from './sort-control.component';
import {type BrowseSortField, type BrowseSortOption} from './sort';

type Key = 'title' | 'pageCount';

const FIELDS: Record<Key, BrowseSortField> = {
  title: {labelKey: 'book.fields.title', group: 'common', defaultDirection: 'asc', kind: 'alphabetical'},
  pageCount: {labelKey: 'book.fields.pageCount', group: 'common', defaultDirection: 'desc', kind: 'numeric'},
};
const OPTIONS: BrowseSortOption<Key>[] = (['title', 'pageCount'] as const).map(id => ({
  ...FIELDS[id],
  id,
  directions: ['asc', 'desc'],
}));

describe('BrowseSortControlComponent', () => {
  let fixture: ComponentFixture<BrowseSortControlComponent<Key>>;
  let openMultiSortDialog: ReturnType<typeof vi.fn>;
  const host = (): HTMLElement => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    openMultiSortDialog = vi.fn();
    await TestBed.configureTestingModule({
      imports: [BrowseSortControlComponent, getTranslocoModule()],
      providers: [{provide: DialogLauncherService, useValue: {openMultiSortDialog}}],
    }).compileComponents();

    fixture = TestBed.createComponent(BrowseSortControlComponent<Key>);
    fixture.componentRef.setInput('sortOptions', OPTIONS);
    fixture.componentRef.setInput('sortTerms', [{key: 'title', direction: 'asc'}]);
    fixture.componentRef.setInput('sortField', (key: Key) => FIELDS[key]);
    await fixture.whenStable();
  });

  it('flips only the primary term on a direction toggle and keeps the rest', async () => {
    fixture.componentRef.setInput('sortTerms', [
      {key: 'title', direction: 'asc'},
      {key: 'pageCount', direction: 'desc'},
    ]);
    const changes: unknown[] = [];
    fixture.componentInstance.sortTermsChange.subscribe(change => changes.push(change));
    await fixture.whenStable();

    buttonByLabel('Sort Descending').click();

    expect(changes).toEqual([[{key: 'title', direction: 'desc'}, {key: 'pageCount', direction: 'desc'}]]);
  });

  it('marks no simple option active during multi-sort and treats picks as fresh sorts', async () => {
    fixture.componentRef.setInput('sortTerms', [
      {key: 'title', direction: 'asc'},
      {key: 'pageCount', direction: 'desc'},
    ]);
    const changes: unknown[] = [];
    fixture.componentInstance.sortTermsChange.subscribe(change => changes.push(change));
    await fixture.whenStable();

    buttonByText('Title +1').click();
    await fixture.whenStable();

    const titleItem = menuItemByText('Title');
    expect(titleItem.querySelector('svg')).toBeNull();
    expect(titleItem.textContent).toContain('1');

    titleItem.click();
    expect(changes).toEqual([[{key: 'title', direction: 'asc'}]]);
  });

  it('applies the multi-sort result before asking to save it as the default', async () => {
    const terms = [{key: 'pageCount', direction: 'desc'}, {key: 'title', direction: 'asc'}];
    openMultiSortDialog.mockResolvedValue({onClose: of({terms, saveAsDefault: true})});
    fixture.componentRef.setInput('saveDefaultFor', 'All Books');
    const events: unknown[] = [];
    fixture.componentInstance.sortTermsChange.subscribe(change => events.push(['apply', change]));
    fixture.componentInstance.sortDefaultSaved.subscribe(change => events.push(['save', change]));
    await fixture.whenStable();

    buttonByText('Title').click();
    await fixture.whenStable();
    menuItemByText('Multi-Sort').click();
    await fixture.whenStable();

    expect(openMultiSortDialog).toHaveBeenCalledWith(expect.objectContaining({saveDefaultFor: 'All Books'}));
    expect(events).toEqual([['apply', terms], ['save', terms]]);
  });

  function optionalButtonByLabel(label: string): HTMLButtonElement | null {
    return host().querySelector(`button[aria-label="${label}"]`) as HTMLButtonElement | null;
  }

  function buttonByLabel(label: string): HTMLButtonElement {
    const button = optionalButtonByLabel(label);
    if (!button) throw new Error(`Could not find button labelled "${label}"`);
    return button;
  }

  function buttonByText(text: string): HTMLButtonElement {
    const button = buttonByTextOrNull(text);
    if (!button) throw new Error(`Could not find button with text "${text}"`);
    return button;
  }

  function buttonByTextOrNull(text: string): HTMLButtonElement | null {
    const buttons = Array.from(host().querySelectorAll('button'));
    return buttons.find(candidate => candidate.textContent.trim() === text) ?? null;
  }

  function menuItemByText(text: string): HTMLElement {
    const items = Array.from(document.querySelectorAll('.cdk-overlay-container app-menu-item')) as HTMLElement[];
    const item = items.find(candidate => candidate.textContent.includes(text));
    if (!item) throw new Error(`Could not find menu item "${text}"`);
    return item;
  }
});
