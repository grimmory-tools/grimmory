import {ComponentFixture, TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it} from 'vitest';

import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {bookSortOptions} from './book-browse-sort';
import {BookBrowseToolbarComponent} from './book-browse-toolbar.component';
import {LibraryShelfMenuService} from '../service/library-shelf-menu.service';

describe('BookBrowseToolbarComponent', () => {
  let fixture: ComponentFixture<BookBrowseToolbarComponent>;
  const host = (): HTMLElement => fixture.nativeElement as HTMLElement;


  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BookBrowseToolbarComponent, getTranslocoModule()],
      providers: [
        {provide: LibraryShelfMenuService, useValue: {}},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BookBrowseToolbarComponent);
    fixture.componentRef.setInput(
      'sortOptions',
      bookSortOptions(['title', '-title', 'pageCount', '-pageCount']),
    );
    fixture.componentRef.setInput('sortTerms', [{key: 'title', direction: 'asc'}]);
    fixture.componentRef.setInput('viewMode', 'grid');
    fixture.componentRef.setInput('columnOptions', []);
    fixture.componentRef.setInput('densitySmallerDisabled', false);
    fixture.componentRef.setInput('densityLargerDisabled', false);
    fixture.componentRef.setInput('filtersOpen', false);
    fixture.componentRef.setInput('actionTarget', null);
    await fixture.whenStable();
  });

  it('routes the direction toggle through sortDirectionChange, not sortChange', async () => {
    const sortChanges: unknown[] = [];
    const directionChanges: unknown[] = [];
    fixture.componentInstance.sortChange.subscribe(change => sortChanges.push(change));
    fixture.componentInstance.sortDirectionChange.subscribe(change => directionChanges.push(change));
    await fixture.whenStable();

    buttonByLabel('Sort Descending').click();

    expect(sortChanges).toEqual([]);
    expect(directionChanges).toEqual([{key: 'title', direction: 'desc'}]);
  });

  it('marks no simple option active during multi-sort and treats picks as fresh sorts', async () => {
    fixture.componentRef.setInput('sortTerms', [
      {key: 'title', direction: 'asc'},
      {key: 'pageCount', direction: 'desc'},
    ]);
    const changes: unknown[] = [];
    fixture.componentInstance.sortChange.subscribe(change => changes.push(change));
    await fixture.whenStable();

    buttonByText('Title +1').click();
    await fixture.whenStable();

    const titleItem = menuItemByText('Title');
    expect(titleItem.querySelector('svg')).toBeNull();
    expect(titleItem.textContent).toContain('1');

    titleItem.click();
    expect(changes).toEqual([{key: 'title', direction: 'asc'}]);
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
