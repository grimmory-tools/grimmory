import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { JsonPipe } from '@angular/common';
import { CdkScrollable } from '@angular/cdk/scrolling';
import { Router } from '@angular/router';
import { form, FormField, FormRoot, hidden, max, min, minLength, required, validate } from '@angular/forms/signals';
import { AppButtonComponent } from '../../../shared/ui/button/app-button.component';
import { AppFieldComponent } from '../../../shared/ui/field/app-field.component';
import { AppInputComponent } from '../../../shared/ui/input/app-input.component';
import { AppMessageComponent } from '../../../shared/ui/message/app-message.component';
import { AppNumberInputComponent } from '../../../shared/ui/number-input/app-number-input.component';
import { AppSliderComponent } from '../../../shared/ui/slider/app-slider.component';
import { AppSwitchComponent } from '../../../shared/ui/switch/app-switch.component';
import { AppTagComponent } from '../../../shared/ui/tag/app-tag.component';
import { LucideBook, LucideChevronLeft, LucideLoaderCircle, LucideSave, LucideSmartphone, LucideTablet } from '@lucide/angular';

interface DeviceFormModel {
  hardcover: { enabled: boolean; apiKey: string };
  kobo: {
    enabled: boolean;
    twoWaySync: boolean;
    convertToKepub: boolean;
    markAsReading: number;
    markAsFinished: number;
    conversionLimitMb: number | null;
  };
  koreader: { enabled: boolean; syncWithWebReader: boolean; username: string; password: string };
}

function createInitialModel(): DeviceFormModel {
  return {
    hardcover: { enabled: false, apiKey: '' },
    kobo: {
      enabled: true,
      twoWaySync: false,
      convertToKepub: true,
      markAsReading: 3,
      markAsFinished: 95,
      conversionLimitMb: 50,
    },
    koreader: { enabled: false, syncWithWebReader: false, username: '', password: '' },
  };
}

@Component({
  selector: 'app-device-form-example',
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
    AppNumberInputComponent,
    AppSliderComponent,
    AppSwitchComponent,
    AppTagComponent,
    LucideBook,
    LucideSmartphone,
    LucideTablet,
    LucideChevronLeft,
    LucideSave,
    LucideLoaderCircle,
  ],
  templateUrl: './device-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeviceFormExampleComponent {
  private readonly router = inject(Router);

  readonly model = signal<DeviceFormModel>(createInitialModel());
  readonly deviceForm = form(this.model, path => {
    hidden(path.hardcover.apiKey, ({ valueOf }) => !valueOf(path.hardcover.enabled));
    required(path.hardcover.apiKey, { message: 'Enter your Hardcover API key' });
    minLength(path.hardcover.apiKey, 20, { message: 'Hardcover keys are at least 20 characters' });

    min(path.kobo.markAsReading, 1);
    max(path.kobo.markAsReading, 10);
    min(path.kobo.markAsFinished, 90);
    max(path.kobo.markAsFinished, 100);
    min(path.kobo.conversionLimitMb, 1);
    max(path.kobo.conversionLimitMb, 250);
    hidden(path.kobo.twoWaySync, ({ valueOf }) => !valueOf(path.kobo.enabled));
    hidden(path.kobo.convertToKepub, ({ valueOf }) => !valueOf(path.kobo.enabled));
    hidden(path.kobo.markAsReading, ({ valueOf }) => !valueOf(path.kobo.enabled));
    hidden(path.kobo.markAsFinished, ({ valueOf }) => !valueOf(path.kobo.enabled));
    hidden(path.kobo.conversionLimitMb, ({ valueOf }) => !valueOf(path.kobo.enabled));
    validate(path.kobo.twoWaySync, ({ value, valueOf }) =>
      value() && !valueOf(path.kobo.convertToKepub)
        ? { kind: 'needsKepub', message: 'Two-way sync needs KEPUB conversion turned on' }
        : null,
    );

    hidden(path.koreader.syncWithWebReader, ({ valueOf }) => !valueOf(path.koreader.enabled));
    hidden(path.koreader.username, ({ valueOf }) => !valueOf(path.koreader.enabled));
    hidden(path.koreader.password, ({ valueOf }) => !valueOf(path.koreader.enabled));
    required(path.koreader.username, { message: 'Choose a sync username' });
    required(path.koreader.password, { message: 'Set a sync password' });
    minLength(path.koreader.password, 6, { message: 'Use at least 6 characters' });
  }, {
    submission: {
      action: async field => {
        this.submitState.set('idle');
        this.submitAttempted.set(true);
        await delay(900);
        const hardcover = this.model().hardcover;
        if (hardcover.enabled && !hardcover.apiKey.startsWith('hc_')) {
          return [{ fieldTree: field.hardcover.apiKey, kind: 'server', message: 'Key must start with "hc_"' }];
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

  validateAll(): void {
    this.submitState.set('idle');
    this.deviceForm().markAsTouched();
  }

  reset(): void {
    this.submitState.set('idle');
    this.submitAttempted.set(false);
    this.model.set(createInitialModel());
    this.deviceForm().reset();
  }

  back(): void {
    void this.router.navigate(['/design-system']);
  }
}

function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}
