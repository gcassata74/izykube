import { Container, toContainerRole } from './container.class';

describe('Container model', () => {
  it('leaves role undefined when not provided', () => {
    const container = new Container('id', 'web', 'asset', 80);
    expect(container.role).toBeUndefined();
  });

  it('normalizes arbitrary role values via toContainerRole', () => {
    expect(toContainerRole('INIT')).toBe('INIT');
    expect(toContainerRole('SIDECAR')).toBe('SIDECAR');
    expect(toContainerRole('unexpected')).toBeUndefined();
  });
});
