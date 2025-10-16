import {Component, inject, OnInit} from '@angular/core';
import {DialogService, DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {DirectoryPickerComponent} from '../../shared/components/directory-picker/directory-picker.component';
import {MessageService} from 'primeng/api';
import {Router} from '@angular/router';
import {LibraryService} from '../book/service/library.service';
import {TableModule} from 'primeng/table';
import {Step, StepList, StepPanel, StepPanels, Stepper} from 'primeng/stepper';
import {FormsModule} from '@angular/forms';
import {InputText} from 'primeng/inputtext';
import {BookFileType, Library, LibraryScanMode} from '../book/model/library.model';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {Tooltip} from 'primeng/tooltip';
import {IconPickerService} from '../../shared/service/icon-picker.service';
import {Select} from 'primeng/select';
import {Button} from 'primeng/button';

@Component({
  selector: 'app-library-creator',
  standalone: true,
  templateUrl: './library-creator.component.html',
  imports: [TableModule, StepPanel, FormsModule, InputText, Stepper, StepList, Step, StepPanels, ToggleSwitch, Tooltip, Select, Button],
  styleUrl: './library-creator.component.scss'
})
export class LibraryCreatorComponent implements OnInit {
  chosenLibraryName: string = '';
  folders: string[] = [];
  selectedIcon: string | null = null;

  mode!: string;
  library!: Library | undefined;
  editModeLibraryName: string = '';
  directoryPickerDialogRef!: DynamicDialogRef<DirectoryPickerComponent> | null;
  watch: boolean = false;
  scanMode: LibraryScanMode = 'FILE_AS_BOOK';
  defaultBookFormat: BookFileType | undefined = undefined;

  readonly scanModeOptions = [
    {label: 'Each file is a book (Recommended)', value: 'FILE_AS_BOOK'},
    {label: 'Each folder is a book with extras', value: 'FOLDER_AS_BOOK'}
  ];

  readonly bookFormatOptions = [
    {label: 'None', value: undefined},
    {label: 'EPUB', value: 'EPUB'},
    {label: 'PDF', value: 'PDF'},
    {label: 'CBX/CBZ/CBR', value: 'CBX'}
  ];

  private dialogService = inject(DialogService);
  private dynamicDialogRef = inject(DynamicDialogRef);
  private dynamicDialogConfig = inject(DynamicDialogConfig);
  private libraryService = inject(LibraryService);
  private messageService = inject(MessageService);
  private router = inject(Router);
  private iconPicker = inject(IconPickerService);

  ngOnInit(): void {
    const data = this.dynamicDialogConfig?.data;
    if (data?.mode === 'edit') {
      this.mode = data.mode;
      this.library = this.libraryService.findLibraryById(data.libraryId);
      if (this.library) {
        const {name, icon, paths, watch, scanMode, defaultBookFormat} = this.library;
        this.chosenLibraryName = name;
        this.editModeLibraryName = name;
        this.selectedIcon = `pi pi-${icon}`;
        this.watch = watch;
        this.scanMode = scanMode || 'FILE_AS_BOOK';
        this.defaultBookFormat = defaultBookFormat || undefined;
        this.folders = paths.map(path => path.path);
      }
    }
  }

  openDirectoryPicker(): void {
    this.directoryPickerDialogRef = this.dialogService.open(DirectoryPickerComponent, {
      header: 'Select Media Directory',
      modal: true,
      closable: true,
      contentStyle: {overflow: 'hidden'},
      baseZIndex: 10
    });
    this.directoryPickerDialogRef?.onClose.subscribe((selectedFolders: string[] | null) => {
      if (selectedFolders && selectedFolders.length > 0) {
        selectedFolders.forEach(folder => {
          if (!this.folders.includes(folder)) {
            this.addFolder(folder);
          }
        });
      }
    });
  }

  openIconPicker(): void {
    this.iconPicker.open().subscribe(icon => {
      if (icon) {
        this.selectedIcon = icon;
      }
    });
  }

  addFolder(folder: string): void {
    this.folders.push(folder);
  }

  removeFolder(index: number): void {
    this.folders.splice(index, 1);
  }

  clearSelectedIcon(): void {
    this.selectedIcon = null;
  }

  isLibraryDetailsValid(): boolean {
    return !!this.chosenLibraryName.trim() && !!this.selectedIcon;
  }

  isDirectorySelectionValid(): boolean {
    return this.folders.length > 0;
  }

  validateLibraryNameAndProceed(activateCallback: Function): void {
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

  createOrUpdateLibrary(): void {
    if (this.mode === 'edit') {
      const library: Library = {
        name: this.chosenLibraryName,
        icon: this.selectedIcon?.replace('pi pi-', '') || 'heart',
        paths: this.folders.map(folder => ({path: folder})),
        watch: this.watch,
        scanMode: this.scanMode,
        defaultBookFormat: this.defaultBookFormat
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
        watch: this.watch,
        scanMode: this.scanMode,
        defaultBookFormat: this.defaultBookFormat
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

  getFolderName(path: string): string {
    if (!path || typeof path !== 'string') {
      return '';
    }
    const parts = path.split('/').filter(p => p);
    return parts[parts.length - 1] || path;
  }
}
