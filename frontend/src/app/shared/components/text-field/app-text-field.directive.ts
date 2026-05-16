import {Directive} from '@angular/core';

@Directive({
  selector: 'input[appTextField]',
  standalone: true,
  host: {
    class: 'app-text-field',
  },
})
export class AppTextFieldDirective {}
