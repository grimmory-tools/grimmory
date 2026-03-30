import {Injectable, signal} from '@angular/core';

@Injectable({providedIn: 'root'})
export class AuthInitializationService {
  private readonly _initialized = signal(false);
  readonly initialized = this._initialized.asReadonly();

  markAsInitialized(): void {
    this._initialized.set(true);
  }
}
