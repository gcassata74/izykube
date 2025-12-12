const assetFixtures = [
  { id: 'asset-1', name: 'web', version: '1.0.0', type: 'image', port: 8080, image: 'registry/web:1.0.0' },
  { id: 'asset-2', name: 'batch', version: '1.0.0', type: 'image', port: 9000, image: 'registry/batch:1.0.0' }
];

describe('Cluster editor forms (real components)', () => {
  beforeEach(() => {
    cy.viewport(1440, 900);
    cy.window().then(win => {
      (win as any).__izyAssets = assetFixtures;
    });
    cy.visit('/cluster-editor');
    cy.window().its('izyAddNode').should('be.a', 'function');
  });

  it('fills deployment form', () => {
    cy.window().then(win => win.izyAddNode('deployment', { name: 'api-deploy' }));
    cy.contains('.diagram-node', 'api-deploy').click({ force: true });

    cy.get('.node-form-body').within(() => {
      cy.get('#name').clear().type('api-deploy');
      cy.get('#replicas input').clear().type('2');
      cy.get('p-dropdown#workloadType .p-dropdown-trigger').click({ force: true });
      cy.contains('.p-dropdown-item', 'StatefulSet').click({ force: true });
      cy.get('p-dropdown#strategyType .p-dropdown-trigger').click({ force: true });
      cy.contains('.p-dropdown-item', 'Rolling Update').click({ force: true });
      cy.get('p-dropdown#assetId .p-dropdown-trigger').click({ force: true });
      cy.contains('.p-dropdown-item', 'web - 1.0.0', { timeout: 15000 }).click({ force: true });
      cy.get('#containerPort input').clear().type('8081');
    });
    cy.get('.node-form-body #name').should('have.value', 'api-deploy');
  });

  it('fills service form with NodePort and frontend URL', () => {
    cy.window().then(win => win.izyAddNode('service', { name: 'web-service' }));
    cy.contains('.diagram-node', 'web-service').click({ force: true });

    cy.get('.node-form-body').within(() => {
      cy.get('#name').clear().type('web-service');
      cy.get('p-dropdown#type .p-dropdown-trigger', { timeout: 15000 }).click({ force: true });
      cy.contains('.p-dropdown-item', 'NodePort', { timeout: 15000 }).click({ force: true });
      cy.get('#port input').clear().type('8080');
      cy.get('#nodePort input').clear().type('30080');
      cy.contains('label', 'Expose service').click({ force: true });
      cy.get('#frontendUrl', { timeout: 10000 }).should('be.enabled').type('https://example.local');
    });

    cy.get('.node-form-header').should('contain.text', 'service');
  });

  it('fills ingress form linked to a service and adds annotations', () => {
    cy.window().then(win => {
      win.izyAddNode('service', { name: 'api-service' });
      win.izyAddNode('ingress', { name: 'main-ingress' });
      win.izyConnect('api-service', 'main-ingress', { type: 'Expose' });
    });
    cy.wait(800);
    cy.contains('.diagram-node', 'main-ingress').click({ force: true });

    cy.get('.node-form-body').within(() => {
      cy.get('p-dropdown#serviceName', { timeout: 20000 }).should('exist');
      cy.get('#name').clear().type('main-ingress');
      cy.get('#host').clear().type('app.example.com');
      cy.get('#path').clear().type('/api');
      cy.get('p-dropdown#serviceName .p-dropdown-trigger', { timeout: 15000 }).click({ force: true });
      cy.contains('.p-dropdown-item', /^api-service/i, { timeout: 15000 }).click({ force: true });
      cy.get('#servicePort input').clear().type('8080');
      cy.contains('button', 'Add annotation').click();
      cy.get('input[placeholder=\"key\"]').last().type('nginx.ingress.kubernetes.io/rewrite-target');
      cy.get('input[placeholder=\"value\"]').last().type('/');
    });
  });

  it('fills Istio form linked to a service', () => {
    cy.window().then(win => {
      win.izyAddNode('service', { name: 'mesh-service' });
      win.izyAddNode('Istio', { name: 'istio-route' });
      win.izyConnect('mesh-service', 'istio-route', { type: 'Expose' });
    });
    cy.wait(800);
    cy.contains('.diagram-node', 'istio-route').click({ force: true });

    cy.get('.node-form-body').within(() => {
      cy.get('p-dropdown#serviceName', { timeout: 20000 }).should('exist');
      cy.get('#name').clear().type('istio-route');
      cy.get('#host').clear().type('mesh.example.com');
      cy.get('#path').clear().type('/mesh');
      cy.get('p-dropdown#serviceName .p-dropdown-trigger', { timeout: 15000 }).click({ force: true });
      cy.contains('.p-dropdown-item', /^mesh-service/i, { timeout: 15000 }).click({ force: true });
      cy.get('#servicePort input').clear().type('8080');
    });
  });

  it('fills container form with asset and role', () => {
    cy.window().then(win => win.izyAddNode('container', { name: 'api-container' }));
    cy.contains('.diagram-node', 'api-container').click({ force: true });

    cy.get('.node-form-body').within(() => {
      cy.get('p-dropdown#assetId', { timeout: 20000 }).should('exist');
      cy.get('#name').clear().type('api-container');
      cy.get('p-dropdown#assetId .p-dropdown-trigger', { timeout: 15000 }).click({ force: true });
      cy.contains('.p-dropdown-item', 'web - 1.0.0', { timeout: 15000 }).click({ force: true });
      cy.get('#containerPort input').clear().type('8080');
      cy.get('p-dropdown#containerRole .p-dropdown-trigger').click({ force: true });
      cy.contains('.p-dropdown-item', 'Sidecar').click();
    });
  });

  it('fills job form with asset', () => {
    cy.window().then(win => win.izyAddNode('job', { name: 'batch-job' }));
    cy.contains('.diagram-node', 'batch-job').click({ force: true });

    cy.get('.node-form-body').within(() => {
      cy.get('p-dropdown#assetId', { timeout: 20000 }).should('exist');
      cy.get('#name').clear().type('batch-job');
      cy.get('p-dropdown#assetId .p-dropdown-trigger', { timeout: 15000 }).click({ force: true });
      cy.contains('.p-dropdown-item', 'batch - 1.0.0', { timeout: 15000 }).click({ force: true });
    });
  });

  it('fills volume form switching to hostPath', () => {
    cy.window().then(win => win.izyAddNode('volume', { name: 'data-volume' }));
    cy.contains('.diagram-node', 'data-volume').click({ force: true });

    cy.get('.node-form-body').within(() => {
      cy.get('#name').clear().type('data-volume');
      cy.get('p-dropdown#type .p-dropdown-trigger').click({ force: true });
      cy.contains('.p-dropdown-item', 'Host Path').click();
      cy.get('#mountPath').clear().type('/data');
      cy.get('#path').clear().type('/var/lib/data');
    });
  });

  it('fills config bundle form on real component', () => {
    cy.window().then(win => win.izyAddNode('configbundle', { name: 'config-bundle' }));

    cy.get('.node-form-body #entry-key-0').clear().type('API_URL');
    cy.get('.node-form-body #entry-value-0').clear().type('https://api.live');
    cy.contains('button', 'Add entry').click();
    cy.get('.node-form-body #entry-key-1').type('PASSWORD');
    cy.get('.node-form-body #entry-value-1').type('secret123');
    cy.get('.node-form-body #entry-sensitivity-1').click();
    cy.contains('.p-dropdown-item', 'Secret').click();
    cy.get('.node-form-body .yaml-preview__content')
      .invoke('val')
      .should('contain', 'API_URL')
      .and('contain', 'PASSWORD');
  });
});
