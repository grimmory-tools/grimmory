import {ChangeDetectionStrategy, Component} from '@angular/core';

@Component({
  selector: 'app-empty',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  templateUrl: './empty.component.html',
  styleUrl: './empty.component.scss'
})
export class EmptyComponent {

}
