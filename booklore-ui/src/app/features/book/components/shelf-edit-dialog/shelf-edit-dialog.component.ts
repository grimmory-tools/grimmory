import {Component, inject, OnInit} from '@angular/core';
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

  shelfName: string = '';
  selectedIcon: IconSelection | null = null;
  shelf!: Shelf | undefined;
  isPublic: boolean = false;
  isAdmin: boolean = this.userService.getCurrentUser()?.permissions.admin ?? false;

  ngOnInit(): void {
    const shelfId = this.dynamicDialogConfig?.data.shelfId;
    this.shelf = this.shelfService.getShelfById(shelfId);
    if (this.shelf) {
      this.shelfName = this.shelf.name;
      this.isPublic = this.shelf.publicShelf ?? false;
      if (this.shelf.iconType && this.shelf.icon) {
        if (this.shelf.iconType === 'PRIME_NG') {
          this.selectedIcon = {type: 'PRIME_NG', value: `pi pi-${this.shelf.icon}`};
        } else {
          this.selectedIcon = {type: 'CUSTOM_SVG', value: this.shelf.icon};
        }
      }
    }
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
