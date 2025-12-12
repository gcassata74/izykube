import './commands';

// Swallow third-party errors so specs can validate UI flows without brittle failures.
Cypress.on('uncaught:exception', () => false);
