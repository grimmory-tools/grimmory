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
  return {value: label.toLowerCase().replace(/\s+/g, '-'), label, count: 3};
}

function authorGroup(count: number): BrowseFilterGroup {
  return {
    key: 'author',
    labelKey: 'browse.showAll',
    multiValued: false,
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
  const includeButtons = () => Array.from(root().querySelectorAll<HTMLButtonElement>('li button:first-child'));
  const excludeButtons = () => Array.from(root().querySelectorAll<HTMLButtonElement>('li button:last-child'));
  const labels = () => includeButtons().map(button => button.querySelector('span:nth-of-type(2)')!.textContent!.trim());

  beforeEach(() => {
    TestBed.configureTestingModule({imports: [HostComponent, getTranslocoModule()]});
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('includes from the row and excludes from the minus box, each clearing itself and replacing the other', () => {
    fixture.componentInstance.groups.set([{...authorGroup(3), picks: new Map([['author-1', 'included'], ['author-2', 'excluded']])}]);
    fixture.detectChanges();

    includeButtons()[0].click();
    excludeButtons()[0].click();
    includeButtons()[1].click();
    excludeButtons()[1].click();
    excludeButtons()[2].click();

    expect(fixture.componentInstance.toggles.map(toggle => toggle.state)).toEqual([null, 'excluded', 'included', null, 'excluded']);
    expect(excludeButtons()[1].getAttribute('aria-label')).toBe('Exclude Author 2');
  });

  it('commits file size ranges in KB from the unit the user picked', () => {
    fixture.componentInstance.groups.set([{
      key: 'file_size',
      labelKey: 'browse.showAll',
      multiValued: false,
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

    expect(labels()).toHaveLength(20);
    expect(labels().at(-1)).toBe('Author 20');
  });

  it('counts a ticked row below the fold as one of the eight', () => {
    fixture.componentInstance.groups.set([{...authorGroup(20), picks: new Map([['author-12', 'included']])}]);
    fixture.detectChanges();

    expect(labels()).toEqual(['Author 1', 'Author 2', 'Author 3', 'Author 4', 'Author 5', 'Author 6', 'Author 7', 'Author 12']);
  });

  it('holds the clicked row through later reorders until another row is clicked', () => {
    const reversed = authorGroup(20);
    reversed.values.reverse();
    const rotated = authorGroup(20);
    rotated.values.push(...rotated.values.splice(0, 10));

    includeButtons()[2].click();
    fixture.componentInstance.groups.set([reversed]);
    fixture.detectChanges();
    expect(fixture.componentInstance.toggles).toEqual([{key: 'author', value: 'author-3', state: 'included'}]);
    expect(labels()).toEqual([
      'Author 20', 'Author 19', 'Author 3', 'Author 18', 'Author 17', 'Author 16', 'Author 15', 'Author 14',
    ]);
    fixture.componentInstance.groups.set([rotated]);
    fixture.detectChanges();
    expect(labels()).toEqual([
      'Author 11', 'Author 12', 'Author 3', 'Author 13', 'Author 14', 'Author 15', 'Author 16', 'Author 17',
    ]);

    includeButtons()[0].click();
    fixture.componentInstance.groups.set([reversed]);
    fixture.detectChanges();
    expect(labels()).toEqual([
      'Author 11', 'Author 20', 'Author 19', 'Author 18', 'Author 17', 'Author 16', 'Author 15', 'Author 14',
    ]);
  });
});
