import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { JsonPipe } from '@angular/common';
import { CdkScrollable } from '@angular/cdk/scrolling';
import { Router } from '@angular/router';
import { form, FormField, FormRoot, minLength, required, validate } from '@angular/forms/signals';
import { AppButtonComponent } from '../../../shared/ui/button/app-button.component';
import { AppFieldComponent } from '../../../shared/ui/field/app-field.component';
import { AppInputComponent } from '../../../shared/ui/input/app-input.component';
import { AppMessageComponent } from '../../../shared/ui/message/app-message.component';
import { AppMultiAutocompleteComponent } from '../../../shared/ui/autocomplete/app-multi-autocomplete.component';
import { AppMultiSelectComponent } from '../../../shared/ui/select/app-multi-select.component';
import { AppRadioGroupComponent, type RadioOption } from '../../../shared/ui/radio-group/app-radio-group.component';
import { AppSelectComponent } from '../../../shared/ui/select/app-select.component';
import { AppSwitchComponent } from '../../../shared/ui/switch/app-switch.component';
import { AppTagComponent } from '../../../shared/ui/tag/app-tag.component';
import { type SelectOption } from '../../../shared/ui/select/app-select.options';
import { LucideChevronLeft, LucideLoaderCircle, LucideSave } from '@lucide/angular';

type OrganizationMode = 'BOOK_PER_FILE' | 'BOOK_PER_FOLDER';
type MetadataSource = 'EMBEDDED' | 'SIDECAR' | 'PREFER_SIDECAR' | 'PREFER_EMBEDDED' | 'NONE';

interface LibraryFormModel {
  name: string;
  folders: string[];
  organizationMode: OrganizationMode;
  metadataSource: MetadataSource;
  allowedFormats: string[];
  watch: boolean;
}

const TAKEN_NAMES = ['fiction', 'comics'];
const RESERVED_NAMES = ['all books', 'unshelved'];
const PATH_SEPARATOR = /[\\/]/;

function createInitialModel(): LibraryFormModel {
  return {
    name: '',
    folders: [],
    organizationMode: 'BOOK_PER_FILE',
    metadataSource: 'EMBEDDED',
    allowedFormats: ['EPUB', 'PDF'],
    watch: true,
  };
}

@Component({
  selector: 'app-library-form-example',
  standalone: true,
  host: { class: 'block h-full min-h-0' },
  imports: [
    CdkScrollable,
    JsonPipe,
    FormField,
    FormRoot,
    AppButtonComponent,
    AppFieldComponent,
    AppInputComponent,
    AppMessageComponent,
    AppMultiAutocompleteComponent,
    AppMultiSelectComponent,
    AppRadioGroupComponent,
    AppSelectComponent,
    AppSwitchComponent,
    AppTagComponent,
    LucideChevronLeft,
    LucideSave,
    LucideLoaderCircle,
  ],
  templateUrl: './library-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LibraryFormExampleComponent {
  private readonly router = inject(Router);

  readonly submitState = signal<'idle' | 'saved'>('idle');
  readonly submitAttempted = signal(false);

  readonly model = signal<LibraryFormModel>(createInitialModel());
  readonly libraryForm = form(
    this.model,
    path => {
      required(path.name, { message: 'Give the library a name' });
      minLength(path.name, 2, { message: 'Use at least 2 characters' });
      validate(path.name, ({ value }) => {
        const name = value().trim();
        if (PATH_SEPARATOR.test(name)) return { kind: 'nameFormat', message: 'Name cannot contain / or \\' };
        if (RESERVED_NAMES.includes(name.toLowerCase())) return { kind: 'nameReserved', message: `"${name}" is a reserved name` };
        return null;
      });
      minLength(path.folders, 1, { message: 'Add at least one folder' });
      minLength(path.allowedFormats, 1, { message: 'Allow at least one format' });
      validate(path.watch, ({ value, valueOf }) =>
        value() && valueOf(path.metadataSource) === 'NONE'
          ? { kind: 'watchWithoutMetadata', message: 'Pick a metadata source or turn watching off' }
          : null,
      );
    },
    {
      submission: {
        action: async field => {
          this.submitState.set('idle');
          this.submitAttempted.set(true);
          await delay(900);
          const name = this.model().name.trim();
          if (TAKEN_NAMES.includes(name.toLowerCase())) {
            return [{ fieldTree: field.name, kind: 'server', message: `A library named "${name}" already exists` }];
          }
          this.submitState.set('saved');
          return undefined;
        },
        onInvalid: field => {
          this.submitState.set('idle');
          this.submitAttempted.set(true);
          field().errorSummary()[0]?.fieldTree().focusBoundControl();
        },
      },
    },
  );

  readonly organizationOptions: readonly RadioOption<OrganizationMode>[] = [
    { label: 'One book per file', value: 'BOOK_PER_FILE', description: 'Each file is separate.' },
    { label: 'One book per folder', value: 'BOOK_PER_FOLDER', description: 'Group files by folder.' },
  ];
  readonly metadataSourceOptions: readonly SelectOption<MetadataSource>[] = [
    { label: 'Embedded', value: 'EMBEDDED' },
    { label: 'Sidecar (.opf)', value: 'SIDECAR' },
    { label: 'Prefer sidecar', value: 'PREFER_SIDECAR' },
    { label: 'Prefer embedded', value: 'PREFER_EMBEDDED' },
    { label: 'No metadata', value: 'NONE' },
  ];
  readonly formatOptions: readonly SelectOption<string>[] = [
    { label: 'EPUB', value: 'EPUB' },
    { label: 'PDF', value: 'PDF' },
    { label: 'CBX', value: 'CBX' },
    { label: 'MOBI', value: 'MOBI' },
    { label: 'AZW3', value: 'AZW3' },
    { label: 'FB2', value: 'FB2' },
    { label: 'Audiobook', value: 'AUDIOBOOK' },
  ];

  private readonly allFolders = ['/books', '/books/sci-fi', '/books/fantasy', '/comics', '/audiobooks', '/imports'];
  readonly folderSuggestions = signal<string[]>([]);

  filterFolders(query: string): void {
    const q = query.trim().toLowerCase();
    const chosen = this.model().folders;
    this.folderSuggestions.set(
      q ? this.allFolders.filter(folder => folder.toLowerCase().includes(q) && !chosen.includes(folder)) : [],
    );
  }

  validateAll(): void {
    this.submitState.set('idle');
    this.libraryForm().markAsTouched();
  }

  reset(): void {
    this.submitState.set('idle');
    this.submitAttempted.set(false);
    this.model.set(createInitialModel());
    this.libraryForm().reset();
  }

  back(): void {
    void this.router.navigate(['/design-system']);
  }
}

function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}
