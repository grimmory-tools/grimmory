import {Directive} from '@angular/core';

@Directive({
  selector: '[appButtonGroup]',
  standalone: true,
  host: {
    class: 'app-control-group app-control-group-inline',
  },
})
export class AppButtonGroupDirective {}
