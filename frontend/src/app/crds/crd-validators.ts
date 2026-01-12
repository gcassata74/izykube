import { AbstractControl, FormArray, FormGroup, ValidationErrors, ValidatorFn } from '@angular/forms';

export function trimmedRequired(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = (control.value || '').toString().trim();
    return value ? null : { required: true };
  };
}

export function allowedFieldName(): ValidatorFn {
  const regex = /^[a-zA-Z0-9_.-]+$/;
  return (control: AbstractControl): ValidationErrors | null => {
    const value = (control.value || '').toString().trim();
    if (!value) {
      return null;
    }
    return regex.test(value) ? null : { invalidFieldName: true };
  };
}

export function applyDuplicateFieldNameErrors(fieldsArray: FormArray<FormGroup>): void {
  const buckets = new Map<string, FormGroup[]>();

  fieldsArray.controls.forEach(group => {
    const nameControl = group.get('fieldName');
    if (!nameControl) {
      return;
    }

    if (nameControl.errors?.['duplicateFieldName']) {
      const { duplicateFieldName, ...rest } = nameControl.errors;
      nameControl.setErrors(Object.keys(rest).length ? rest : null);
    }

    const normalized = ((nameControl.value || '') as string).trim().toLowerCase();
    if (!normalized) {
      return;
    }
    const list = buckets.get(normalized) ?? [];
    list.push(group);
    buckets.set(normalized, list);
  });

  buckets.forEach(groups => {
    if (groups.length < 2) {
      return;
    }
    groups.forEach(group => {
      const nameControl = group.get('fieldName');
      if (!nameControl) {
        return;
      }
      const current = nameControl.errors || {};
      nameControl.setErrors({ ...current, duplicateFieldName: true });
    });
  });
}

