/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
