describe('Asset form', () => {
  beforeEach(() => {
    cy.visit('/asset-form');
  });

  it('saves an image asset', () => {
    cy.intercept('POST', '/api/asset', req => {
      expect(req.body.name).to.eq('Demo Asset');
      expect(req.body.type).to.eq('IMAGE');
      expect(req.body.image).to.eq('registry.example.com/app:1.0.0');
      req.reply({ id: 'asset-1', ...req.body });
    }).as('saveAsset');

    cy.get('#name').clear().type('Demo Asset');
    cy.get('#imageRef').clear().type('registry.example.com/app:1.0.0');
    cy.get('#version').clear().type('1.0.0');

    cy.contains('button', 'Save').click();
    cy.wait('@saveAsset').its('request.body.image').should('contain', 'registry.example.com/app:1.0.0');
  });

  it('switches type to Playbook and applies defaults', () => {
    cy.get('#name').clear().type('Playbook Asset');
    cy.get('#type').click();
    cy.contains('.p-dropdown-item', 'Playbook').click();

    cy.get('input#port').should('have.value', '22');
    cy.get('input#image').should('contain.value', 'ansible');

    cy.contains('button', 'Save').should('not.be.disabled');
  });
});
