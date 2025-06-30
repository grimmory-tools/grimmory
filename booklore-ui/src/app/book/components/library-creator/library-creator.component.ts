import {Component, inject, OnInit, ViewChild} from '@angular/core';
import {DialogService, DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {DirectoryPickerComponent} from '../../../utilities/component/directory-picker/directory-picker.component';
import {MessageService} from 'primeng/api';
import {Router} from '@angular/router';
import {LibraryService} from '../../service/library.service';
import {IconPickerComponent} from '../../../utilities/component/icon-picker/icon-picker.component';
import {Button} from 'primeng/button';
import {TableModule} from 'primeng/table';
import {Step, StepList, StepPanel, StepPanels, Stepper} from 'primeng/stepper';
import {FormsModule} from '@angular/forms';
import {InputText} from 'primeng/inputtext';
import {Library} from '../../model/library.model';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {Tooltip} from 'primeng/tooltip';

@Component({
  selector: 'app-library-creator',
  standalone: true,
  templateUrl: './library-creator.component.html',
  imports: [Button, TableModule, StepPanel, IconPickerComponent, FormsModule, InputText, Stepper, StepList, Step, StepPanels, ToggleSwitch, Tooltip],
  styleUrl: './library-creator.component.scss'
})
export class LibraryCreatorComponent implements OnInit {

  @ViewChild(IconPickerComponent) iconPicker: IconPickerComponent | undefined;

  chosenLibraryName: string = '';
  folders: string[] = [];
  selectedIcon: string | null = null;

  mode!: string;
  library!: Library | undefined;
  editModeLibraryName: string = '';
  directoryPickerDialogRef!: DynamicDialogRef<DirectoryPickerComponent>;
  watch: boolean = false;

  private dialogService = inject(DialogService);
  private dynamicDialogRef = inject(DynamicDialogRef);
  private dynamicDialogConfig = inject(DynamicDialogConfig);
  private libraryService = inject(LibraryService);
  private messageService = inject(MessageService);
  private router = inject(Router);


  ngOnInit(): void {
    const data = this.dynamicDialogConfig?.data;
    if (data?.mode === 'edit') {
      this.mode = data.mode;
      this.library = this.libraryService.findLibraryById(data.libraryId);
      if (this.library) {
        const {name, icon, paths, watch} = this.library;
        this.chosenLibraryName = name;
        this.editModeLibraryName = name;
        this.selectedIcon = `pi pi-${icon}`;
        this.watch = watch;
        this.folders = paths.map(path => path.path);
      }
    }
  }

  openDirectoryPicker() {
    this.directoryPickerDialogRef = this.dialogService.open(DirectoryPickerComponent, {
      header: 'Select Media Directory',
      modal: true,
      closable: true,
      contentStyle: {overflow: 'hidden'},
      baseZIndex: 10
    });
    this.directoryPickerDialogRef.onClose.subscribe((selectedFolder: string) => {
      if (selectedFolder) {
        this.addFolder(selectedFolder);
      }
    });
  }

  addFolder(folder: string): void {
    this.folders.push(folder);
  }

  removeFolder(index: number): void {
    this.folders.splice(index, 1);
  }

  openIconPicker() {
    console.log('aaa')
    if (this.iconPicker) {
      this.iconPicker.open();
    }
  }

  onIconSelected(icon: string) {
    this.selectedIcon = icon;
  }

  clearSelectedIcon() {
    this.selectedIcon = null;
  }

  isLibraryDetailsValid(): boolean {
    return !!this.chosenLibraryName.trim() && !!this.selectedIcon;
  }

  isDirectorySelectionValid(): boolean {
    return this.folders.length > 0;
  }

  createOrUpdateLibrary() {
    if (this.mode === 'edit') {
      const library: Library = {
        name: this.chosenLibraryName,
        icon: this.selectedIcon?.replace('pi pi-', '') || 'heart',
        paths: this.folders.map(folder => ({path: folder})),
        watch: this.watch
      };
      this.libraryService.updateLibrary(library, this.library?.id).subscribe({
        next: () => {
          this.messageService.add({severity: 'success', summary: 'Library Updated', detail: 'The library was updated successfully.'});
          this.dynamicDialogRef.close();
        },
        error: (e) => {
          this.messageService.add({severity: 'error', summary: 'Update Failed', detail: 'An error occurred while updating the library. Please try again.'});
          console.error(e);
        }
      });
    } else {
      const library: Library = {
        name: this.chosenLibraryName,
        icon: this.selectedIcon?.replace('pi pi-', '') || 'heart',
        paths: this.folders.map(folder => ({path: folder})),
        watch: this.watch
      };
      this.libraryService.createLibrary(library).subscribe({
        next: (createdLibrary) => {
          this.router.navigate(['/library', createdLibrary.id, 'books']);
          this.messageService.add({severity: 'success', summary: 'Library Created', detail: 'The library was created successfully.'});
          this.dynamicDialogRef.close();
        },
        error: (e) => {
          this.messageService.add({severity: 'error', summary: 'Creation Failed', detail: 'An error occurred while creating the library. Please try again.'});
          console.error(e);
        }
      });
    }
  }

  validateLibraryNameAndProceed(activateCallback: Function) {
    let trimmedLibraryName = this.chosenLibraryName.trim();
    if (trimmedLibraryName && trimmedLibraryName != this.editModeLibraryName) {
      let exists = this.libraryService.doesLibraryExistByName(trimmedLibraryName);
      if (exists) {
        this.messageService.add({
          severity: 'error',
          summary: 'Library Name Exists',
          detail: 'This library name is already taken.',
        });
      } else {
        activateCallback(2);
      }
    } else {
      activateCallback(2);
    }
  }

}
