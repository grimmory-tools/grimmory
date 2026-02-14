import {Component, inject, OnInit} from '@angular/core';
import {AbstractControl, FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {Button} from 'primeng/button';
import {NgTemplateOutlet} from '@angular/common';
import {InputText} from 'primeng/inputtext';
import {Select} from 'primeng/select';
import {DatePicker} from 'primeng/datepicker';
import {InputNumber} from 'primeng/inputnumber';
import {ReadStatus} from '../../book/model/book.model';
import {LibraryService} from '../../book/service/library.service';
import {Library} from '../../book/model/library.model';
import {MagicShelfService} from '../service/magic-shelf.service';
import {MessageService} from 'primeng/api';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {MultiSelect} from 'primeng/multiselect';
import {AutoComplete} from 'primeng/autocomplete';
import {EMPTY_CHECK_OPERATORS, MULTI_VALUE_OPERATORS, parseValue, removeNulls, serializeDateRules} from '../service/magic-shelf-utils';
import {IconPickerService, IconSelection} from '../../../shared/service/icon-picker.service';
import {CheckboxChangeEvent, CheckboxModule} from "primeng/checkbox";
import {UserService} from "../../settings/user-management/user.service";
import {IconDisplayComponent} from '../../../shared/components/icon-display/icon-display.component';
import {Tooltip} from 'primeng/tooltip';
import {BookService} from '../../book/service/book.service';
import {ShelfService} from '../../book/service/shelf.service';
import {Shelf} from '../../book/model/shelf.model';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

export type RuleOperator =
  | 'equals'
  | 'not_equals'
  | 'contains'
  | 'does_not_contain'
  | 'starts_with'
  | 'ends_with'
  | 'greater_than'
  | 'greater_than_equal_to'
  | 'less_than'
  | 'less_than_equal_to'
  | 'in_between'
  | 'is_empty'
  | 'is_not_empty'
  | 'includes_any'
  | 'excludes_all'
  | 'includes_all'

export type RuleField =
  | 'library'
  | 'shelf'
  | 'title'
  | 'subtitle'
  | 'authors'
  | 'categories'
  | 'publisher'
  | 'publishedDate'
  | 'seriesName'
  | 'seriesNumber'
  | 'seriesTotal'
  | 'pageCount'
  | 'language'
  | 'isbn13'
  | 'isbn10'
  | 'amazonRating'
  | 'amazonReviewCount'
  | 'goodreadsRating'
  | 'goodreadsReviewCount'
  | 'hardcoverRating'
  | 'hardcoverReviewCount'
  | 'ranobedbRating'
  | 'personalRating'
  | 'fileType'
  | 'fileSize'
  | 'readStatus'
  | 'dateFinished'
  | 'lastReadTime'
  | 'metadataScore'
  | 'moods'
  | 'tags';


interface FullFieldConfig {
  label: string;
  type?: FieldType;
  max?: number;
}

type FieldType = 'number' | 'decimal' | 'date' | undefined;

export interface Rule {
  field: RuleField;
  operator: RuleOperator;
  value: unknown;
  valueStart?: unknown;
  valueEnd?: unknown;
}

export interface FieldConfig {
  type: FieldType;
  max?: number;
}

export interface GroupRule {
  name: string;
  type: 'group';
  join: 'and' | 'or';
  rules: (Rule | GroupRule)[];
}

export type RuleFormGroup = FormGroup<{
  field: FormControl<"" | RuleField | null>;
  operator: FormControl<"" | RuleOperator | null>;
  value: FormControl<string | null>;
  valueStart: FormControl<string | null>;
  valueEnd: FormControl<string | null>;
}>;

export type GroupFormGroup = FormGroup<{
  type: FormControl<'group'>;
  join: FormControl<'and' | 'or'>;
  rules: FormArray<GroupFormGroup | RuleFormGroup>;
}>;

const FIELD_CONFIGS: Record<RuleField, FullFieldConfig> = {
  library: {label: 'library'},
  shelf: {label: 'shelf'},
  readStatus: {label: 'readStatus'},
  dateFinished: {label: 'dateFinished', type: 'date'},
  lastReadTime: {label: 'lastReadTime', type: 'date'},
  metadataScore: {label: 'metadataScore', type: 'decimal', max: 100},
  title: {label: 'title'},
  authors: {label: 'authors'},
  categories: {label: 'categories'},
  moods: {label: 'moods'},
  tags: {label: 'tags'},
  publisher: {label: 'publisher'},
  publishedDate: {label: 'publishedDate', type: 'date'},
  personalRating: {label: 'personalRating', type: 'decimal', max: 10},
  pageCount: {label: 'pageCount', type: 'number'},
  language: {label: 'language'},
  isbn13: {label: 'isbn13'},
  isbn10: {label: 'isbn10'},
  seriesName: {label: 'seriesName'},
  seriesNumber: {label: 'seriesNumber', type: 'number'},
  seriesTotal: {label: 'seriesTotal', type: 'number'},
  fileSize: {label: 'fileSize', type: 'number'},
  fileType: {label: 'fileType'},
  subtitle: {label: 'subtitle'},
  amazonRating: {label: 'amazonRating', type: 'decimal', max: 5},
  amazonReviewCount: {label: 'amazonReviewCount', type: 'number'},
  goodreadsRating: {label: 'goodreadsRating', type: 'decimal', max: 5},
  goodreadsReviewCount: {label: 'goodreadsReviewCount', type: 'number'},
  hardcoverRating: {label: 'hardcoverRating', type: 'decimal', max: 5},
  hardcoverReviewCount: {label: 'hardcoverReviewCount', type: 'number'},
  ranobedbRating: {label: 'ranobedbRating', type: 'decimal', max: 5}
};

const READ_STATUS_KEYS: Record<string, string> = {
  UNREAD: 'unread',
  READING: 'reading',
  RE_READING: 'reReading',
  READ: 'read',
  PARTIALLY_READ: 'partiallyRead',
  PAUSED: 'paused',
  WONT_READ: 'wontRead',
  ABANDONED: 'abandoned',
  UNSET: 'unset'
};

@Component({
  selector: 'app-magic-shelf',
  templateUrl: './magic-shelf-component.html',
  styleUrl: './magic-shelf-component.scss',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    NgTemplateOutlet,
    InputText,
    Select,
    Button,
    DatePicker,
    InputNumber,
    MultiSelect,
    AutoComplete,
    CheckboxModule,
    IconDisplayComponent,
    Tooltip,
    TranslocoDirective
  ]
})
export class MagicShelfComponent implements OnInit {

  private readonly t = inject(TranslocoService);

  numericFieldConfigMap = new Map<RuleField, FieldConfig>(
    Object.entries(FIELD_CONFIGS)
      .filter(([_, config]) => config.type)
      .map(([key, config]) => [key as RuleField, {type: config.type!, max: config.max}])
  );

  get conditionOptions(): { label: string; value: 'and' | 'or' }[] {
    return [
      {label: this.t.translate('magicShelf.conditions.and'), value: 'and'},
      {label: this.t.translate('magicShelf.conditions.or'), value: 'or'},
    ];
  }

  get fieldOptions() {
    return Object.entries(FIELD_CONFIGS).map(([key, config]) => {
      const translationKey = key === 'categories' ? 'genre' : config.label;
      return {
        label: this.t.translate(`magicShelf.fields.${translationKey}`),
        value: key as RuleField
      };
    });
  }

  fileType: { label: string; value: string }[] = [
    {label: 'PDF', value: 'pdf'},
    {label: 'EPUB', value: 'epub'},
    {label: 'CBR', value: 'cbr'},
    {label: 'CBZ', value: 'cbz'},
    {label: 'CB7', value: 'cb7'},
    {label: 'FB2', value: 'fb2'},
    {label: 'MOBI', value: 'mobi'},
    {label: 'AZW3', value: 'azw3'}
  ];

  get readStatusOptions() {
    return Object.entries(ReadStatus).map(([key, value]) => ({
      label: this.t.translate(`magicShelf.readStatuses.${READ_STATUS_KEYS[key]}`),
      value
    }));
  }

  libraries: Library[] = [];
  libraryOptions: { label: string; value: number }[] = [];
  shelves: Shelf[] = [];
  shelfOptions: { label: string; value: number }[] = [];
  categoryOptions: { label: string; value: string }[] = [];

  form = new FormGroup({
    name: new FormControl<string | null>(null),
    icon: new FormControl<string | null>(null),
    isPublic: new FormControl<boolean>(false),
    group: this.createGroup()
  });

  shelfId: number | null = null;
  isAdmin: boolean = false;
  editMode!: boolean;

  libraryService = inject(LibraryService);
  shelfService = inject(ShelfService);
  bookService = inject(BookService);
  magicShelfService = inject(MagicShelfService);
  ref = inject(DynamicDialogRef);
  messageService = inject(MessageService);
  config = inject(DynamicDialogConfig);
  userService = inject(UserService);
  private iconPicker = inject(IconPickerService);

  selectedIcon: IconSelection | null = null;

  trackByFn(ruleCtrl: AbstractControl, index: number): unknown {
    return ruleCtrl;
  }

  ngOnInit(): void {
    this.isAdmin = this.userService.getCurrentUser()?.permissions.admin ?? false;
    const id = this.config?.data?.id;
    this.editMode = !!this.config?.data?.editMode;

    // Load available categories from book metadata
    this.bookService.bookState$.subscribe(state => {
      if (state.loaded && state.books) {
        // Extract unique categories from all books
        const categoriesSet = new Set<string>();
        state.books.forEach(book => {
          if (book.metadata?.categories) {
            book.metadata.categories.forEach(cat => categoriesSet.add(cat));
          }
        });

        this.categoryOptions = Array.from(categoriesSet).map(cat => ({
          label: cat,
          value: cat
        })).sort((a, b) => a.label.localeCompare(b.label));
      }
    });

    if (id) {
      this.shelfId = id;
      this.magicShelfService.getShelf(id).subscribe((data) => {
        const iconValue = data?.icon ?? null;

        this.form = new FormGroup({
          name: new FormControl<string | null>(data?.name ?? null, {nonNullable: true, validators: [Validators.required]}),
          icon: new FormControl<string | null>(iconValue),
          isPublic: new FormControl<boolean>(data?.isPublic ?? false),
          group: data?.filterJson ? this.buildGroupFromData(JSON.parse(data.filterJson)) : this.createGroup()
        });

        if (iconValue) {
          this.selectedIcon = iconValue.startsWith('pi ')
            ? {type: 'PRIME_NG', value: iconValue}
            : {type: 'CUSTOM_SVG', value: iconValue};
        }
      });
    } else {
      this.form = new FormGroup({
        name: new FormControl<string | null>(null, {nonNullable: true, validators: [Validators.required]}),
        icon: new FormControl<string | null>(null),
        isPublic: new FormControl<boolean>(false),
        group: this.createGroup()
      });
    }

    this.libraries = this.libraryService.getLibrariesFromState();
    this.libraryOptions = this.libraries.map(lib => ({
      label: lib.name,
      value: lib.id!
    }));

    this.shelves = this.shelfService.getShelvesFromState();
    this.shelfOptions = this.shelves.map(shelf => ({
      label: shelf.name,
      value: shelf.id!
    }));
  }

  buildGroupFromData(data: GroupRule): GroupFormGroup {
    const rulesArray = new FormArray<FormGroup>([]);

    data.rules.forEach(rule => {
      if ('type' in rule && rule.type === 'group') {
        rulesArray.push(this.buildGroupFromData(rule));
      } else {
        rulesArray.push(this.buildRuleFromData(rule as Rule));
      }
    });

    return new FormGroup({
      type: new FormControl<'group'>('group'),
      join: new FormControl(data.join),
      rules: rulesArray as FormArray<GroupFormGroup | RuleFormGroup>
    }) as GroupFormGroup;
  }

  buildRuleFromData(data: Rule): RuleFormGroup {
    const config = FIELD_CONFIGS[data.field];
    const type = config?.type;

    return new FormGroup({
      field: new FormControl<RuleField>(data.field),
      operator: new FormControl<RuleOperator>(data.operator),
      value: new FormControl(parseValue(data.value, type)),
      valueStart: new FormControl(parseValue(data.valueStart, type)),
      valueEnd: new FormControl(parseValue(data.valueEnd, type)),
    }) as RuleFormGroup;
  }

  get group(): GroupFormGroup {
    return this.form.get('group') as GroupFormGroup;
  }

  getOperatorOptionsForField(field: RuleField | null | undefined) {
    const baseOperators = [
      {label: this.t.translate('magicShelf.operators.equals'), value: 'equals'},
      {label: this.t.translate('magicShelf.operators.notEqual'), value: 'not_equals'},
      {label: this.t.translate('magicShelf.operators.empty'), value: 'is_empty'},
      {label: this.t.translate('magicShelf.operators.notEmpty'), value: 'is_not_empty'},
    ];

    const multiValueOperators = [
      {label: this.t.translate('magicShelf.operators.includesAny'), value: 'includes_any'},
      {label: this.t.translate('magicShelf.operators.excludesAll'), value: 'excludes_all'},
      {label: this.t.translate('magicShelf.operators.includesAll'), value: 'includes_all'},
    ];

    const textOperators = [
      {label: this.t.translate('magicShelf.operators.contains'), value: 'contains'},
      {label: this.t.translate('magicShelf.operators.doesNotContain'), value: 'does_not_contain'},
      {label: this.t.translate('magicShelf.operators.startsWith'), value: 'starts_with'},
      {label: this.t.translate('magicShelf.operators.endsWith'), value: 'ends_with'},
    ];

    const comparisonOperators = [
      {label: this.t.translate('magicShelf.operators.greaterThan'), value: 'greater_than'},
      {label: this.t.translate('magicShelf.operators.greaterOrEqual'), value: 'greater_than_equal_to'},
      {label: this.t.translate('magicShelf.operators.lessThan'), value: 'less_than'},
      {label: this.t.translate('magicShelf.operators.lessOrEqual'), value: 'less_than_equal_to'},
      {label: this.t.translate('magicShelf.operators.between'), value: 'in_between'},
    ];

    if (!field) return [...baseOperators, ...multiValueOperators];

    const config = FIELD_CONFIGS[field];
    const isMultiValueField = ['library', 'shelf', 'authors', 'categories', 'moods', 'tags', 'readStatus', 'fileType', 'language', 'title', 'subtitle', 'publisher', 'seriesName', 'isbn13', 'isbn10'].includes(field);
    const operators = [...baseOperators];

    if (isMultiValueField) {
      operators.push(...multiValueOperators);
    }

    const isTextEligible = !['library', 'shelf', 'readStatus', 'fileType'].includes(field);

    if (config.type === 'number' || config.type === 'decimal' || config.type === 'date') {
      operators.push(...comparisonOperators);
    } else if (isTextEligible) {
      operators.push(...textOperators);
    }

    return operators;
  }

  createRule(): RuleFormGroup {
    return new FormGroup({
      field: new FormControl<RuleField | ''>(''),
      operator: new FormControl<RuleOperator | ''>(''),
      value: new FormControl<string | null>(null),
      valueStart: new FormControl<string | null>(null),
      valueEnd: new FormControl<string | null>(null),
    }) as RuleFormGroup;
  }

  createGroup(): GroupFormGroup {
    return new FormGroup({
      type: new FormControl<'group'>('group' as const),
      join: new FormControl<'and' | 'or'>('and' as 'and' | 'or'),
      rules: new FormArray([] as (GroupFormGroup | RuleFormGroup)[]),
    }) as GroupFormGroup;
  }

  addGroup(group: GroupFormGroup) {
    const rules = group.get('rules') as FormArray;
    rules.push(this.createGroup());
  }

  addRule(group: GroupFormGroup) {
    const rules = group.get('rules') as FormArray;
    rules.push(this.createRule());
  }

  deleteGroup(group: GroupFormGroup) {
    const parent = group.parent;
    if (parent && parent instanceof FormArray) {
      const index = parent.controls.indexOf(group);
      if (index > -1) {
        parent.removeAt(index);
      }
    }
  }

  removeRule(group: GroupFormGroup, index: number) {
    const rules = group.get('rules') as FormArray;
    rules.removeAt(index);
  }

  isGroup(control: AbstractControl): boolean {
    return control instanceof FormGroup && control.get('rules') instanceof FormArray;
  }

  onOperatorChange(ruleCtrl: FormGroup) {
    const operator = ruleCtrl.get('operator')?.value as RuleOperator;

    const valueCtrl = ruleCtrl.get('value');
    const valueStartCtrl = ruleCtrl.get('valueStart');
    const valueEndCtrl = ruleCtrl.get('valueEnd');

    if (MULTI_VALUE_OPERATORS.includes(operator)) {
      valueCtrl?.setValue([]);
      valueStartCtrl?.setValue(null);
      valueEndCtrl?.setValue(null);
    } else if (EMPTY_CHECK_OPERATORS.includes(operator)) {
      valueCtrl?.setValue(null);
      valueStartCtrl?.setValue(null);
      valueEndCtrl?.setValue(null);
    } else {
      valueCtrl?.setValue('');
      valueStartCtrl?.setValue(null);
      valueEndCtrl?.setValue(null);
    }
  }

  onFieldChange(ruleCtrl: RuleFormGroup) {
    ruleCtrl.get('operator')?.setValue('');
    ruleCtrl.get('value')?.setValue(null);
    ruleCtrl.get('valueStart')?.setValue(null);
    ruleCtrl.get('valueEnd')?.setValue(null);
  }

  openIconPicker() {
    this.iconPicker.open().subscribe(icon => {
      if (icon) {
        this.selectedIcon = icon;
        const iconValue = icon.type === 'CUSTOM_SVG'
          ? icon.value
          : icon.value;
        this.form.get('icon')?.setValue(iconValue);
      }
    });
  }

  clearSelectedIcon(): void {
    this.selectedIcon = null;
    this.form.get('icon')?.setValue(null);
  }

  private hasAtLeastOneValidRule(group: GroupFormGroup): boolean {
    const rulesArray = group.get('rules') as FormArray;

    return rulesArray.controls.some(ctrl => {
      const type = (ctrl.get('type') as FormControl<'group'> | null)?.value;

      if (type === 'group') {
        return this.hasAtLeastOneValidRule(ctrl as GroupFormGroup);
      } else {
        const field = ctrl.get('field')?.value;
        const operator = ctrl.get('operator')?.value;
        return !!field && !!operator;
      }
    });
  }

  onAutoCompleteBlur(formControl: { value: unknown; setValue: (value: unknown[]) => void }, event: Event) {
    const target = event.target as HTMLInputElement | null;
    const inputValue = target?.value?.trim();
    if (inputValue) {
      const currentValue = formControl.value || [];
      const values = Array.isArray(currentValue) ? currentValue :
        typeof currentValue === 'string' && currentValue ? currentValue.split(',').map((v: string) => v.trim()) : [];

      if (!values.includes(inputValue)) {
        values.push(inputValue);
        formControl.setValue(values);
      }
      if (target) {
        target.value = '';
      }
    }
  }

  onIsPublicChange(event: CheckboxChangeEvent): void {
    const checked = event.checked ?? false;
    this.form.get('isPublic')?.setValue(checked);
  }

  submit() {
    if (!this.hasAtLeastOneValidRule(this.group)) {
      this.messageService.add({severity: 'warn', summary: this.t.translate('magicShelf.toast.validationErrorSummary'), detail: this.t.translate('magicShelf.toast.validationErrorDetail')});
      return;
    }

    const value = this.form.value as { name: string | null; icon: string | null; group: GroupRule, isPublic: boolean | null };
    const cleanedGroup = removeNulls(serializeDateRules(value.group));

    this.magicShelfService.saveShelf({
      id: this.shelfId ?? undefined,
      name: value.name,
      icon: value.icon,
      iconType: this.selectedIcon?.type,
      isPublic: !!value.isPublic,
      group: cleanedGroup
    }).subscribe({
      next: (savedShelf) => {
        this.messageService.add({severity: 'success', summary: this.t.translate('magicShelf.toast.successSummary'), detail: this.t.translate('magicShelf.toast.successDetail')});
        if (savedShelf?.id) {
          this.shelfId = savedShelf.id;
          this.form.patchValue({
            name: savedShelf.name,
            icon: savedShelf.icon,
            isPublic: savedShelf.isPublic
          });
        }
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('magicShelf.toast.errorSummary'),
          detail: err?.error?.message || this.t.translate('magicShelf.toast.errorDetailDefault')
        });
      }
    });
  }

  cancel() {
    this.ref.close();
  }
}
