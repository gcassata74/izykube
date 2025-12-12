// Custom Cypress commands for diagram interactions.

const getElementCenter = (el: HTMLElement) => {
  const rect = el.getBoundingClientRect();
  return {
    x: rect.left + rect.width / 2,
    y: rect.top + rect.height / 2
  };
};

Cypress.Commands.add('dragPaletteItem', (label: string) => {
  cy.contains('.palette-item', label, { matchCase: false })
    .should('be.visible')
    .then($item => {
      const itemCenter = getElementCenter($item[0]);
      cy.get('.diagram-viewport').then($canvas => {
        const canvasCenter = getElementCenter($canvas[0]);
        cy.wrap($item).trigger('mousedown', {
          button: 0,
          clientX: itemCenter.x,
          clientY: itemCenter.y,
          force: true
        });
        cy.document().trigger('mousemove', {
          clientX: canvasCenter.x,
          clientY: canvasCenter.y,
          force: true
        });
        cy.document().trigger('mouseup', {
          clientX: canvasCenter.x,
          clientY: canvasCenter.y,
          force: true
        });
      });
    });
});

Cypress.Commands.add('selectNode', (label: string | RegExp) => {
  cy.contains('.diagram-node', label).click({ force: true });
  cy.get('.node-form-body').should('be.visible');
});

Cypress.Commands.add('connectNodes', (fromLabel: string, toLabel: string) => {
  cy.contains('.diagram-node', fromLabel)
    .find('.connection-point')
    .first()
    .then($fromPoint => {
      const start = getElementCenter($fromPoint[0]);
      cy.contains('.diagram-node', toLabel)
        .find('.connection-point')
        .first()
        .then($toPoint => {
          const end = getElementCenter($toPoint[0]);
          cy.wrap($fromPoint).trigger('mousedown', {
            button: 0,
            clientX: start.x,
            clientY: start.y,
            force: true
          });
          cy.document().trigger('mousemove', {
            clientX: end.x,
            clientY: end.y,
            force: true
          });
          cy.wrap($toPoint).trigger('mouseup', {
            button: 0,
            clientX: end.x,
            clientY: end.y,
            force: true
          });
        });
    });
});

declare global {
  namespace Cypress {
    interface Chainable {
      dragPaletteItem(label: string): Chainable<void>;
      selectNode(label: string | RegExp): Chainable<void>;
      connectNodes(fromLabel: string, toLabel: string): Chainable<void>;
    }
  }
}

export {};
