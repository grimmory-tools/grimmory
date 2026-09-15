import {Directive, signal} from '@angular/core';

@Directive({
  selector: '[appArtworkRevealGroup]',
})
export class ArtworkRevealGroupDirective {
  private pending = 0;
  private readonly revealed = signal(false);
  readonly ready = this.revealed.asReadonly();

  register(): () => void {
    if (this.revealed()) {
      return () => undefined;
    }
    this.pending++;
    let completed = false;
    return () => {
      if (completed) {
        return;
      }
      completed = true;
      this.pending--;
      if (this.pending === 0) {
        this.revealed.set(true);
      }
    };
  }
}
