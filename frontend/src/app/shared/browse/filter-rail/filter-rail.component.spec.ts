import {Component, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {BrowseFilterRailComponent} from './filter-rail.component';
import {
  type BrowseFilterGroup,
  type BrowseFilterRangeCommit,
  type BrowseFilterToggle,
  type BrowseFilterValue,
} from '../facets';

function railValue(label: string): BrowseFilterValue {
  return {value: label.toLowerCase().replace(/\s+/g, '-'), label, count: 3, state: null};
}

function authorGroup(count: number): BrowseFilterGroup {
  return {
    key: 'author',
    labelKey: 'browse.showAll',
    values: Array.from({length: count}, (_, index) => railValue(`Author ${index + 1}`)),
  };
}

@Component({
  imports: [BrowseFilterRailComponent],
  template: `
    <app-browse-filter-rail
      [groups]="groups()"
      [openKeys]="openKeys"
      [searchTerms]="searchTerms"
      (toggleValue)="toggles.push($event)"
      (commitRange)="commits.push($event)" />
  `,
})
class HostComponent {
  readonly groups = signal<readonly BrowseFilterGroup[]>([authorGroup(20)]);
  readonly openKeys: ReadonlySet<string> = new Set(['author', 'file_size']);
  readonly searchTerms: Readonly<Record<string, string>> = {};
  readonly toggles: BrowseFilterToggle[] = [];
  readonly commits: BrowseFilterRangeCommit[] = [];
}

describe('BrowseFilterRailComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  const root = (): HTMLElement => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({imports: [HostComponent, getTranslocoModule()]});
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('commits file size ranges in KB from the unit the user picked', () => {
    fixture.componentInstance.groups.set([{
      key: 'file_size',
      labelKey: 'browse.showAll',
      values: [],
      range: {min: null, max: null, boundsMin: 358, boundsMax: null, fileSize: true},
    }]);
    fixture.detectChanges();

    const inputs = root().querySelectorAll<HTMLInputElement>('app-number-input input');
    const selects = root().querySelectorAll<HTMLSelectElement>('select');
    selects[1].value = 'GB';
    selects[1].dispatchEvent(new Event('change'));
    fixture.detectChanges();
    inputs[0].value = '500';
    inputs[0].dispatchEvent(new Event('input'));
    inputs[1].value = '1.4';
    inputs[1].dispatchEvent(new Event('input'));
    inputs[1].dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', bubbles: true}));

    expect(fixture.componentInstance.commits).toEqual([{key: 'file_size', min: 500, max: 1468006}]);
  });

  it('searching a group shows every match, not just the first fold', () => {
    root().querySelector<HTMLButtonElement>('button[aria-label="Search…"]')!.click();
    fixture.detectChanges();
    const input = root().querySelector<HTMLInputElement>('[data-search-wrap] input')!;
    input.value = 'Author';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const labels = Array.from(root().querySelectorAll<HTMLElement>('button[aria-pressed]'))
      .map(button => button.querySelector('span:nth-of-type(2)')!.textContent!.trim());
    expect(labels).toHaveLength(20);
    expect(labels.at(-1)).toBe('Author 20');
  });

  it('counts a ticked row below the fold as one of the eight', () => {
    const group = authorGroup(20);
    group.values[11] = {...group.values[11], state: 'included'};
    fixture.componentInstance.groups.set([group]);
    fixture.detectChanges();

    const labels = Array.from(root().querySelectorAll<HTMLElement>('button[aria-pressed]'))
      .map(button => button.querySelector('span:nth-of-type(2)')!.textContent!.trim());
    expect(labels).toEqual(['Author 1', 'Author 2', 'Author 3', 'Author 4', 'Author 5', 'Author 6', 'Author 7', 'Author 12']);
  });

  it('keeps the clicked row at its on-screen slot when the list reorders', () => {
    root().querySelectorAll<HTMLButtonElement>('button[aria-pressed]')[2].click();
    const reordered = authorGroup(20);
    reordered.values.reverse();
    fixture.componentInstance.groups.set([reordered]);
    fixture.detectChanges();

    const labels = Array.from(root().querySelectorAll<HTMLElement>('button[aria-pressed]'))
      .map(button => button.querySelector('span:nth-of-type(2)')!.textContent!.trim());
    expect(fixture.componentInstance.toggles).toEqual([{key: 'author', value: 'author-3', state: 'included'}]);
    expect(labels).toEqual([
      'Author 20', 'Author 19', 'Author 3', 'Author 18', 'Author 17', 'Author 16', 'Author 15', 'Author 14',
    ]);
  });

  it('lets the clicked row move once a later change reorders the list again', () => {
    root().querySelectorAll<HTMLButtonElement>('button[aria-pressed]')[2].click();
    const reordered = authorGroup(20);
    reordered.values.reverse();
    fixture.componentInstance.groups.set([reordered]);
    fixture.detectChanges();
    fixture.componentInstance.groups.set([authorGroup(20)]);
    fixture.detectChanges();

    const labels = Array.from(root().querySelectorAll<HTMLElement>('button[aria-pressed]'))
      .map(button => button.querySelector('span:nth-of-type(2)')!.textContent!.trim());
    expect(labels).toEqual([
      'Author 1', 'Author 2', 'Author 3', 'Author 4', 'Author 5', 'Author 6', 'Author 7', 'Author 8',
    ]);
  });

  it('keeps a row clicked in the search results at its slot when the results reorder', () => {
    root().querySelector<HTMLButtonElement>('button[aria-label="Search…"]')!.click();
    fixture.detectChanges();
    const input = root().querySelector<HTMLInputElement>('[data-search-wrap] input')!;
    input.value = 'Author 1';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    root().querySelectorAll<HTMLButtonElement>('button[aria-pressed]')[2].click();
    const reordered = authorGroup(20);
    reordered.values.reverse();
    fixture.componentInstance.groups.set([reordered]);
    fixture.detectChanges();

    const labels = Array.from(root().querySelectorAll<HTMLElement>('button[aria-pressed]'))
      .map(button => button.querySelector('span:nth-of-type(2)')!.textContent!.trim());
    expect(fixture.componentInstance.toggles).toEqual([{key: 'author', value: 'author-11', state: 'included'}]);
    expect(labels).toEqual([
      'Author 19', 'Author 18', 'Author 11', 'Author 17', 'Author 16', 'Author 15', 'Author 14', 'Author 13',
      'Author 12', 'Author 10', 'Author 1',
    ]);
  });
});
