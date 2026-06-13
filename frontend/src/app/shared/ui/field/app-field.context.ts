import { InjectionToken, type Signal } from '@angular/core';
import { type FieldState, type ValidationError } from '@angular/forms/signals';

export type AppFieldLike = () => FieldState<unknown>;

export interface AppFieldContext {
  readonly controlId: Signal<string>;
  readonly labelId: Signal<string | null>;
  readonly describedById: Signal<string | null>;
  readonly validationVisible: Signal<boolean>;
}

export const APP_FIELD = new InjectionToken<AppFieldContext>('APP_FIELD');

export function appFieldErrorMessage(error: ValidationError): string {
  return typeof error.message === 'string' ? error.message : error.kind;
}
