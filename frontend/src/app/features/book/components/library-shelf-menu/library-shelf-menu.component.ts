import {ChangeDetectionStrategy, Component, computed, inject, input, output, viewChild} from '@angular/core';
import {LucideTrash2} from '@lucide/angular';
import {TranslocoPipe} from '@jsverse/transloco';

import {AppMenuComponent} from '../../../../shared/ui/menu/app-menu.component';
import {AppMenuContentDirective} from '../../../../shared/ui/menu/app-menu-content.directive';
import {AppMenuItemComponent} from '../../../../shared/ui/menu/app-menu-item.component';
import {AppMenuSectionComponent} from '../../../../shared/ui/menu/app-menu-section.component';
import {AppMenuSeparatorComponent} from '../../../../shared/ui/menu/app-menu-separator.component';
import {MagicShelf} from '../../../magic-shelf/service/magic-shelf.service';
import {UserService} from '../../../settings/user-management/user.service';
import {Library} from '../../model/library.model';
import {Shelf} from '../../model/shelf.model';
import {LibraryShelfMenuService} from '../../service/library-shelf-menu.service';

type Persisted<T extends {id?: number | null}> = Omit<T, 'id'> & {id: number};

export type LibraryShelfMenuTarget =
  | {type: 'library'; entity: Persisted<Library>}
  | {type: 'shelf'; entity: Persisted<Shelf>}
  | {type: 'magicShelf'; entity: Persisted<MagicShelf>};

@Component({
  selector: 'app-library-shelf-menu',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {class: 'contents'},
  imports: [
    TranslocoPipe,
    AppMenuComponent,
    AppMenuContentDirective,
    AppMenuItemComponent,
    AppMenuSectionComponent,
    AppMenuSeparatorComponent,
  ],
  template: `
    @let currentTarget = target();
    <app-menu
      [ariaLabel]="ariaLabel()"
      (opened)="opened.emit()"
      (closed)="closed.emit()">
      <ng-template appMenuContent>
        @if (available()) {
          @switch (currentTarget.type) {
            @case ('library') {
              <app-menu-item value="add-physical-book" (selected)="actions.addPhysicalBook(currentTarget.entity.id)">
                {{ 'book.shelfMenuService.library.addPhysicalBook' | transloco }}
              </app-menu-item>
              <app-menu-item value="import-isbns" (selected)="actions.importIsbns(currentTarget.entity.id)">
                {{ 'book.shelfMenuService.library.bulkIsbnImport' | transloco }}
              </app-menu-item>
              <app-menu-separator />
              <app-menu-item
                value="edit-library"
                (selected)="actions.editLibrary(currentTarget.entity.id)">
                {{ 'book.shelfMenuService.library.editLibrary' | transloco }}
              </app-menu-item>
              <app-menu-item value="rescan-library" (selected)="actions.rescanLibrary(currentTarget.entity)">
                {{ 'book.shelfMenuService.library.rescanLibrary' | transloco }}
              </app-menu-item>
              <app-menu-separator />
              <app-menu-item value="custom-fetch-metadata" (selected)="actions.customFetchLibraryMetadata(currentTarget.entity.id)">
                {{ 'book.shelfMenuService.library.customFetchMetadata' | transloco }}
              </app-menu-item>
              <app-menu-item value="auto-fetch-metadata" (selected)="actions.autoFetchLibraryMetadata(currentTarget.entity.id)">
                {{ 'book.shelfMenuService.library.autoFetchMetadata' | transloco }}
              </app-menu-item>
              <app-menu-item value="find-duplicates" (selected)="actions.findLibraryDuplicates(currentTarget.entity.id)">
                {{ 'book.shelfMenuService.library.findDuplicates' | transloco }}
              </app-menu-item>
              <app-menu-separator />
              <app-menu-item
                value="delete-library"
                [icon]="trashIcon"
                variant="destructive"
                (selected)="actions.deleteLibrary(currentTarget.entity)">
                {{ 'book.shelfMenuService.library.deleteLibrary' | transloco }}
              </app-menu-item>
            }
            @case ('shelf') {
              @if (currentTarget.entity.publicShelf || !canManageShelf()) {
                <app-menu-section>
                  @if (currentTarget.entity.publicShelf) {
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
                (selected)="actions.editShelf(currentTarget.entity.id)">
                {{ 'book.shelfMenuService.shelf.editShelf' | transloco }}
              </app-menu-item>
              <app-menu-separator />
              <app-menu-item
                value="delete-shelf"
                [icon]="trashIcon"
                [disabled]="!canManageShelf()"
                variant="destructive"
                (selected)="actions.deleteShelf(currentTarget.entity)">
                {{ 'book.shelfMenuService.shelf.deleteShelf' | transloco }}
              </app-menu-item>
            }
            @case ('magicShelf') {
              <app-menu-item
                value="edit-magic-shelf"
                [disabled]="!canManageMagicShelf()"
                (selected)="actions.editMagicShelf(currentTarget.entity.id)">
                {{ 'book.shelfMenuService.magicShelf.editMagicShelf' | transloco }}
              </app-menu-item>
              <app-menu-item value="export-magic-shelf" (selected)="actions.copyMagicShelfJson(currentTarget.entity.filterJson)">
                {{ 'book.shelfMenuService.magicShelf.exportJson' | transloco }}
              </app-menu-item>
              <app-menu-separator />
              <app-menu-item
                value="delete-magic-shelf"
                [icon]="trashIcon"
                [disabled]="!canManageMagicShelf()"
                variant="destructive"
                (selected)="actions.deleteMagicShelf(currentTarget.entity)">
                {{ 'book.shelfMenuService.magicShelf.deleteMagicShelf' | transloco }}
              </app-menu-item>
            }
          }
        }
      </ng-template>
    </app-menu>
  `,
})
export class LibraryShelfMenuComponent {
  readonly target = input.required<LibraryShelfMenuTarget>();
  readonly ariaLabel = input.required<string>();

  readonly opened = output<void>();
  readonly closed = output<void>();
  readonly menu = viewChild.required(AppMenuComponent);

  private readonly currentUser = inject(UserService).currentUser;
  protected readonly actions = inject(LibraryShelfMenuService);
  protected readonly trashIcon = LucideTrash2.icon;

  readonly available = computed(() => {
    const user = this.currentUser();
    if (!user) return false;
    return this.target().type !== 'library'
      || user.permissions.admin
      || user.permissions.canManageLibrary;
  });

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
