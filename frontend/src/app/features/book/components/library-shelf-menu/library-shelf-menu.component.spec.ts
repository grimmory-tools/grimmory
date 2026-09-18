import {signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {beforeEach, describe, expect, it} from 'vitest';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {LayoutService} from '../../../../shared/layout/layout.service';
import {UserService} from '../../../settings/user-management/user.service';
import {LibraryShelfMenuService} from '../../service/library-shelf-menu.service';
import {LibraryShelfMenuComponent} from './library-shelf-menu.component';

describe('LibraryShelfMenuComponent', () => {
  let fixture: ComponentFixture<LibraryShelfMenuComponent>;
  const currentUser = signal({
    id: 3,
    permissions: {admin: false, canManageLibrary: true},
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LibraryShelfMenuComponent, getTranslocoModule()],
      providers: [
        provideRouter([]),
        {provide: UserService, useValue: {currentUser}},
        {provide: LibraryShelfMenuService, useValue: {}},
        {provide: LayoutService, useValue: {isDesktop: signal(true)}},
      ],
    });
    fixture = TestBed.createComponent(LibraryShelfMenuComponent);
  });

  it.each([
    {
      name: 'library manager',
      user: {id: 3, permissions: {admin: false, canManageLibrary: true}},
      target: {type: 'library', entity: {id: 7, name: 'Library', watch: true, paths: []}},
      labels: ['Add Physical Book', 'Import ISBNs from File', 'Edit Library', 'Re-scan Library',
        'Custom Fetch Metadata', 'Auto Fetch Metadata', 'Find Duplicates', 'Delete Library'],
      disabled: 0,
      available: true,
    },
    {
      name: 'library reader',
      user: {id: 3, permissions: {admin: false, canManageLibrary: false}},
      target: {type: 'library', entity: {id: 7, name: 'Library', watch: true, paths: []}},
      labels: [],
      disabled: 0,
      available: false,
    },
    {
      name: 'public shelf reader',
      user: {id: 3, permissions: {admin: false, canManageLibrary: false}},
      target: {type: 'shelf', entity: {id: 11, name: 'Shelf', userId: 99, publicShelf: true}},
      labels: ['Edit Shelf', 'Delete Shelf'],
      disabled: 2,
      available: true,
    },
    {
      name: 'public magic-shelf reader',
      user: {id: 3, permissions: {admin: false, canManageLibrary: false}},
      target: {type: 'magicShelf', entity: {id: 13, name: 'Magic', filterJson: '{}', isPublic: true}},
      labels: ['Edit Magic Shelf', 'Copy JSON', 'Delete Magic Shelf'],
      disabled: 2,
      available: true,
    },
  ])('renders the $name state', async ({user, target, labels, disabled, available}) => {
    currentUser.set(user);
    fixture.componentRef.setInput('target', target);
    fixture.componentRef.setInput('ariaLabel', 'Actions');
    await fixture.whenStable();

    fixture.componentInstance.menu().open(fixture.nativeElement);
    await fixture.whenStable();

    const menu = document.querySelector('app-menu[aria-label="Actions"]') as HTMLElement;
    const renderedLabels = Array.from(menu.querySelectorAll('app-menu-item'))
      .map(item => item.textContent?.trim());
    expect(fixture.componentInstance.available()).toBe(available);
    expect(renderedLabels).toEqual(labels);
    expect(menu.querySelectorAll('app-menu-item[aria-disabled="true"]')).toHaveLength(disabled);
  });
});
