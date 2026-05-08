import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { MessageService } from 'primeng/api';
import { Router } from '@angular/router';
import { LibraryService } from '../book/service/library.service';
import { FormsModule } from '@angular/forms';
import { InputText } from 'primeng/inputtext';
import { Library, MetadataSource, OrganizationMode } from '../book/model/library.model';
import { BookType } from '../book/model/book.model';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { Tooltip } from 'primeng/tooltip';
import { IconPickerService, IconSelection } from '../../shared/service/icon-picker.service';
import { Button } from 'primeng/button';
import { IconDisplayComponent } from '../../shared/components/icon-display/icon-display.component';
import { DialogLauncherService } from '../../shared/services/dialog-launcher.service';
import { switchMap, map } from 'rxjs';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { Checkbox } from 'primeng/checkbox';
import { Select } from 'primeng/select';
import { TranslocoDirective, TranslocoPipe, TranslocoService } from '@jsverse/transloco';

interface FormatEntry { type: BookType; label: string }

interface LibraryCreatorDialogData {
  mode: 'create' | 'edit';
  libraryId?: number;
}

@Component({
  selector: 'app-library-creator',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './library-creator.component.html',
  imports: [FormsModule, InputText, ToggleSwitch, Tooltip, Button, IconDisplayComponent, DragDropModule, Checkbox, Select, TranslocoDirective, TranslocoPipe],
  styleUrl: './library-creator.component.scss'
})
export class LibraryCreatorComponent {

  private readonly dialogLauncherService = inject(DialogLauncherService);
  private readonly dynamicDialogRef = inject(DynamicDialogRef);
  private readonly dynamicDialogConfig = inject(DynamicDialogConfig);
  private readonly libraryService = inject(LibraryService);
  private readonly messageService = inject(MessageService);
  private readonly router = inject(Router);
  private readonly iconPicker = inject(IconPickerService);
  private readonly t = inject(TranslocoService);

  private readonly activeLang = toSignal(this.t.langChanges$, {
    initialValue: this.t.getActiveLang(),
  });

  readonly allBookFormats: FormatEntry[] = [
    { type: 'EPUB', label: 'EPUB' },
    { type: 'PDF', label: 'PDF' },
    { type: 'CBX', label: 'CBX (CBZ/CBR/CB7)' },
    { type: 'MOBI', label: 'MOBI' },
    { type: 'AZW3', label: 'AZW3' },
    { type: 'FB2', label: 'FB2' },
    { type: 'AUDIOBOOK', label: 'Audiobook' },
  ];

  readonly chosenLibraryName = signal<string>('');
  readonly folders = signal<string[]>([]);
  readonly selectedIcon = signal<IconSelection | null>(null);
  readonly mode = signal<string>('');
  readonly editModeLibraryName = signal<string>('');
  readonly watch = signal<boolean>(false);
  readonly formatPriority = signal<FormatEntry[]>([...this.allBookFormats]);
  readonly allowAllFormats = signal<boolean>(true);
  readonly selectedAllowedFormats = signal<Set<BookType>>(new Set(this.allBookFormats.map(f => f.type)));
  readonly formatCounts = signal<Record<string, number>>({});
  readonly metadataSource = signal<MetadataSource>('EMBEDDED');
  readonly organizationMode = signal<OrganizationMode>('BOOK_PER_FILE');

  readonly metadataSourceOptions = computed(() => {
    this.activeLang();
    return [
      { label: this.t.translate('libraryCreator.creator.metadataSourceEmbedded'), value: 'EMBEDDED' },
      { label: this.t.translate('libraryCreator.creator.metadataSourceSidecar'), value: 'SIDECAR' },
      { label: this.t.translate('libraryCreator.creator.metadataSourcePreferSidecar'), value: 'PREFER_SIDECAR' },
      { label: this.t.translate('libraryCreator.creator.metadataSourcePreferEmbedded'), value: 'PREFER_EMBEDDED' },
      { label: this.t.translate('libraryCreator.creator.metadataSourceNone'), value: 'NONE' },
    ];
  });

  readonly organizationModeOptions = computed(() => {
    this.activeLang();
    const base = [
      { label: this.t.translate('libraryCreator.creator.organizationModeBookPerFile'), value: 'BOOK_PER_FILE' },
      { label: this.t.translate('libraryCreator.creator.organizationModeBookPerFolder'), value: 'BOOK_PER_FOLDER' },
    ];
    if (this.organizationMode() === 'AUTO_DETECT') {
      base.push({ label: this.t.translate('libraryCreator.creator.organizationModeAutoDetect'), value: 'AUTO_DETECT' });
    }
    return base;
  });

  readonly isLibraryDetailsValid = computed(() => !!this.chosenLibraryName().trim());
  readonly isDirectorySelectionValid = computed(() => this.folders().length > 0);

  constructor() {
    /**
     * Runs once after construction. `untracked` prevents the body from being
     * treated as a reactive dependency (we only want it to run once on init).
     */
    effect(() => {
      untracked(() => this.initFromDialogData());
    });
  }

  private initFromDialogData(): void {
    const data = this.dynamicDialogConfig?.data as LibraryCreatorDialogData;
    if (data?.mode !== 'edit') {
      this.mode.set('create');
      return;
    }

    this.mode.set('edit');

    const library = this.libraryService.findLibraryById(data.libraryId!);
    if (!library) {
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('libraryCreator.creator.toast.updateFailedSummary'),
        detail: this.t.translate('libraryCreator.creator.toast.updateFailedDetail'),
      });
      this.dynamicDialogRef.close();
      return;
    }

    const { name, icon, iconType, paths, watch, formatPriority, allowedFormats } = library;

    this.chosenLibraryName.set(name);
    this.editModeLibraryName.set(name);
    this.watch.set(watch);
    this.folders.set(paths.map(p => p.path));

    if (icon != null && iconType) {
      if (iconType === 'CUSTOM_SVG') {
        this.selectedIcon.set({ type: 'CUSTOM_SVG', value: icon });
      } else {
        const value = icon.slice(0, 6) === 'pi pi-' ? icon : `pi pi-${icon}`;
        this.selectedIcon.set({ type: 'PRIME_NG', value });
      }
    }

    if (formatPriority && formatPriority.length > 0) {
      const ordered = formatPriority
        .map(type => this.allBookFormats.find(f => f.type === type)!)
        .filter(Boolean);
      const existingTypes = new Set(formatPriority);
      this.allBookFormats.forEach(f => {
        if (!existingTypes.has(f.type)) ordered.push(f);
      });
      this.formatPriority.set(ordered);
    }

    if (allowedFormats && allowedFormats.length > 0) {
      this.allowAllFormats.set(false);
      this.selectedAllowedFormats.set(new Set(allowedFormats));
    } else {
      this.allowAllFormats.set(true);
      this.selectedAllowedFormats.set(new Set(this.allBookFormats.map(f => f.type)));
    }

    if (library.metadataSource) {
      this.metadataSource.set(library.metadataSource);
    }

    if (library.organizationMode) {
      this.organizationMode.set(library.organizationMode);
    }

    this.libraryService.getBookCountsByFormat(library.id!).subscribe(counts => {
      this.formatCounts.set(counts);
    });
  }

  onAllowAllFormatsChange(): void {
    if (this.allowAllFormats()) {
      this.selectedAllowedFormats.set(new Set(this.allBookFormats.map(f => f.type)));
    }
  }

  onFormatCheckboxChange(formatType: BookType, checked: boolean): void {
    const next = new Set(this.selectedAllowedFormats());
    if (checked) {
      next.add(formatType);
    } else {
      if (next.size === 1 && next.has(formatType)) {
        return;
      }
      next.delete(formatType);
    }
    this.selectedAllowedFormats.set(next);
    this.allowAllFormats.set(next.size === this.allBookFormats.length);
  }

  isFormatSelected(formatType: BookType): boolean {
    return this.selectedAllowedFormats().has(formatType);
  }

  getFormatWarning(formatType: BookType): string | null {
    if (this.mode() !== 'edit') return null;
    const count = this.formatCounts()[formatType];
    if (count && count > 0 && !this.selectedAllowedFormats().has(formatType)) {
      return this.t.translate('libraryCreator.creator.formatWarning', { count });
    }
    return null;
  }

  hasAnyFormatWarning(): boolean {
    if (this.mode() !== 'edit') return false;
    return this.allBookFormats.some(f => this.getFormatWarning(f.type) !== null);
  }

  async openDirectoryPicker(): Promise<void> {
    const ref = await this.dialogLauncherService.openDirectoryPickerDialog();
    ref?.onClose.subscribe((selectedFolders: string[] | null) => {
      if (selectedFolders && selectedFolders.length > 0) {
        this.folders.update(current => {
          const incoming = selectedFolders.filter(f => !current.includes(f));
          return incoming.length > 0 ? [...current, ...incoming] : current;
        });
      }
    });
  }

  addFolder(folder: string): void {
    this.folders.update(current => [...current, folder]);
  }

  removeFolder(index: number): void {
    this.folders.update(current => current.filter((_, i) => i !== index));
  }

  openIconPicker(): void {
    this.iconPicker.open().subscribe(icon => {
      if (icon) this.selectedIcon.set(icon);
    });
  }

  clearSelectedIcon(): void {
    this.selectedIcon.set(null);
  }

  closeDialog(): void {
    this.dynamicDialogRef.close();
  }

  createOrUpdateLibrary(): void {
    const trimmedLibraryName = this.chosenLibraryName().trim();
    if (trimmedLibraryName && trimmedLibraryName !== this.editModeLibraryName()) {
      const exists = this.libraryService.doesLibraryExistByName(trimmedLibraryName);
      if (exists) {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('libraryCreator.creator.toast.nameExistsSummary'),
          detail: this.t.translate('libraryCreator.creator.toast.nameExistsDetail'),
        });
        return;
      }
    }

    const library: Library = {
      name: trimmedLibraryName,
      icon: this.selectedIcon()?.value ?? null,
      iconType: this.selectedIcon()?.type ?? null,
      paths: this.folders().map(folder => ({ path: folder })),
      watch: this.watch(),
      formatPriority: this.formatPriority().map(f => f.type),
      allowedFormats: this.allowAllFormats() ? [] : Array.from(this.selectedAllowedFormats()),
      metadataSource: this.metadataSource(),
      organizationMode: this.organizationMode(),
    };

    if (this.mode() === 'edit') {
      const libraryId = (this.dynamicDialogConfig.data as LibraryCreatorDialogData)?.libraryId;
      if (libraryId == null) {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('libraryCreator.creator.toast.updateFailedSummary'),
          detail: this.t.translate('libraryCreator.creator.toast.updateFailedDetail'),
        });
        return;
      }

      this.libraryService.updateLibrary(library, libraryId).pipe(
        switchMap(() => this.libraryService.refreshLibrary(libraryId))
      ).subscribe({
        next: () => {
          this.messageService.add({ severity: 'success', summary: this.t.translate('libraryCreator.creator.toast.updatedSummary'), detail: this.t.translate('libraryCreator.creator.toast.updatedDetail') });
          this.dynamicDialogRef.close();
        },
        error: (e: Error) => {
          this.messageService.add({ severity: 'error', summary: this.t.translate('libraryCreator.creator.toast.updateFailedSummary'), detail: this.t.translate('libraryCreator.creator.toast.updateFailedDetail') });
          console.error(e);
        }
      });
    } else {
      this.libraryService.scanLibraryPaths(library).pipe(
        switchMap(count => {
          if (count >= 500) {
            console.warn(`Library has ${count} processable files (>500). Will use buffered loading.`);
            this.libraryService.setLargeLibraryLoading(true, count);
          }
          return this.libraryService.createLibrary(library).pipe(
            map(createdLibrary => ({ createdLibrary, count }))
          );
        })
      ).subscribe({
        next: ({ createdLibrary, count }) => {
          if (createdLibrary) {
            this.router.navigate(['/library', createdLibrary.id, 'books']);
            this.messageService.add({
              severity: 'success',
              summary: this.t.translate('libraryCreator.creator.toast.createdSummary'),
              detail: count >= 500
                ? this.t.translate('libraryCreator.creator.toast.createdLargeDetail', { count })
                : this.t.translate('libraryCreator.creator.toast.createdDetail'),
            });
            this.dynamicDialogRef.close();
          }
        },
        error: (e: Error) => {
          this.libraryService.setLargeLibraryLoading(false, 0);
          this.messageService.add({ severity: 'error', summary: this.t.translate('libraryCreator.creator.toast.createFailedSummary'), detail: this.t.translate('libraryCreator.creator.toast.createFailedDetail') });
          console.error(e);
        }
      });
    }
  }

  onFormatPriorityDrop(event: CdkDragDrop<FormatEntry[]>): void {
    this.formatPriority.update(current => {
      const next = [...current];
      moveItemInArray(next, event.previousIndex, event.currentIndex);
      return next;
    });
  }
}
