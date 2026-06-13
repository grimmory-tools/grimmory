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
export const APP_FIELD_DEFAULT_ERROR_KEY = 'shared.ui.validation.default';

export function appFieldErrorKey(kind: string): string {
  return `shared.ui.validation.${kind}`;
}

export function appFieldErrorMessage(error: ValidationError, fallback?: (kind: string) => string): string {
  if (typeof error.message === 'string' && error.message.length > 0) return error.message;
  return fallback ? fallback(error.kind) : error.kind;
}
