import { FormArray, FormBuilder, FormGroup } from '@angular/forms';
import { applyDuplicateFieldNameErrors } from './crd-validators';

describe('CRD validators', () => {
  it('marks duplicate schema field names (case-insensitive)', () => {
    const fb = new FormBuilder();
    const fields = new FormArray<FormGroup>([
      fb.group({ fieldName: ['replicas'], fieldType: ['number'] }),
      fb.group({ fieldName: ['Replicas'], fieldType: ['number'] }),
      fb.group({ fieldName: ['image'], fieldType: ['string'] }),
    ]);

    applyDuplicateFieldNameErrors(fields);

    expect(fields.at(0).get('fieldName')?.errors?.['duplicateFieldName']).toBeTrue();
    expect(fields.at(1).get('fieldName')?.errors?.['duplicateFieldName']).toBeTrue();
    expect(fields.at(2).get('fieldName')?.errors?.['duplicateFieldName']).toBeUndefined();
  });
});
