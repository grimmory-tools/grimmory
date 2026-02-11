import {Component, EventEmitter, HostListener, inject, Input, Output} from '@angular/core';
import {ReaderViewManagerService} from '../../core/view-manager.service';
import {ReaderIconComponent} from '../../shared/icon.component';

interface TocItem {
  label: string;
  href: string;
  subitems: any;
  id: number;
}

interface PageItem {
  label: string;
  href: string;
  subitems: any;
  id: number;
}

interface RelocateEventDetail {
  fraction: number;
  section: { current: number; total: number };
  location: { current: number; next: number; total: number };
  time: { section: number; total: number };
  tocItem: TocItem;
  pageItem: PageItem;
  cfi: string;
  range: any;
}

@Component({
  selector: 'app-reader-navbar',
  standalone: true,
  imports: [ReaderIconComponent],
  templateUrl: './footer.component.html',
  styleUrls: ['./footer.component.scss']
})
export class ReaderNavbarComponent {
  @Input() progressData: RelocateEventDetail | null = null;
  @Input() forceVisible = false;
  @Input() set sectionFractions(value: number[]) {
    this._sectionFractions = value.filter(f => f > 0.001 && f < 0.999);
  }
  private _sectionFractions: number[] = [];

  get displaySectionFractions(): number[] {
    return this._sectionFractions;
  }
  @Output() progressChange = new EventEmitter<number>();

  private managerService = inject(ReaderViewManagerService);
  showLocationPopover = false;

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    const target = event.target as HTMLElement;
    const clickedInside = target.closest('.location-popover') || target.closest('.location-btn');
    if (!clickedInside && this.showLocationPopover) {
      this.showLocationPopover = false;
    }
  }

  @HostListener('window:blur')
  onWindowBlur() {
    setTimeout(() => {
      if (this.showLocationPopover) {
        this.showLocationPopover = false;
      }
    }, 200);
  }

  toggleLocationPopover() {
    this.showLocationPopover = !this.showLocationPopover;
  }

  get currentFraction(): number {
    return this.progressData?.fraction ?? 0;
  }

  get currentPercentage(): number {
    return Math.round(this.currentFraction * 100);
  }

  get timeTotal(): string {
    return this.formatDuration((this.progressData?.time.total ?? 0) * 60);
  }

  get timeSection(): string {
    return this.formatDuration((this.progressData?.time.section ?? 0) * 60);
  }

  get sectionCurrent(): number {
    return this.progressData?.section.current ?? 0;
  }

  get sectionTotal(): number {
    return this.progressData?.section.total ?? 0;
  }

  get currentChapter(): string {
    return this.progressData?.tocItem?.label ?? '';
  }

  get currentPage(): string {
    return this.progressData?.pageItem?.label ?? '';
  }

  get navbarVisible(): boolean {
    return this.forceVisible;
  }

  get canGoPrevious(): boolean {
    const s = this.progressData?.section;
    return !!s && s.current > 0;
  }

  get canGoNext(): boolean {
    const s = this.progressData?.section;
    return !!s && s.current < s.total - 1;
  }

  onProgressChange(event: Event) {
    const target = event.target as HTMLInputElement;
    const fraction = parseFloat(target.value) / 100;
    this.progressChange.emit(fraction);
  }

  onGoToPercentage(value: string): void {
    const percentage = parseFloat(value);
    if (isNaN(percentage) || percentage < 0 || percentage > 100) return;
    const fraction = percentage / 100;
    this.progressChange.emit(fraction);
  }

  onFirstSection() {
    this.managerService.goToSection(0).subscribe();
  }

  onPreviousSection(): void {
    const s = this.progressData?.section;
    if (!s || s.current <= 0) return;
    this.managerService.goToSection(s.current - 1).subscribe();
  }

  onNextSection(): void {
    const s = this.progressData?.section;
    if (!s || s.current >= s.total - 1) return;
    this.managerService.goToSection(s.current + 1).subscribe();
  }

  onLastSection(): void {
    const s = this.progressData?.section;
    if (!s || s.total <= 0) return;
    this.managerService.goToSection(s.total - 1).subscribe();
  }

  private formatDuration(seconds: number): string {
    if (seconds < 60) return `${Math.round(seconds)} sec`;
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes} min`;
    const hours = Math.floor(minutes / 60);
    const remainingMinutes = minutes % 60;
    return remainingMinutes > 0
      ? `${hours} hr ${remainingMinutes} min`
      : `${hours} hr`;
  }
}
