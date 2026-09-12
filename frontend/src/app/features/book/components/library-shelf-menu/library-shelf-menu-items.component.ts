import {ChangeDetectionStrategy, Component, computed, inject, input} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';

import {AppMenuComponent} from '../../../../shared/ui/menu/app-menu.component';
import {AppMenuItemComponent} from '../../../../shared/ui/menu/app-menu-item.component';
import {AppMenuSectionComponent} from '../../../../shared/ui/menu/app-menu-section.component';
import {AppMenuSeparatorComponent} from '../../../../shared/ui/menu/app-menu-separator.component';
import {type LibraryShelfMenuTarget} from '../../../../shared/layout/navigation/library-shelf-menu-target.model';
import {UserService} from '../../../settings/user-management/user.service';
import {LibraryShelfMenuService} from '../../service/library-shelf-menu.service';

export interface LibraryShelfMenuUser {
  readonly id: number;
  readonly permissions: {
    readonly admin: boolean;
    readonly canManageLibrary: boolean;
  };
}

export function libraryShelfMenuAvailable(
  target: LibraryShelfMenuTarget,
  user: LibraryShelfMenuUser | null,
): boolean {
  if (!user) {
    return false;
  }
  return target.type !== 'library'
    || user.permissions.admin
    || user.permissions.canManageLibrary;
}

@Component({
  selector: 'app-library-shelf-menu-items',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {class: 'contents'},
  imports: [
    TranslocoPipe,
    AppMenuComponent,
    AppMenuItemComponent,
    AppMenuSectionComponent,
    AppMenuSeparatorComponent,
  ],
  template: `
    @let current = target();
    @switch (current.type) {
      @case ('library') {
        <app-menu-item value="add-physical-book" (selected)="actions.addPhysicalBook(current.entity.id)">
          {{ 'book.shelfMenuService.library.addPhysicalBook' | transloco }}
        </app-menu-item>
        <app-menu-item value="import-isbns" (selected)="actions.importIsbns(current.entity.id)">
          {{ 'book.shelfMenuService.library.bulkIsbnImport' | transloco }}
        </app-menu-item>
        <app-menu-item value="rescan-library" (selected)="actions.rescanLibrary(current.entity)">
          {{ 'book.shelfMenuService.library.rescanLibrary' | transloco }}
        </app-menu-item>
        <app-menu-separator />
        <app-menu-item value="manage-library" [submenu]="manageLibraryMenu">
          {{ 'book.shelfMenuService.library.manageLibrary' | transloco }}
        </app-menu-item>

        <app-menu
          #manageLibraryMenu="ngMenu"
          [ariaLabel]="'book.shelfMenuService.library.manageLibrary' | transloco">
          <app-menu-item value="edit-library" (selected)="actions.editLibrary(current.entity.id)">
            {{ 'book.shelfMenuService.library.editLibrary' | transloco }}
          </app-menu-item>
          <app-menu-item value="custom-fetch-metadata" (selected)="actions.customFetchLibraryMetadata(current.entity.id)">
            {{ 'book.shelfMenuService.library.customFetchMetadata' | transloco }}
          </app-menu-item>
          <app-menu-item value="auto-fetch-metadata" (selected)="actions.autoFetchLibraryMetadata(current.entity.id)">
            {{ 'book.shelfMenuService.library.autoFetchMetadata' | transloco }}
          </app-menu-item>
          <app-menu-item value="find-duplicates" (selected)="actions.findLibraryDuplicates(current.entity.id)">
            {{ 'book.shelfMenuService.library.findDuplicates' | transloco }}
          </app-menu-item>
          <app-menu-separator />
          <app-menu-item value="delete-library" variant="destructive" (selected)="actions.deleteLibrary(current.entity)">
            {{ 'book.shelfMenuService.library.deleteLibrary' | transloco }}
          </app-menu-item>
        </app-menu>
      }
      @case ('shelf') {
        @if (current.entity.publicShelf || !canManageShelf()) {
          <app-menu-section>
            @if (current.entity.publicShelf) {
              {{ 'book.shelfMenuService.shelf.publicShelfPrefix' | transloco }}
            }
            {{ (canManageShelf()
              ? 'book.shelfMenuService.shelf.optionsLabel'
              : 'book.shelfMenuService.shelf.readOnly') | transloco }}
          </app-menu-section>
        }
        <app-menu-item
          value="edit-shelf"
          [disabled]="!canManageShelf()"
          (selected)="actions.editShelf(current.entity.id)">
          {{ 'book.shelfMenuService.shelf.editShelf' | transloco }}
        </app-menu-item>
        <app-menu-separator />
        <app-menu-item
          value="delete-shelf"
          [disabled]="!canManageShelf()"
          variant="destructive"
          (selected)="actions.deleteShelf(current.entity)">
          {{ 'book.shelfMenuService.shelf.deleteShelf' | transloco }}
        </app-menu-item>
      }
      @case ('magicShelf') {
        <app-menu-item
          value="edit-magic-shelf"
          [disabled]="!canManageMagicShelf()"
          (selected)="actions.editMagicShelf(current.entity.id)">
          {{ 'book.shelfMenuService.magicShelf.editMagicShelf' | transloco }}
        </app-menu-item>
        <app-menu-item value="export-magic-shelf" (selected)="actions.copyMagicShelfJson(current.entity.filterJson)">
          {{ 'book.shelfMenuService.magicShelf.exportJson' | transloco }}
        </app-menu-item>
        <app-menu-separator />
        <app-menu-item
          value="delete-magic-shelf"
          [disabled]="!canManageMagicShelf()"
          variant="destructive"
          (selected)="actions.deleteMagicShelf(current.entity)">
          {{ 'book.shelfMenuService.magicShelf.deleteMagicShelf' | transloco }}
        </app-menu-item>
      }
    }
  `,
})
export class LibraryShelfMenuItemsComponent {
  readonly target = input.required<LibraryShelfMenuTarget>();

  private readonly currentUser = inject(UserService).currentUser;
  protected readonly actions = inject(LibraryShelfMenuService);

  protected readonly canManageShelf = computed(() => {
    const target = this.target();
    return target.type === 'shelf' && target.entity.userId === this.currentUser()?.id;
  });

  protected readonly canManageMagicShelf = computed(() => {
    const target = this.target();
    return target.type === 'magicShelf'
      && (!target.entity.isPublic || !!this.currentUser()?.permissions.admin);
  });
}
