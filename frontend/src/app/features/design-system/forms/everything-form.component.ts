import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { JsonPipe } from '@angular/common';
import { CdkScrollable } from '@angular/cdk/scrolling';
import { Router } from '@angular/router';
import { form, FormField, FormRoot, max, min, minLength, required, validate } from '@angular/forms/signals';
import { AppButtonComponent } from '../../../shared/ui/button/app-button.component';
import { AppFieldComponent } from '../../../shared/ui/field/app-field.component';
import { AppInputComponent } from '../../../shared/ui/input/app-input.component';
import { AppTextareaComponent } from '../../../shared/ui/textarea/app-textarea.component';
import { AppNumberInputComponent } from '../../../shared/ui/number-input/app-number-input.component';
import { AppSelectComponent } from '../../../shared/ui/select/app-select.component';
import { AppMultiSelectComponent } from '../../../shared/ui/select/app-multi-select.component';
import { AppAutocompleteComponent } from '../../../shared/ui/autocomplete/app-autocomplete.component';
import { AppMultiAutocompleteComponent } from '../../../shared/ui/autocomplete/app-multi-autocomplete.component';
import { AppCheckboxComponent } from '../../../shared/ui/checkbox/app-checkbox.component';
import { AppSwitchComponent } from '../../../shared/ui/switch/app-switch.component';
import { AppRadioGroupComponent, type RadioOption } from '../../../shared/ui/radio-group/app-radio-group.component';
import { AppSliderComponent } from '../../../shared/ui/slider/app-slider.component';
import { AppRatingComponent } from '../../../shared/ui/rating/app-rating.component';
import { AppDatePickerComponent } from '../../../shared/ui/date-picker/app-date-picker.component';
import { AppDateRangePickerComponent, type DatePickerRange } from '../../../shared/ui/date-picker/app-date-range-picker.component';
import { AppMessageComponent } from '../../../shared/ui/message/app-message.component';
import { AppTagComponent } from '../../../shared/ui/tag/app-tag.component';
import { type SelectOption } from '../../../shared/ui/select/app-select.options';

interface EverythingFormModel {
  text: string;
  textarea: string;
  number: number | null;
  select: string;
  multiSelect: string[];
  autocomplete: string;
  multiAutocomplete: string[];
  checkbox: boolean;
  switch: boolean;
  radio: string;
  slider: number;
  rating: number;
  date: string;
  dateRange: DatePickerRange;
}

function createInitialModel(): EverythingFormModel {
  return {
    text: '',
    textarea: '',
    number: null,
    select: '',
    multiSelect: [],
    autocomplete: '',
    multiAutocomplete: [],
    checkbox: false,
    switch: false,
    radio: '',
    slider: 50,
    rating: 0,
    date: '',
    dateRange: { start: '', end: '' },
  };
}

@Component({
  selector: 'app-everything-form-example',
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
    AppTextareaComponent,
    AppNumberInputComponent,
    AppSelectComponent,
    AppMultiSelectComponent,
    AppAutocompleteComponent,
    AppMultiAutocompleteComponent,
    AppCheckboxComponent,
    AppSwitchComponent,
    AppRadioGroupComponent,
    AppSliderComponent,
    AppRatingComponent,
    AppDatePickerComponent,
    AppDateRangePickerComponent,
    AppMessageComponent,
    AppTagComponent,
  ],
  providers: [
    {
      provide: APP_FIELD_ERROR_REVEAL,
      useFactory: (form: EverythingFormExampleComponent) => form.submitAttempted,
      deps: [forwardRef(() => EverythingFormExampleComponent)],
    },
  ],
  templateUrl: './everything-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EverythingFormExampleComponent {
  private readonly router = inject(Router);

  readonly model = signal<EverythingFormModel>(createInitialModel());
  readonly everythingForm = form(this.model, path => {
    required(path.text, { message: 'Required' });
    required(path.textarea, { message: 'Required' });
    required(path.number, { message: 'Enter a number' });
    min(path.number, 0, { message: '0 or more' });
    max(path.number, 100, { message: '100 or less' });
    min(path.slider, 0);
    max(path.slider, 100);
    required(path.select, { message: 'Pick an option' });
    required(path.autocomplete, { message: 'Pick or type a value' });
    required(path.radio, { message: 'Choose one' });
    required(path.date, { message: 'Pick a date' });
    minLength(path.multiSelect, 1, { message: 'Pick at least one' });
    minLength(path.multiAutocomplete, 1, { message: 'Add at least one tag' });
    validate(path.checkbox, ({ value }) => (value() ? null : { kind: 'mustAccept', message: 'Please confirm' }));
    validate(path.rating, ({ value }) => (value() > 0 ? null : { kind: 'rate', message: 'Add a rating' }));
    validate(path.dateRange, ({ value }) =>
      value().start && value().end ? null : { kind: 'range', message: 'Pick a start and end date' },
    );
  }, {
    submission: {
      action: async field => {
        this.submitState.set('idle');
        this.submitAttempted.set(true);
        await delay(700);
        if (this.model().text.trim().toLowerCase() === 'taken') {
          return [{ fieldTree: field.text, kind: 'server', message: '"taken" is not allowed' }];
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
  });

  readonly submitState = signal<'idle' | 'saved'>('idle');
  readonly submitAttempted = signal(false);

  readonly selectOptions: readonly SelectOption<string>[] = [
    { label: 'One', value: 'one' },
    { label: 'Two', value: 'two' },
    { label: 'Three', value: 'three' },
  ];
  readonly radioOptions: readonly RadioOption<string>[] = [
    { label: 'Option A', value: 'a', description: 'First choice.' },
    { label: 'Option B', value: 'b', description: 'Second choice.' },
  ];

  private readonly allTags = ['alpha', 'beta', 'gamma', 'delta', 'epsilon', 'zeta'];
  readonly autocompleteSuggestions = signal<string[]>([]);
  readonly multiAutocompleteSuggestions = signal<string[]>([]);

  filterAutocomplete(query: string): void {
    const q = query.trim().toLowerCase();
    this.autocompleteSuggestions.set(q ? this.allTags.filter(t => t.includes(q)) : [...this.allTags]);
  }

  filterMultiAutocomplete(query: string): void {
    const q = query.trim().toLowerCase();
    const chosen = this.model().multiAutocomplete;
    this.multiAutocompleteSuggestions.set(this.allTags.filter(t => t.includes(q) && !chosen.includes(t)));
  }

  validateAll(): void {
    this.submitState.set('idle');
    this.everythingForm().markAsTouched();
  }

  reset(): void {
    this.submitState.set('idle');
    this.submitAttempted.set(false);
    this.model.set(createInitialModel());
    this.everythingForm().reset();
  }

  back(): void {
    void this.router.navigate(['/design-system']);
  }
}

function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}
