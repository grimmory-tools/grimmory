import {of, throwError} from 'rxjs';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {DynamicDialogRef} from 'primeng/dynamicdialog';

import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {DirectoryPickerComponent} from './directory-picker.component';
import {UtilityService} from './utility.service';

describe('DirectoryPickerComponent', () => {
  let fixture: ComponentFixture<DirectoryPickerComponent>;
  let component: DirectoryPickerComponent;
  let utilityService: {getFolders: ReturnType<typeof vi.fn>};
  let dialogRef: {close: ReturnType<typeof vi.fn>};

  beforeEach(async () => {
    utilityService = {
      getFolders: vi.fn(() => of(['/books', '/comics'])),
    };
    dialogRef = {
      close: vi.fn(),
    };

    vi.useFakeTimers();

    await TestBed.configureTestingModule({
      imports: [DirectoryPickerComponent, getTranslocoModule()],
      providers: [
        {provide: UtilityService, useValue: utilityService},
        {provide: DynamicDialogRef, useValue: dialogRef},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DirectoryPickerComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('loads the root folders on init and updates loading state after the delayed response', () => {
    component.ngOnInit();

    expect(utilityService.getFolders).toHaveBeenCalledWith('/');
    expect(component.isLoading()).toBe(true);

    vi.advanceTimersByTime(100);

    expect(component.paths).toEqual(['/books', '/comics']);
    expect(component.filteredPaths).toEqual(['/books', '/comics']);
    expect(component.isLoading()).toBe(false);
    expect(component.breadcrumbItems).toEqual([]);
  });

  it('builds breadcrumb items for nested paths', () => {
    component.updateBreadcrumb('/books/scifi/dune');

    expect(component.breadcrumbItems.map(item => item.label)).toEqual(['books', 'scifi', 'dune']);
  });

  it('filters visible paths using a case-insensitive search', () => {
    component.paths = ['/Books', '/Comics', '/Magazines'];
    component.searchQuery = 'co';

    component.onSearch();

    expect(component.filteredPaths).toEqual(['/Comics']);
  });

  it('tracks checkbox selection and exposes the selection state', () => {
    component.onCheckboxChange('/books', true);
    expect(component.selectedFolders).toEqual(['/books']);
    expect(component.isFolderSelected('/books')).toBe(true);

    component.onCheckboxChange('/books', false);
    expect(component.selectedFolders).toEqual([]);
    expect(component.isFolderSelected('/books')).toBe(false);
  });

  it('navigates up to the parent path and clears the active search', () => {
    const getFoldersSpy = vi.spyOn(component, 'getFolders').mockImplementation(() => undefined);
    component.selectedProductName = '/books/scifi';
    component.searchQuery = 'du';

    component.goUp();

    expect(component.selectedProductName).toBe('/books');
    expect(component.searchQuery).toBe('');
    expect(getFoldersSpy).toHaveBeenCalledWith('/books');
  });

  it('selects all filtered folders and can clear them again', () => {
    component.filteredPaths = ['/books', '/comics'];

    component.selectAll();
    expect(component.selectedFolders).toEqual(['/books', '/comics']);
    expect(component.selectedFoldersMap).toMatchObject({
      '/books': true,
      '/comics': true,
    });

    component.deselectAll();
    expect(component.selectedFolders).toEqual([]);
    expect(component.selectedFoldersMap).toMatchObject({
      '/books': false,
      '/comics': false,
    });
  });

  it('closes the dialog with the selected folders or null', () => {
    component.selectedFolders = ['/books'];

    component.onSelect();
    component.onCancel();

    expect(dialogRef.close).toHaveBeenNthCalledWith(1, ['/books']);
    expect(dialogRef.close).toHaveBeenNthCalledWith(2, null);
  });

  it('clears loading state when folder loading fails', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    utilityService.getFolders.mockReturnValueOnce(throwError(() => new Error('boom')));

    component.getFolders('/broken');

    expect(component.isLoading()).toBe(false);
    expect(console.error).toHaveBeenCalled();
  });

  it('extracts the final folder name from a path', () => {
    expect(component.getFolderName('/books/scifi')).toBe('scifi');
    expect(component.getFolderName('/')).toBe('/');
  });
});
