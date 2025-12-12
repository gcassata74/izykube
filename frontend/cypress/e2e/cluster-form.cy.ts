describe('Cluster form', () => {
  beforeEach(() => {
    cy.visit('/cluster-form');
  });

  it('blocks submit until name is provided', () => {
    cy.contains('button', 'Save').should('be.disabled');
    cy.get('#clustername').click().blur();
    cy.contains('Field is mandatory').should('be.visible');
  });

  it('creates a new cluster with generated namespace', () => {
    cy.intercept('POST', '/api/cluster', req => {
      expect(req.body.name).to.eq('Demo Diagram');
      expect(req.body.nameSpace).to.eq('demo-diagram');
      req.reply({ id: 'cluster-1', ...req.body });
    }).as('createCluster');

    cy.get('#clustername').clear().type('Demo Diagram');
    cy.contains('button', 'Save').click();

    cy.wait('@createCluster').its('request.body.name').should('eq', 'Demo Diagram');
  });
});
