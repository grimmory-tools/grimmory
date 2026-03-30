import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {ExternalDocLinkComponent} from './external-doc-link.component';

describe('ExternalDocLinkComponent', () => {
  let fixture: ComponentFixture<ExternalDocLinkComponent>;
  let component: ExternalDocLinkComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ExternalDocLinkComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ExternalDocLinkComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('opens the configured documentation link in a new tab', () => {
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
    component.docType = 'taskManagement';

    component.openLink();

    expect(openSpy).toHaveBeenCalledWith('https://grimmory.org/docs/tools/task-manager', '_blank');
  });

  it('does nothing when the doc type is unknown at runtime', () => {
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
    (component as unknown as {docType: string}).docType = 'unknown';

    component.openLink();

    expect(openSpy).not.toHaveBeenCalled();
  });

  it('renders the clickable icon with the configured size', () => {
    component.docType = 'authentication';
    component.tooltip = 'Read the docs';
    component.tooltipPosition = 'left';
    component.size = '1.5rem';
    fixture.detectChanges();

    const icon = fixture.nativeElement.querySelector('i') as HTMLElement;
    expect(icon.getAttribute('role')).toBe('button');
    expect(icon.getAttribute('tabindex')).toBe('0');
    expect(icon.style.fontSize).toBe('1.5rem');
  });
});
