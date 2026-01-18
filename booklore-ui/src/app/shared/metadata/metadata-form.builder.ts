import {Injectable} from '@angular/core';
import {FormControl, FormGroup} from '@angular/forms';
import {ALL_METADATA_FIELDS, MetadataFieldConfig} from './metadata-field.config';

@Injectable({
  providedIn: 'root'
})
export class MetadataFormBuilder {

  buildForm(
    includeLockedControls: boolean = true,
    fields: MetadataFieldConfig[] = ALL_METADATA_FIELDS
  ): FormGroup {
    const controls: Record<string, FormControl> = {};

    for (const field of fields) {
      const defaultValue = this.getDefaultValue(field.type);
      controls[field.controlName] = new FormControl(defaultValue);

      if (includeLockedControls) {
        controls[field.lockedKey] = new FormControl(false);
      }
    }

    controls['thumbnailUrl'] = new FormControl('');
    if (includeLockedControls) {
      controls['coverLocked'] = new FormControl(false);
    }

    return new FormGroup(controls);
  }

  private getDefaultValue(type: string): unknown {
    switch (type) {
      case 'array':
        return [];
      case 'number':
        return null;
      default:
        return '';
    }
  }

  applyLockStates(
    metadataForm: FormGroup,
    lockedFields: Record<string, boolean>,
    fields: MetadataFieldConfig[] = ALL_METADATA_FIELDS
  ): void {
    for (const key of Object.keys(metadataForm.controls)) {
      if (!key.endsWith('Locked')) {
        metadataForm.get(key)?.enable({emitEvent: false});
      }
    }

    for (const field of fields) {
      if (lockedFields[field.lockedKey]) {
        metadataForm.get(field.controlName)?.disable({emitEvent: false});
      }
    }
  }

  setAllFieldsLocked(metadataForm: FormGroup, locked: boolean): void {
    for (const key of Object.keys(metadataForm.controls)) {
      if (key.endsWith('Locked')) {
        metadataForm.get(key)?.setValue(locked);
        const fieldName = key.replace('Locked', '');
        if (locked) {
          metadataForm.get(fieldName)?.disable();
        } else {
          metadataForm.get(fieldName)?.enable();
        }
      }
    }
  }
}
