import {ChangeDetectionStrategy, Component, computed, inject, input, output, viewChild} from '@angular/core';

import {AppMenuComponent} from '../../../../shared/ui/menu/app-menu.component';
import {AppMenuContentDirective} from '../../../../shared/ui/menu/app-menu-content.directive';
import {UserService} from '../../../settings/user-management/user.service';
import {type LibraryShelfMenuTarget} from '../../../../shared/layout/navigation/library-shelf-menu-target.model';
import {libraryShelfMenuAvailable, LibraryShelfMenuItemsComponent} from './library-shelf-menu-items.component';

export type {LibraryShelfMenuTarget} from '../../../../shared/layout/navigation/library-shelf-menu-target.model';

@Component({
  selector: 'app-library-shelf-menu',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {class: 'contents'},
  imports: [AppMenuComponent, AppMenuContentDirective, LibraryShelfMenuItemsComponent],
  template: `
    <app-menu
      [ariaLabel]="ariaLabel()"
      (opened)="opened.emit()"
      (closed)="closed.emit()">
      <ng-template appMenuContent>
        @if (available()) {
          <app-library-shelf-menu-items [target]="target()" />
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

  readonly available = computed(
    () => libraryShelfMenuAvailable(this.target(), this.currentUser()),
  );
}
