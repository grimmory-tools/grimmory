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
import {EMPTY_CHECK_OPERATORS, MULTI_VALUE_OPERATORS, RELATIVE_DATE_OPERATORS, parseValue, removeNulls, serializeDateRules} from '../service/magic-shelf-utils';
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
  | 'within_last'
  | 'older_than'
  | 'this_period'

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
  | 'tags'
  | 'addedOn'
  | 'lubimyczytacRating'
  | 'description'
  | 'narrator'
  | 'ageRating'
  | 'contentRating'
  | 'audibleRating'
  | 'audibleReviewCount'
  | 'abridged'
  | 'audiobookDuration'
  | 'isPhysical'
  | 'seriesStatus'
  | 'seriesGaps'
  | 'seriesPosition'
  | 'readingProgress'
  | 'metadataPresence';


interface FullFieldConfig {
  label: string;
  type?: FieldType;
  max?: number;
}

type FieldType = 'number' | 'decimal' | 'date' | 'boolean' | undefined;

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
  ranobedbRating: {label: 'ranobedbRating', type: 'decimal', max: 5},
  addedOn: {label: 'addedOn', type: 'date'},
  lubimyczytacRating: {label: 'lubimyczytacRating', type: 'decimal', max: 5},
  description: {label: 'description'},
  narrator: {label: 'narrator'},
  ageRating: {label: 'ageRating', type: 'number'},
  contentRating: {label: 'contentRating'},
  audibleRating: {label: 'audibleRating', type: 'decimal', max: 5},
  audibleReviewCount: {label: 'audibleReviewCount', type: 'number'},
  abridged: {label: 'abridged', type: 'boolean'},
  audiobookDuration: {label: 'audiobookDuration', type: 'number'},
  isPhysical: {label: 'isPhysical', type: 'boolean'},
  seriesStatus: {label: 'seriesStatus'},
  seriesGaps: {label: 'seriesGaps'},
  seriesPosition: {label: 'seriesPosition'},
  readingProgress: {label: 'readingProgress', type: 'decimal', max: 100},
  metadataPresence: {label: 'metadataPresence'}
};

interface FieldGroup {
  translationKey: string;
  fields: RuleField[];
}

const FIELD_GROUPS: FieldGroup[] = [
  { translationKey: 'organization', fields: ['library', 'shelf', 'readStatus', 'readingProgress'] },
  { translationKey: 'bookInfo', fields: ['title', 'subtitle', 'description', 'authors', 'categories', 'publisher', 'language', 'pageCount', 'ageRating', 'contentRating'] },
  { translationKey: 'series', fields: ['seriesName', 'seriesNumber', 'seriesTotal', 'seriesStatus', 'seriesGaps', 'seriesPosition'] },
  { translationKey: 'dates', fields: ['publishedDate', 'dateFinished', 'lastReadTime', 'addedOn'] },
  { translationKey: 'ratingsReviews', fields: ['personalRating', 'amazonRating', 'amazonReviewCount', 'goodreadsRating', 'goodreadsReviewCount', 'hardcoverRating', 'hardcoverReviewCount', 'ranobedbRating', 'lubimyczytacRating', 'audibleRating', 'audibleReviewCount'] },
  { translationKey: 'qualityMetadata', fields: ['metadataScore', 'metadataPresence'] },
  { translationKey: 'tagsMoods', fields: ['moods', 'tags'] },
  { translationKey: 'audiobook', fields: ['narrator', 'abridged', 'audiobookDuration'] },
  { translationKey: 'fileIdentifiers', fields: ['fileType', 'fileSize', 'isbn13', 'isbn10', 'isPhysical'] }
];

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
    return FIELD_GROUPS
      .map(group => ({
        label: this.t.translate(`magicShelf.fieldGroups.${group.translationKey}`),
        items: group.fields.map(field => {
          const config = FIELD_CONFIGS[field];
          const translationKey = field === 'categories' ? 'genre' : config.label;
          return {
            label: this.t.translate(`magicShelf.fields.${translationKey}`),
            value: field
          };
        })
      }))
      .filter(group => group.items.length > 0);
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

  get booleanOptions() {
    return [
      {label: this.t.translate('magicShelf.booleanValues.yes'), value: 'true'},
      {label: this.t.translate('magicShelf.booleanValues.no'), value: 'false'},
    ];
  }

  get contentRatingOptions() {
    return [
      {label: this.t.translate('magicShelf.contentRatings.everyone'), value: 'EVERYONE'},
      {label: this.t.translate('magicShelf.contentRatings.teen'), value: 'TEEN'},
      {label: this.t.translate('magicShelf.contentRatings.mature'), value: 'MATURE'},
      {label: this.t.translate('magicShelf.contentRatings.adult'), value: 'ADULT'},
      {label: this.t.translate('magicShelf.contentRatings.explicit'), value: 'EXPLICIT'},
    ];
  }

  get seriesStatusOptions() {
    return [
      {label: this.t.translate('magicShelf.seriesStatuses.reading'), value: 'reading'},
      {label: this.t.translate('magicShelf.seriesStatuses.completed'), value: 'completed'},
      {label: this.t.translate('magicShelf.seriesStatuses.ongoing'), value: 'ongoing'},
      {label: this.t.translate('magicShelf.seriesStatuses.notStarted'), value: 'not_started'},
      {label: this.t.translate('magicShelf.seriesStatuses.fullyRead'), value: 'fully_read'},
    ];
  }

  get seriesGapsOptions() {
    return [
      {label: this.t.translate('magicShelf.seriesGaps.anyGap'), value: 'any_gap'},
      {label: this.t.translate('magicShelf.seriesGaps.missingFirst'), value: 'missing_first'},
      {label: this.t.translate('magicShelf.seriesGaps.missingLatest'), value: 'missing_latest'},
      {label: this.t.translate('magicShelf.seriesGaps.duplicateNumber'), value: 'duplicate_number'},
    ];
  }

  get seriesPositionOptions() {
    return [
      {label: this.t.translate('magicShelf.seriesPositions.nextUnread'), value: 'next_unread'},
      {label: this.t.translate('magicShelf.seriesPositions.firstInSeries'), value: 'first_in_series'},
      {label: this.t.translate('magicShelf.seriesPositions.lastInSeries'), value: 'last_in_series'},
    ];
  }

  get metadataPresenceOptions() {
    return [
      { label: this.t.translate('magicShelf.metadataFieldGroups.bookInfo'), items: [
        {label: this.t.translate('magicShelf.metadataFields.title'), value: 'title'},
        {label: this.t.translate('magicShelf.metadataFields.subtitle'), value: 'subtitle'},
        {label: this.t.translate('magicShelf.metadataFields.description'), value: 'description'},
        {label: this.t.translate('magicShelf.metadataFields.thumbnailUrl'), value: 'thumbnailUrl'},
        {label: this.t.translate('magicShelf.metadataFields.publisher'), value: 'publisher'},
        {label: this.t.translate('magicShelf.metadataFields.publishedDate'), value: 'publishedDate'},
        {label: this.t.translate('magicShelf.metadataFields.language'), value: 'language'},
        {label: this.t.translate('magicShelf.metadataFields.pageCount'), value: 'pageCount'},
      ]},
      { label: this.t.translate('magicShelf.metadataFieldGroups.authorsCategories'), items: [
        {label: this.t.translate('magicShelf.metadataFields.authors'), value: 'authors'},
        {label: this.t.translate('magicShelf.metadataFields.categories'), value: 'categories'},
        {label: this.t.translate('magicShelf.metadataFields.moods'), value: 'moods'},
        {label: this.t.translate('magicShelf.metadataFields.tags'), value: 'tags'},
      ]},
      { label: this.t.translate('magicShelf.metadataFieldGroups.series'), items: [
        {label: this.t.translate('magicShelf.metadataFields.seriesName'), value: 'seriesName'},
        {label: this.t.translate('magicShelf.metadataFields.seriesNumber'), value: 'seriesNumber'},
        {label: this.t.translate('magicShelf.metadataFields.seriesTotal'), value: 'seriesTotal'},
      ]},
      { label: this.t.translate('magicShelf.metadataFieldGroups.identifiers'), items: [
        {label: this.t.translate('magicShelf.metadataFields.isbn13'), value: 'isbn13'},
        {label: this.t.translate('magicShelf.metadataFields.isbn10'), value: 'isbn10'},
        {label: this.t.translate('magicShelf.metadataFields.asin'), value: 'asin'},
      ]},
      { label: this.t.translate('magicShelf.metadataFieldGroups.contentClassification'), items: [
        {label: this.t.translate('magicShelf.metadataFields.ageRating'), value: 'ageRating'},
        {label: this.t.translate('magicShelf.metadataFields.contentRating'), value: 'contentRating'},
      ]},
      { label: this.t.translate('magicShelf.metadataFieldGroups.ratings'), items: [
        {label: this.t.translate('magicShelf.metadataFields.personalRating'), value: 'personalRating'},
        {label: this.t.translate('magicShelf.metadataFields.amazonRating'), value: 'amazonRating'},
        {label: this.t.translate('magicShelf.metadataFields.goodreadsRating'), value: 'goodreadsRating'},
        {label: this.t.translate('magicShelf.metadataFields.hardcoverRating'), value: 'hardcoverRating'},
        {label: this.t.translate('magicShelf.metadataFields.ranobedbRating'), value: 'ranobedbRating'},
        {label: this.t.translate('magicShelf.metadataFields.lubimyczytacRating'), value: 'lubimyczytacRating'},
        {label: this.t.translate('magicShelf.metadataFields.audibleRating'), value: 'audibleRating'},
      ]},
      { label: this.t.translate('magicShelf.metadataFieldGroups.reviewCounts'), items: [
        {label: this.t.translate('magicShelf.metadataFields.amazonReviewCount'), value: 'amazonReviewCount'},
        {label: this.t.translate('magicShelf.metadataFields.goodreadsReviewCount'), value: 'goodreadsReviewCount'},
        {label: this.t.translate('magicShelf.metadataFields.hardcoverReviewCount'), value: 'hardcoverReviewCount'},
        {label: this.t.translate('magicShelf.metadataFields.audibleReviewCount'), value: 'audibleReviewCount'},
      ]},
      { label: this.t.translate('magicShelf.metadataFieldGroups.externalIds'), items: [
        {label: this.t.translate('magicShelf.metadataFields.goodreadsId'), value: 'goodreadsId'},
        {label: this.t.translate('magicShelf.metadataFields.hardcoverId'), value: 'hardcoverId'},
        {label: this.t.translate('magicShelf.metadataFields.googleId'), value: 'googleId'},
        {label: this.t.translate('magicShelf.metadataFields.audibleId'), value: 'audibleId'},
        {label: this.t.translate('magicShelf.metadataFields.lubimyczytacId'), value: 'lubimyczytacId'},
        {label: this.t.translate('magicShelf.metadataFields.ranobedbId'), value: 'ranobedbId'},
        {label: this.t.translate('magicShelf.metadataFields.comicvineId'), value: 'comicvineId'},
      ]},
      { label: this.t.translate('magicShelf.metadataFieldGroups.audiobook'), items: [
        {label: this.t.translate('magicShelf.metadataFields.narrator'), value: 'narrator'},
        {label: this.t.translate('magicShelf.metadataFields.abridged'), value: 'abridged'},
        {label: this.t.translate('magicShelf.metadataFields.audiobookDuration'), value: 'audiobookDuration'},
      ]},
      { label: this.t.translate('magicShelf.metadataFieldGroups.comic'), items: [
        {label: this.t.translate('magicShelf.metadataFields.comicCharacters'), value: 'comicCharacters'},
        {label: this.t.translate('magicShelf.metadataFields.comicTeams'), value: 'comicTeams'},
        {label: this.t.translate('magicShelf.metadataFields.comicLocations'), value: 'comicLocations'},
        {label: this.t.translate('magicShelf.metadataFields.comicPencillers'), value: 'comicPencillers'},
        {label: this.t.translate('magicShelf.metadataFields.comicInkers'), value: 'comicInkers'},
        {label: this.t.translate('magicShelf.metadataFields.comicColorists'), value: 'comicColorists'},
        {label: this.t.translate('magicShelf.metadataFields.comicLetterers'), value: 'comicLetterers'},
        {label: this.t.translate('magicShelf.metadataFields.comicCoverArtists'), value: 'comicCoverArtists'},
        {label: this.t.translate('magicShelf.metadataFields.comicEditors'), value: 'comicEditors'},
      ]},
    ];
  }

  get dateUnitOptions() {
    return [
      {label: this.t.translate('magicShelf.dateUnits.days'), value: 'days'},
      {label: this.t.translate('magicShelf.dateUnits.weeks'), value: 'weeks'},
      {label: this.t.translate('magicShelf.dateUnits.months'), value: 'months'},
      {label: this.t.translate('magicShelf.dateUnits.years'), value: 'years'},
    ];
  }

  get datePeriodOptions() {
    return [
      {label: this.t.translate('magicShelf.datePeriods.week'), value: 'week'},
      {label: this.t.translate('magicShelf.datePeriods.month'), value: 'month'},
      {label: this.t.translate('magicShelf.datePeriods.year'), value: 'year'},
    ];
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
    const isRelativeDate = RELATIVE_DATE_OPERATORS.includes(data.operator);

    let value, valueStart, valueEnd;
    if (isRelativeDate) {
      value = data.operator === 'this_period' ? data.value : parseValue(data.value, 'number');
      valueStart = null;
      valueEnd = data.operator !== 'this_period' ? data.valueEnd : null;
    } else {
      value = parseValue(data.value, type);
      valueStart = parseValue(data.valueStart, type);
      valueEnd = parseValue(data.valueEnd, type);
    }

    return new FormGroup({
      field: new FormControl<RuleField>(data.field),
      operator: new FormControl<RuleOperator>(data.operator),
      value: new FormControl(value),
      valueStart: new FormControl(valueStart),
      valueEnd: new FormControl(valueEnd),
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

    const relativeDateOperators = [
      {label: this.t.translate('magicShelf.operators.withinLast'), value: 'within_last'},
      {label: this.t.translate('magicShelf.operators.olderThan'), value: 'older_than'},
      {label: this.t.translate('magicShelf.operators.thisPeriod'), value: 'this_period'},
    ];

    if (!field) return [...baseOperators, ...multiValueOperators];

    const config = FIELD_CONFIGS[field];

    if (config.type === 'boolean') {
      return baseOperators;
    }

    // Composite fields: only is/isNot or has/hasNot
    if (field === 'seriesStatus' || field === 'seriesPosition') {
      return [
        {label: this.t.translate('magicShelf.operators.is'), value: 'equals'},
        {label: this.t.translate('magicShelf.operators.isNot'), value: 'not_equals'},
      ];
    }
    if (field === 'seriesGaps' || field === 'metadataPresence') {
      return [
        {label: this.t.translate('magicShelf.operators.has'), value: 'equals'},
        {label: this.t.translate('magicShelf.operators.hasNot'), value: 'not_equals'},
      ];
    }

    const isMultiValueField = ['library', 'shelf', 'authors', 'categories', 'moods', 'tags', 'readStatus', 'fileType', 'language', 'title', 'subtitle', 'publisher', 'seriesName', 'isbn13', 'isbn10', 'contentRating', 'narrator', 'description'].includes(field);
    const operators = [...baseOperators];

    if (isMultiValueField) {
      operators.push(...multiValueOperators);
    }

    const isTextEligible = !['library', 'shelf', 'readStatus', 'fileType', 'contentRating'].includes(field);

    if (config.type === 'date') {
      operators.push(...comparisonOperators, ...relativeDateOperators);
    } else if (config.type === 'number' || config.type === 'decimal') {
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

    if (operator === 'within_last' || operator === 'older_than') {
      valueCtrl?.setValue(null);
      valueStartCtrl?.setValue(null);
      valueEndCtrl?.setValue('days');
    } else if (operator === 'this_period') {
      valueCtrl?.setValue(null);
      valueStartCtrl?.setValue(null);
      valueEndCtrl?.setValue(null);
    } else if (MULTI_VALUE_OPERATORS.includes(operator)) {
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
