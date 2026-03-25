import {Component, effect, inject, Injector, OnInit} from '@angular/core';
import {ShelfService} from '../../service/shelf.service';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';

import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {Shelf} from '../../model/shelf.model';
import {MessageService} from 'primeng/api';
import {IconPickerService, IconSelection} from '../../../../shared/service/icon-picker.service';
import {IconDisplayComponent} from '../../../../shared/components/icon-display/icon-display.component';
import {CheckboxModule} from 'primeng/checkbox';
import {UserService} from '../../../settings/user-management/user.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-shelf-edit-dialog',
  imports: [
    Button,
    InputText,
    ReactiveFormsModule,
    FormsModule,
    IconDisplayComponent,
    CheckboxModule,
    TranslocoDirective
  ],
  templateUrl: './shelf-edit-dialog.component.html',
  standalone: true,
  styleUrl: './shelf-edit-dialog.component.scss'
})
export class ShelfEditDialogComponent implements OnInit {

  private shelfService = inject(ShelfService);
  private dynamicDialogConfig = inject(DynamicDialogConfig);
  private dynamicDialogRef = inject(DynamicDialogRef);
  private messageService = inject(MessageService);
  private iconPickerService = inject(IconPickerService);
  private userService = inject(UserService);
  private readonly t = inject(TranslocoService);
  private readonly injector = inject(Injector);
  private shelfInitialized = false;

  shelfName: string = '';
  selectedIcon: IconSelection | null = null;
  shelf!: Shelf | undefined;
  isPublic: boolean = false;
  isAdmin: boolean = this.userService.getCurrentUser()?.permissions.admin ?? false;

  ngOnInit(): void {
    const shelfId = this.dynamicDialogConfig?.data.shelfId;
    effect(() => {
      if (this.shelfInitialized) {
        return;
      }

      const shelf = this.shelfService.shelves().find(currentShelf => currentShelf.id === shelfId);
      if (!shelf) {
        return;
      }

      this.shelfInitialized = true;
      this.shelf = shelf;
      this.shelfName = shelf.name;
      this.isPublic = shelf.publicShelf ?? false;
      if (shelf.iconType && shelf.icon) {
        if (shelf.iconType === 'PRIME_NG') {
          this.selectedIcon = {type: 'PRIME_NG', value: `pi pi-${shelf.icon}`};
        } else {
          this.selectedIcon = {type: 'CUSTOM_SVG', value: shelf.icon};
        }
      }
    }, {injector: this.injector});
  }

  openIconPicker() {
    this.iconPickerService.open().subscribe(icon => {
      if (icon) {
        this.selectedIcon = icon;
      }
    })
  }

  clearSelectedIcon() {
    this.selectedIcon = null;
  }

  save() {
    const iconValue = this.selectedIcon?.value ?? null;
    const iconType = this.selectedIcon?.type ?? null;

    const shelf: Shelf = {
      name: this.shelfName,
      icon: iconValue,
      iconType: iconType,
      publicShelf: this.isPublic
    };

    this.shelfService.updateShelf(shelf, this.shelf?.id).subscribe({
      next: () => {
        this.messageService.add({severity: 'success', summary: this.t.translate('book.shelfEditDialog.toast.updateSuccessSummary'), detail: this.t.translate('book.shelfEditDialog.toast.updateSuccessDetail')});
        this.dynamicDialogRef.close();
      },
      error: (e) => {
        this.messageService.add({severity: 'error', summary: this.t.translate('book.shelfEditDialog.toast.updateFailedSummary'), detail: this.t.translate('book.shelfEditDialog.toast.updateFailedDetail')});
        console.error(e);
      }
    });
  }

  closeDialog() {
    this.dynamicDialogRef.close();
  }
}
