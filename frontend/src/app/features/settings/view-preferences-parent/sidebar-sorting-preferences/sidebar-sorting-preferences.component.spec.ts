import {signal, WritableSignal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {MessageService} from 'primeng/api';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {LayoutService} from '../../../../shared/layout/layout.service';
import {SortPref} from '../../../../shared/layout/sidebar-sort-preferences';
import {SidebarSortingPreferencesComponent} from './sidebar-sorting-preferences.component';

describe('SidebarSortingPreferencesComponent', () => {
  let fixture: ComponentFixture<SidebarSortingPreferencesComponent>;
  let setLibrarySort: ReturnType<typeof vi.fn>;
  let messageService: {add: ReturnType<typeof vi.fn>};
  let librarySort: WritableSignal<SortPref>;
  let shelfSort: WritableSignal<SortPref>;
  let magicShelfSort: WritableSignal<SortPref>;

  beforeEach(() => {
    librarySort = signal<SortPref>({field: 'name', order: 'asc'});
    shelfSort = signal<SortPref>({field: 'id', order: 'desc'});
    magicShelfSort = signal<SortPref>({field: 'name', order: 'desc'});
    setLibrarySort = vi.fn((value: SortPref) => librarySort.set(value));
    messageService = {add: vi.fn()};

    TestBed.configureTestingModule({
      imports: [SidebarSortingPreferencesComponent, getTranslocoModule({translocoConfig: {reRenderOnLangChange: false}})],
      providers: [
        {
          provide: LayoutService,
          useValue: {
            librarySort,
            shelfSort,
            magicShelfSort,
            setLibrarySort,
            setShelfSort: vi.fn((value: SortPref) => shelfSort.set(value)),
            setMagicShelfSort: vi.fn((value: SortPref) => magicShelfSort.set(value)),
          },
        },
        {provide: MessageService, useValue: messageService},
      ],
    });

    fixture = TestBed.createComponent(SidebarSortingPreferencesComponent);
    TestBed.flushEffects();
    fixture.detectChanges();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('hydrates sidebar sorting selections from the current user', () => {
    const component = fixture.componentInstance;

    expect(component.selectedLibrarySorting()).toEqual({field: 'name', order: 'asc'});
    expect(component.selectedShelfSorting()).toEqual({field: 'id', order: 'desc'});
    expect(component.selectedMagicShelfSorting()).toEqual({field: 'name', order: 'desc'});
    expect(component.sortingOptions()).not.toHaveLength(0);
  });

  it('persists library sorting changes and shows a success toast', () => {
    const component = fixture.componentInstance;

    component.onLibrarySortingChange({field: 'id', order: 'asc'});

    expect(setLibrarySort).toHaveBeenCalledWith({field: 'id', order: 'asc'});
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
  });
});
