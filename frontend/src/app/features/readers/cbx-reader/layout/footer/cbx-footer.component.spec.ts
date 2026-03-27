import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../../../core/testing/transloco-testing';
import {CbxFooterComponent} from './cbx-footer.component';
import {CbxFooterService} from './cbx-footer.service';

describe('CbxFooterComponent', () => {
  let fixture: ComponentFixture<CbxFooterComponent>;
  let component: CbxFooterComponent;
  let footerService: CbxFooterService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CbxFooterComponent, getTranslocoModule()],
      providers: [CbxFooterService],
    }).compileComponents();

    fixture = TestBed.createComponent(CbxFooterComponent);
    component = fixture.componentInstance;
    footerService = TestBed.inject(CbxFooterService);
    fixture.detectChanges();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('derives display state and navigation availability from the footer service state', () => {
    footerService.updateState({
      currentPage: 1,
      totalPages: 4,
      isTwoPageView: true,
    });

    expect(component.displayPage).toBe(2);
    expect(component.displaySecondPage).toBe(3);
    expect(component.sliderValue).toBe(2);
    expect(component.canGoPrevious).toBe(true);
    expect(component.canGoNext).toBe(true);
  });

  it('builds page slider ticks for both short and long books', () => {
    footerService.updateState({totalPages: 8});
    expect(component.sliderTicks).toEqual([1, 2, 3, 4, 5, 6, 7, 8]);

    footerService.updateState({totalPages: 53});
    expect(component.sliderTicks[0]).toBe(1);
    expect(component.sliderTicks).toContain(6);
    expect(component.sliderTicks.at(-1)).toBe(53);
  });

  it('emits navigation events through the footer service', () => {
    const previousSpy = vi.spyOn(footerService, 'emitPreviousPage');
    const nextSpy = vi.spyOn(footerService, 'emitNextPage');
    const firstSpy = vi.spyOn(footerService, 'emitFirstPage');
    const lastSpy = vi.spyOn(footerService, 'emitLastPage');
    const previousBookSpy = vi.spyOn(footerService, 'emitPreviousBook');
    const nextBookSpy = vi.spyOn(footerService, 'emitNextBook');

    component.onPreviousPage();
    component.onNextPage();
    component.onFirstPage();
    component.onLastPage();
    component.onPreviousBook();
    component.onNextBook();

    expect(previousSpy).toHaveBeenCalledOnce();
    expect(nextSpy).toHaveBeenCalledOnce();
    expect(firstSpy).toHaveBeenCalledOnce();
    expect(lastSpy).toHaveBeenCalledOnce();
    expect(previousBookSpy).toHaveBeenCalledOnce();
    expect(nextBookSpy).toHaveBeenCalledOnce();
  });

  it('emits page changes only for valid manual page input and slider changes', () => {
    const goToPageSpy = vi.spyOn(footerService, 'emitGoToPage');
    const sliderSpy = vi.spyOn(footerService, 'emitSliderChange');
    footerService.updateState({totalPages: 10});

    component.goToPageInput = 5;
    component.onGoToPage();
    expect(goToPageSpy).toHaveBeenCalledWith(5);
    expect(component.goToPageInput).toBeNull();

    component.goToPageInput = 12;
    component.onGoToPage();
    expect(goToPageSpy).toHaveBeenCalledTimes(1);

    component.onSliderChange({target: {value: '7'}} as unknown as Event);
    expect(sliderSpy).toHaveBeenCalledWith(7);
  });
});
