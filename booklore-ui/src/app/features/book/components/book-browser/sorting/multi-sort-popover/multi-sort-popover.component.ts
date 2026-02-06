import {Component, EventEmitter, Input, Output} from '@angular/core';
import {SortDirection, SortOption} from '../../../../model/sort.model';
import {CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList} from '@angular/cdk/drag-drop';
import {Select} from 'primeng/select';
import {FormsModule} from '@angular/forms';
import {Tooltip} from 'primeng/tooltip';

@Component({
  selector: 'app-multi-sort-popover',
  standalone: true,
  imports: [
    CdkDropList,
    CdkDrag,
    CdkDragHandle,
    Select,
    FormsModule,
    Tooltip
  ],
  templateUrl: './multi-sort-popover.component.html',
  styleUrl: './multi-sort-popover.component.scss'
})
export class MultiSortPopoverComponent {
  @Input() sortCriteria: SortOption[] = [];
  @Input() availableSortOptions: SortOption[] = [];

  @Output() criteriaChange = new EventEmitter<SortOption[]>();
  @Output() addCriterion = new EventEmitter<string>();
  @Output() removeCriterion = new EventEmitter<number>();
  @Output() toggleDirection = new EventEmitter<number>();
  @Output() reorder = new EventEmitter<CdkDragDrop<SortOption[]>>();

  selectedField: string | null = null;

  get unusedOptions(): SortOption[] {
    const usedFields = new Set(this.sortCriteria.map(c => c.field));
    return this.availableSortOptions.filter(opt => !usedFields.has(opt.field));
  }

  onDrop(event: CdkDragDrop<SortOption[]>): void {
    this.reorder.emit(event);
  }

  onToggleDirection(index: number): void {
    this.toggleDirection.emit(index);
  }

  onRemove(index: number): void {
    this.removeCriterion.emit(index);
  }

  onAddField(): void {
    if (this.selectedField) {
      this.addCriterion.emit(this.selectedField);
      this.selectedField = null;
    }
  }

  getDirectionIcon(direction: SortDirection): string {
    return direction === SortDirection.ASCENDING ? 'pi pi-arrow-up' : 'pi pi-arrow-down';
  }

  getDirectionTooltip(direction: SortDirection): string {
    return direction === SortDirection.ASCENDING ? 'Ascending - click to change' : 'Descending - click to change';
  }

  protected readonly SortDirection = SortDirection;
}
