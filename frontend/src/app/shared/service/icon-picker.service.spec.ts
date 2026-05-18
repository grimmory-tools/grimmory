import {TestBed} from '@angular/core/testing';
import {firstValueFrom, of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {DialogLauncherService} from '../services/dialog-launcher.service';
import {IconPickerService} from './icon-picker.service';

describe('IconPickerService', () => {
  const dialogLauncherService = {
    openIconPickerDialog: vi.fn(),
  };

  let service: IconPickerService;

  beforeEach(() => {
    vi.restoreAllMocks();
    dialogLauncherService.openIconPickerDialog.mockReset();

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        IconPickerService,
        {provide: DialogLauncherService, useValue: dialogLauncherService},
      ]
    });

    service = TestBed.inject(IconPickerService);
  });

  it('returns the icon selected by the dialog', async () => {
    dialogLauncherService.openIconPickerDialog.mockResolvedValue({
      onClose: of({type: 'PRIME_NG', value: 'pi pi-book'}),
    });

    await expect(firstValueFrom(service.open())).resolves.toEqual({
      type: 'PRIME_NG',
      value: 'pi pi-book',
    });
  });
});
