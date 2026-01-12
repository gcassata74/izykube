import * as yaml from 'js-yaml';
import { CrdDefinition, CrdFieldType } from '../model/crd-definition.class';

export function derivePlural(singularName: string): string {
  const trimmed = (singularName || '').trim();
  return trimmed ? `${trimmed}s` : '';
}

export function deriveKind(singularName: string): string {
  const trimmed = (singularName || '').trim();
  if (!trimmed) {
    return '';
  }
  const parts = trimmed.split(/[\W_]+/).filter(Boolean);
  return parts.map(part => part.charAt(0).toUpperCase() + part.slice(1)).join('');
}

export function deriveMetadataName(plural: string, group: string): string {
  const p = (plural || '').trim();
  const g = (group || '').trim();
  return p && g ? `${p}.${g}` : '';
}

function schemaForType(fieldType: CrdFieldType): any {
  return { type: fieldType };
}

export function generateCrdYaml(def: Pick<CrdDefinition, 'group' | 'singularName' | 'scope' | 'version' | 'schemaFields'>): string {
  const plural = derivePlural(def.singularName);
  const kind = deriveKind(def.singularName);
  const metadataName = deriveMetadataName(plural, def.group);

  const specProperties: Record<string, any> = {};
  for (const field of def.schemaFields || []) {
    if (!field?.fieldName || !field.fieldType) {
      continue;
    }
    specProperties[field.fieldName] = schemaForType(field.fieldType);
  }

  const doc: any = {
    apiVersion: 'apiextensions.k8s.io/v1',
    kind: 'CustomResourceDefinition',
    metadata: { name: metadataName },
    spec: {
      group: def.group,
      scope: def.scope,
      names: {
        plural,
        singular: def.singularName,
        kind,
      },
      versions: [
        {
          name: def.version,
          served: true,
          storage: true,
          schema: {
            openAPIV3Schema: {
              type: 'object',
              properties: {
                spec: {
                  type: 'object',
                  ...(Object.keys(specProperties).length ? { properties: specProperties } : {}),
                },
              },
            },
          },
        },
      ],
    },
  };

  return yaml.dump(doc, { noRefs: true, lineWidth: 120 });
}

