import {ComponentFixture, TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {DynamicDialogRef} from 'primeng/dynamicdialog';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {VersionService} from '../../../service/version.service';
import {VersionChangelogDialogComponent} from './version-changelog-dialog.component';

describe('VersionChangelogDialogComponent', () => {
  let fixture: ComponentFixture<VersionChangelogDialogComponent>;
  let component: VersionChangelogDialogComponent;
  let versionService: {getChangelog: ReturnType<typeof vi.fn>};

  beforeEach(async () => {
    versionService = {
      getChangelog: vi.fn(() => of([
        {
          version: '1.2.3',
          name: 'Stable',
          changelog: '## Added\n- New feature',
          url: 'https://example.com/releases/1.2.3',
          publishedAt: '2026-03-26T00:00:00Z',
        },
      ])),
    };

    await TestBed.configureTestingModule({
      imports: [VersionChangelogDialogComponent, getTranslocoModule()],
      providers: [
        {provide: VersionService, useValue: versionService},
        {provide: DynamicDialogRef, useValue: {close: vi.fn()}},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(VersionChangelogDialogComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('loads the changelog on init and clears the loading state', () => {
    component.ngOnInit();

    expect(versionService.getChangelog).toHaveBeenCalled();
    expect(component.changelog).toHaveLength(1);
    expect(component.loading).toBe(false);
  });

  it('clears the loading state when the changelog request fails', () => {
    versionService.getChangelog.mockReturnValueOnce(
      throwError(() => new Error('failed'))
    );

    component.ngOnInit();

    expect(component.changelog).toEqual([]);
    expect(component.loading).toBe(false);
  });

  it('converts markdown to sanitized HTML and normalizes headings to h3', () => {
    const html = component.markdownToHtml('## Heading\n\n<script>alert(1)</script><b>Safe</b>');

    expect(html).toContain('<h3');
    expect(html).not.toContain('<h2');
    expect(html).not.toContain('<script>');
    expect(html).toContain('<b>Safe</b>');
  });
});
