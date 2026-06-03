import {ChangeDetectionStrategy, Component, computed, DestroyRef, EventEmitter, inject, Input, OnChanges, OnInit, Output, signal, SimpleChanges} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {Select} from 'primeng/select';
import {MessageService} from 'primeng/api';
import {Tooltip} from 'primeng/tooltip';
import {
  AGE_RATING_OPTIONS,
  CONTENT_RATINGS,
  ContentRestriction,
  ContentRestrictionMode,
  ContentRestrictionType
} from '../content-restriction.model';
import {ContentRestrictionService} from '../content-restriction.service';
import {BookService} from '../../../book/service/book.service';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-content-restrictions-editor',
  standalone: true,
  imports: [
    FormsModule,
    Button,
    Select,
    Tooltip,
    TranslocoDirective,
    TranslocoPipe
  ],
  templateUrl: './content-restrictions-editor.component.html',
  styleUrls: ['./content-restrictions-editor.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ContentRestrictionsEditorComponent implements OnInit, OnChanges {
  @Input() userId!: number;
  @Input() isEditing = false;
  @Output() restrictionsChanged = new EventEmitter<ContentRestriction[]>();

  private contentRestrictionService = inject(ContentRestrictionService);
  private bookService = inject(BookService);
  private messageService = inject(MessageService);
  private t = inject(TranslocoService);
  private destroyRef = inject(DestroyRef);
  private readonly sortedMetadata = computed(() => {
    const md = this.bookService.uniqueMetadata();
    return {
      categories: [...md.categories].sort(),
      tags: [...md.tags].sort(),
      moods: [...md.moods].sort(),
    };
  });

  readonly restrictions = signal<ContentRestriction[]>([]);
  private readonly excludeRestrictions = computed(() => this.restrictions().filter(r => r.mode === ContentRestrictionMode.EXCLUDE));
  private readonly allowOnlyRestrictions = computed(() => this.restrictions().filter(r => r.mode === ContentRestrictionMode.ALLOW_ONLY));

  get availableCategories(): string[] { return this.sortedMetadata().categories; }
  get availableTags(): string[] { return this.sortedMetadata().tags; }
  get availableMoods(): string[] { return this.sortedMetadata().moods; }

  newRestriction: Partial<ContentRestriction> = {
    restrictionType: ContentRestrictionType.CATEGORY,
    mode: ContentRestrictionMode.EXCLUDE,
    value: ''
  };

  restrictionTypes = [
    {label: 'Category/Genre', value: ContentRestrictionType.CATEGORY, translationKey: 'settingsUsers.contentRestrictions.types.category'},
    {label: 'Tag', value: ContentRestrictionType.TAG, translationKey: 'settingsUsers.contentRestrictions.types.tag'},
    {label: 'Mood', value: ContentRestrictionType.MOOD, translationKey: 'settingsUsers.contentRestrictions.types.mood'},
    {label: 'Age Rating', value: ContentRestrictionType.AGE_RATING, translationKey: 'settingsUsers.contentRestrictions.types.ageRating'},
    {label: 'Content Rating', value: ContentRestrictionType.CONTENT_RATING, translationKey: 'settingsUsers.contentRestrictions.types.contentRating'}
  ];

  restrictionModes = [
    {label: 'Exclude (Hide matching)', value: ContentRestrictionMode.EXCLUDE, translationKey: 'settingsUsers.contentRestrictions.modes.exclude'},
    {label: 'Allow Only (Show only matching)', value: ContentRestrictionMode.ALLOW_ONLY, translationKey: 'settingsUsers.contentRestrictions.modes.allowOnly'}
  ];

  ageRatingOptions = AGE_RATING_OPTIONS;
  contentRatingOptions = CONTENT_RATINGS.map(r => ({label: r, value: r}));

  ngOnInit() {
    this.loadRestrictions();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['userId'] && !changes['userId'].firstChange) {
      this.loadRestrictions();
    }
  }

  loadRestrictions() {
    if (!this.userId) return;
    const requestedUserId = this.userId;

    this.contentRestrictionService.getUserRestrictions(requestedUserId).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (restrictions) => {
        if (this.userId !== requestedUserId) return;
        this.restrictions.set(restrictions);
        this.restrictionsChanged.emit(restrictions);
      },
      error: () => {
        if (this.userId !== requestedUserId) return;
        const restrictions: ContentRestriction[] = [];
        this.restrictions.set(restrictions);
        this.restrictionsChanged.emit(restrictions);
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: this.t.translate('settingsUsers.contentRestrictions.loadError')
        });
      }
    });
  }

  getValueOptions(): {label: string, value: string}[] {
    switch (this.newRestriction.restrictionType) {
      case ContentRestrictionType.CATEGORY:
        return this.availableCategories.map(c => ({label: c, value: c}));
      case ContentRestrictionType.TAG:
        return this.availableTags.map(t => ({label: t, value: t}));
      case ContentRestrictionType.MOOD:
        return this.availableMoods.map(m => ({label: m, value: m}));
      case ContentRestrictionType.AGE_RATING:
        return this.ageRatingOptions.map(o => ({label: o.label, value: o.value}));
      case ContentRestrictionType.CONTENT_RATING:
        return this.contentRatingOptions;
      default:
        return [];
    }
  }

  addRestriction() {
    if (!this.newRestriction.value || !this.newRestriction.restrictionType || !this.newRestriction.mode) {
      this.messageService.add({
        severity: 'warn',
        summary: this.t.translate('common.error'),
        detail: this.t.translate('settingsUsers.contentRestrictions.selectAllFields')
      });
      return;
    }

    const exists = this.restrictions().some(r =>
      r.restrictionType === this.newRestriction.restrictionType &&
      r.value === this.newRestriction.value
    );

    if (exists) {
      this.messageService.add({
        severity: 'warn',
        summary: this.t.translate('common.error'),
        detail: this.t.translate('settingsUsers.contentRestrictions.alreadyExists')
      });
      return;
    }

    const restriction: ContentRestriction = {
      userId: this.userId,
      restrictionType: this.newRestriction.restrictionType!,
      mode: this.newRestriction.mode!,
      value: this.newRestriction.value!
    };
    const requestedUserId = this.userId;

    this.contentRestrictionService.addRestriction(requestedUserId, restriction).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (added) => {
        if (this.userId !== requestedUserId) return;
        const restrictions = [...this.restrictions(), added];
        this.restrictions.set(restrictions);
        this.restrictionsChanged.emit(restrictions);
        this.newRestriction.value = '';
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('common.success'),
          detail: this.t.translate('settingsUsers.contentRestrictions.addSuccess')
        });
      },
      error: () => {
        if (this.userId !== requestedUserId) return;
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: this.t.translate('settingsUsers.contentRestrictions.addError')
        });
      }
    });
  }

  removeRestriction(restriction: ContentRestriction) {
    if (!restriction.id) return;
    const requestedUserId = this.userId;

    this.contentRestrictionService.deleteRestriction(requestedUserId, restriction.id).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: () => {
        if (this.userId !== requestedUserId) return;
        const restrictions = this.restrictions().filter(r => r.id !== restriction.id);
        this.restrictions.set(restrictions);
        this.restrictionsChanged.emit(restrictions);
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('common.success'),
          detail: this.t.translate('settingsUsers.contentRestrictions.removeSuccess')
        });
      },
      error: () => {
        if (this.userId !== requestedUserId) return;
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: this.t.translate('settingsUsers.contentRestrictions.removeError')
        });
      }
    });
  }

  getRestrictionTypeLabel(type: ContentRestrictionType): string {
    const found = this.restrictionTypes.find(t => t.value === type);
    return found ? this.t.translate(found.translationKey) : type;
  }

  getModeLabel(mode: ContentRestrictionMode): string {
    return mode === ContentRestrictionMode.EXCLUDE
      ? this.t.translate('settingsUsers.contentRestrictions.modes.exclude')
      : this.t.translate('settingsUsers.contentRestrictions.modes.allowOnly');
  }

  getModeClass(mode: ContentRestrictionMode): string {
    return mode === ContentRestrictionMode.EXCLUDE ? 'mode-exclude' : 'mode-allow';
  }

  getExcludeRestrictions(): ContentRestriction[] {
    return this.excludeRestrictions();
  }

  getAllowOnlyRestrictions(): ContentRestriction[] {
    return this.allowOnlyRestrictions();
  }
}
