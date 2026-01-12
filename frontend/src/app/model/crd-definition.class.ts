export type CrdScope = 'Namespaced' | 'Cluster';

export type CrdFieldType = 'string' | 'number' | 'boolean' | 'object' | 'array';

export interface CrdSchemaField {
  fieldName: string;
  fieldType: CrdFieldType;
}

export interface CrdDefinitionSummary {
  id: string;
  group: string;
  singularName: string;
  scope: CrdScope | string;
  version: string;
  plural?: string;
  kind?: string;
  metadataName?: string;
  updatedAt?: string;
}

export interface CrdDefinition extends CrdDefinitionSummary {
  schemaFields: CrdSchemaField[];
}

