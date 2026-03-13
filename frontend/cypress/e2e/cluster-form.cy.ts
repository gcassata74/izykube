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
