import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CdkScrollable } from '@angular/cdk/scrolling';
import { Router } from '@angular/router';
import { email, form, FormField, minLength, required, validate } from '@angular/forms/signals';
import { MenuItem } from 'primeng/api';
import {
  LucideChevronLeft,
  LucideDownload,
  LucideEllipsisVertical,
  LucideFile,
  LucideFolder,
  LucideFolderOpen,
  LucideHeart,
  LucideList,
  LucideSave,
  LucideSearch,
  LucideSettings,
  LucideSmartphone,
  LucideTag,
  LucideTrash2,
  LucideTriangleAlert,
  LucideX,
  LucideZap,
} from '@lucide/angular';
import { AppAccordionComponent } from '../../shared/ui/accordion/app-accordion.component';
import {
  AppAccordionActionsDirective,
  AppAccordionContentDirective,
  AppAccordionHeaderDirective,
} from '../../shared/ui/accordion/app-accordion.directives';
import { AppAutocompleteComponent } from '../../shared/ui/autocomplete/app-autocomplete.component';
import { AppMultiAutocompleteComponent } from '../../shared/ui/autocomplete/app-multi-autocomplete.component';
import { AppButtonComponent } from '../../shared/ui/button/app-button.component';
import { type ButtonSize, type ButtonTone, type ButtonVariant } from '../../shared/ui/button/app-button.variants';
import { AppCheckboxComponent } from '../../shared/ui/checkbox/app-checkbox.component';
import { AppDatePickerComponent } from '../../shared/ui/date-picker/app-date-picker.component';
import { AppDateRangePickerComponent, type DatePickerRange } from '../../shared/ui/date-picker/app-date-range-picker.component';
import { AppFieldComponent } from '../../shared/ui/field/app-field.component';
import { AppInputComponent } from '../../shared/ui/input/app-input.component';
import { AppMenuComponent } from '../../shared/ui/menu/app-menu.component';
import { AppMessageComponent } from '../../shared/ui/message/app-message.component';
import { AppNumberInputComponent } from '../../shared/ui/number-input/app-number-input.component';
import { appMenuSection, appMenuSeparator } from '../../shared/ui/menu/app-menu.items';
import { AppRadioGroupComponent, type RadioOption } from '../../shared/ui/radio-group/app-radio-group.component';
import { AppRatingComponent } from '../../shared/ui/rating/app-rating.component';
import { AppMultiSelectComponent } from '../../shared/ui/select/app-multi-select.component';
import { AppSelectComponent } from '../../shared/ui/select/app-select.component';
import { type SelectOption, type SelectOptionGroup } from '../../shared/ui/select/app-select.options';
import { AppSelectOptionTemplateDirective, AppSelectSelectedTemplateDirective } from '../../shared/ui/select/app-select.templates';
import { AppSliderComponent } from '../../shared/ui/slider/app-slider.component';
import { AppSpinnerComponent } from '../../shared/ui/spinner/app-spinner.component';
import { AppSplitButtonComponent } from '../../shared/ui/split-button/app-split-button.component';
import { AppSwitchComponent } from '../../shared/ui/switch/app-switch.component';
import { AppTabsComponent, type TabItem } from '../../shared/ui/tabs/app-tabs.component';
import { AppTagComponent } from '../../shared/ui/tag/app-tag.component';
import { AppTextareaComponent } from '../../shared/ui/textarea/app-textarea.component';
import { AppTooltipDirective } from '../../shared/ui/tooltip/app-tooltip.directive';

interface ButtonExample {
  tone: ButtonTone;
  variant: ButtonVariant;
}

@Component({
  selector: 'app-design-system',
  standalone: true,
  imports: [
    CdkScrollable,
    AppAccordionComponent,
    AppAccordionHeaderDirective,
    AppAccordionContentDirective,
    AppAccordionActionsDirective,
    AppAutocompleteComponent,
    AppMultiAutocompleteComponent,
    AppButtonComponent,
    AppCheckboxComponent,
    AppDatePickerComponent,
    AppDateRangePickerComponent,
    AppFieldComponent,
    AppInputComponent,
    AppMenuComponent,
    AppMessageComponent,
    AppNumberInputComponent,
    AppRadioGroupComponent,
    AppRatingComponent,
    AppMultiSelectComponent,
    AppSelectComponent,
    AppSelectOptionTemplateDirective,
    AppSelectSelectedTemplateDirective,
    AppSliderComponent,
    AppSpinnerComponent,
    AppSplitButtonComponent,
    AppSwitchComponent,
    AppTabsComponent,
    AppTagComponent,
    AppTextareaComponent,
    AppTooltipDirective,
    LucideFolder,
    LucideSmartphone,
    LucideList,
    LucideZap,
    LucideChevronLeft,
    LucideHeart,
    LucideSave,
    LucideSettings,
    LucideEllipsisVertical,
    LucideFolderOpen,
    LucideDownload,
    LucideTrash2,
    LucideSearch,
    LucideX,
    LucideTriangleAlert,
    FormField,
  ],
  templateUrl: './design-system.component.html',
  styleUrl: './design-system.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DesignSystemComponent {
  private readonly router = inject(Router);

  openExample(path: string): void {
    void this.router.navigate(['/design-system', 'form', path]);
  }

  readonly buttonExamples: ButtonExample[] = [
    { tone: 'neutral', variant: 'soft' },
    { tone: 'neutral', variant: 'solid' },
    { tone: 'neutral', variant: 'ghost' },
    { tone: 'primary', variant: 'soft' },
    { tone: 'primary', variant: 'solid' },
    { tone: 'primary', variant: 'ghost' },
    { tone: 'danger', variant: 'soft' },
    { tone: 'danger', variant: 'solid' },
    { tone: 'danger', variant: 'ghost' },
  ];
  readonly buttonSizes: ButtonSize[] = ['sm', 'md', 'lg'];
  readonly menuItems: MenuItem[] = [
    appMenuSection('Actions'),
    { label: 'Rename' },
    { label: 'Duplicate' },
    appMenuSeparator(),
    appMenuSection('Danger'),
    { label: 'Delete', styleClass: 'app-menu-item-destructive' },
  ];
  readonly selectOptions: SelectOption<string>[] = [
    { label: 'Option A', value: 'a' },
    { label: 'Option B', value: 'b' },
    { label: 'Option C', value: 'c' },
  ];
  readonly groupedSelectOptions: SelectOptionGroup<string>[] = [
    {
      label: 'Group A',
      options: [
        { label: 'Option A', value: 'a' },
        { label: 'Option B', value: 'b' },
      ],
    },
    {
      label: 'Group B',
      options: [
        { label: 'Option C', value: 'c' },
        { label: 'Option D', value: 'd' },
      ],
    },
  ];
  readonly themeSelectOptions = [
    { label: 'Green', value: 'green', color: 'var(--color-green-500)' },
    { label: 'Blue', value: 'blue', color: 'var(--color-blue-500)' },
    { label: 'Rose', value: 'rose', color: 'var(--color-rose-500)' },
  ];

  readonly settingsTabs: TabItem[] = [
    { id: 'general', label: 'General', icon: LucideSettings.icon },
    { id: 'metadata', label: 'Metadata', icon: LucideTag.icon },
    { id: 'formats', label: 'Formats', icon: LucideFile.icon },
  ];
  readonly manyTabs: TabItem[] = [
    { id: 'general', label: 'General' },
    { id: 'metadata', label: 'Metadata' },
    { id: 'formats', label: 'Formats' },
    { id: 'readers', label: 'Readers' },
    { id: 'integrations', label: 'Integrations' },
    { id: 'notifications', label: 'Notifications' },
    { id: 'security', label: 'Security' },
    { id: 'advanced', label: 'Advanced' },
  ];
  readonly underlineTab = signal<string | undefined>('general');
  readonly segmentedTab = signal<string | undefined>('general');
  readonly segmentedTabSm = signal<string | undefined>('general');
  readonly segmentedTabLg = signal<string | undefined>('general');
  readonly manyTab = signal<string | undefined>('general');

  readonly radioViewOptions: RadioOption<string>[] = [
    { label: 'List', value: 'list' },
    { label: 'Grid', value: 'grid' },
    { label: 'Cards', value: 'cards' },
  ];
  readonly radioDensityOptions: RadioOption<string>[] = [
    { label: 'Compact', value: 'compact', description: 'Tighter rows.' },
    { label: 'Comfortable', value: 'comfortable', description: 'Default spacing.' },
    { label: 'Spacious', value: 'spacious', description: 'Larger gaps.' },
  ];

  inputValue = 'Example title';
  autocompleteValue = '';
  readonly autocompleteOptions = [
    'Alpha',
    'Beta',
    'Gamma',
    'Delta',
    'Epsilon',
    'Zeta',
  ];
  readonly filteredAutocompleteOptions = signal<string[]>([]);
  tags: string[] = ['Tag one'];
  readonly allTags = ['Tag one', 'Tag two', 'Tag three', 'Tag four', 'Tag five'];
  readonly filteredTags = signal<string[]>([]);
  readonly accordionItems = [
    { title: 'General', body: 'General content.' },
    { title: 'Details', body: 'Details content.' },
    { title: 'Settings', body: 'Settings content.' },
  ];
  accordionValue: number | null = 0;
  accordionMultiValue: number[] = [0];
  fontSizeValue: number | null = 16;
  weightValue: number | null = 5;
  aspectRatioValue = 2.1;
  compressionValue = 70;
  personalRating = 3.5;
  ratingValue = 4;
  checkboxAccepted = true;
  switchEnabled = true;
  selectValue: string | null = 'a';
  multiSelectValue: string[] = ['a', 'c'];
  themeValue: string | null = 'green';
  textareaValue = 'Example notes';
  radioViewValue: string | null = 'list';
  radioDensityValue: string | null = 'comfortable';
  dateValue = '';
  dateRangeValue: DatePickerRange = { start: '', end: '' };

  readonly profileModel = signal<{
    displayName: string;
    email: string;
    bio: string;
    viewMode: string | null;
    birthday: string;
  }>({ displayName: '', email: '', bio: '', viewMode: null, birthday: '' });
  readonly profileForm = form(this.profileModel, (path) => {
    required(path.displayName, { message: 'Display name is required' });
    email(path.email, { message: 'Enter a valid email' });
    minLength(path.bio, 10, { message: 'Use at least 10 characters' });
    required(path.viewMode, { message: 'Choose an option' });
    required(path.birthday, { message: 'Pick a date' });
  });

  readonly syncModel = signal({ sync: { enabled: true, host: '' } });
  readonly syncForm = form(this.syncModel, (path) => {
    validate(path.sync, ({ value }) =>
      value().enabled && !value().host
        ? { kind: 'valueRequired', message: 'Enter a value to continue' }
        : null,
    );
  });

  validateSyncGroup(): void {
    this.syncForm.sync().markAsTouched();
  }

  validateProfile(): void {
    this.profileForm().markAsTouched();
  }

  filterAutocompleteOptions(query: string): void {
    const q = query.trim().toLowerCase();
    this.filteredAutocompleteOptions.set(
      q ? this.autocompleteOptions.filter((option) => option.toLowerCase().includes(q)) : [],
    );
  }

  filterTags(query: string): void {
    const q = query.trim().toLowerCase();
    this.filteredTags.set(q ? this.allTags.filter((tag) => tag.toLowerCase().includes(q)) : []);
  }
}
