import { Node } from './node.class';
import { ConfigBundle, ensureConfigBundleDefaults } from './config-bundle.model';

export class ConfigBundleNode extends Node {
  configBundle: ConfigBundle;

  constructor(
    id: string,
    name: string,
    initial: Partial<ConfigBundle> = {},
    kind: 'configmap' | 'secret' = 'configmap'
  ) {
    super(id, name, kind);
    this.configBundle = ensureConfigBundleDefaults({
      id,
      name,
      ...initial
    });
  }
}
