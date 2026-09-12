import {Component, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {BrowseFilterRailComponent} from './filter-rail.component';
import {type BrowseFilterGroup, type BrowseFilterRangeCommit, type BrowseFilterValue} from '../facets';

function railValue(label: string): BrowseFilterValue {
  return {value: label.toLowerCase().replace(/\s+/g, '-'), label, count: 3, selected: false};
}

function authorGroup(count: number): BrowseFilterGroup {
  return {
    key: 'author',
    labelKey: 'browse.showAll',
    defaultOpen: true,
    values: Array.from({length: count}, (_, index) => railValue(`Author ${index + 1}`)),
  };
}

@Component({
  imports: [BrowseFilterRailComponent],
  template: `<app-browse-filter-rail [groups]="groups()" (commitRange)="commits.push($event)" />`,
})
class HostComponent {
  readonly groups = signal<readonly BrowseFilterGroup[]>([authorGroup(20)]);
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
      defaultOpen: true,
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
});
