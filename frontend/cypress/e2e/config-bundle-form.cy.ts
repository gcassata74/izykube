describe('Config bundle form (dev harness)', () => {
  beforeEach(() => {
    cy.visit('/dev/forms');
  });

  it('edits entries and updates YAML preview', () => {
    cy.get('#entry-key-0').clear().type('API_URL');
    cy.get('#entry-value-0').clear().type('https://api.local');

    cy.contains('button', 'Add entry').click();
    cy.get('#entry-key-1').type('PASSWORD');
    cy.get('#entry-value-1').type('supersecret');

    cy.get('#entry-sensitivity-1').click();
    cy.contains('.p-dropdown-item', 'Secret').click();

    cy.get('.yaml-preview__content')
      .invoke('val')
      .should('contain', 'API_URL')
      .and('contain', 'PASSWORD');
  });

  it('enforces duplicate key validation and auto-adds a blank row after delete', () => {
    cy.get('#entry-key-0').clear().type('DUP_KEY');
    cy.contains('button', 'Add entry').click();
    cy.get('#entry-key-1').type('DUP_KEY');
    cy.get('#entry-key-1').blur();

    cy.contains('.field-error', 'Duplicate key.').should('be.visible');

    cy.contains('.entry-card', 'DUP_KEY')
      .find('button')
      .contains('Delete')
      .click();

    cy.get('.entry-card').should('have.length.at.least', 1);
    cy.get('#entry-key-0').clear().type('UNIQUE_KEY');
    cy.contains('.field-error', 'Duplicate key.').should('not.exist');
  });

  it('masks secrets until toggled to show', () => {
    cy.contains('button', 'Add entry').click();
    cy.get('#entry-key-1').type('PRIVATE_TOKEN');
    cy.get('#entry-value-1').type('shhh');

    cy.get('#entry-sensitivity-1').click();
    cy.contains('.p-dropdown-item', 'Secret').click();

    cy.get('#entry-value-1').should('have.attr', 'type', 'password');
    cy.get('p-inputSwitch').click({ force: true });
    cy.get('#entry-value-1').should('have.attr', 'type', 'text');
  });
});
