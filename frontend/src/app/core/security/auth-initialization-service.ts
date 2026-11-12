import {Service, signal} from '@angular/core';

@Service()
export class AuthInitializationService {
  private readonly _initialized = signal(false);
  readonly initialized = this._initialized.asReadonly();

  markAsInitialized(): void {
    this._initialized.set(true);
  }
}
