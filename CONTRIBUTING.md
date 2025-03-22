# Contributing to IzyKube

Thank you for your interest in contributing to IzyKube! This document provides guidelines and instructions for contributing to make the process smooth for everyone.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
  - [Development Environment Setup](#development-environment-setup)
  - [Project Structure](#project-structure)
- [How to Contribute](#how-to-contribute)
  - [Reporting Bugs](#reporting-bugs)
  - [Suggesting Enhancements](#suggesting-enhancements)
  - [Pull Requests](#pull-requests)
- [Development Workflow](#development-workflow)
  - [Branching Strategy](#branching-strategy)
  - [Commit Guidelines](#commit-guidelines)
  - [Testing](#testing)
- [Style Guidelines](#style-guidelines)
  - [Code Style](#code-style)
  - [Documentation](#documentation)
- [Community](#community)
- [License](#license)

## Code of Conduct

This project and everyone participating in it is governed by the [IzyKube Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to [project maintainers].

## Getting Started

### Development Environment Setup

1. **Prerequisites**:
   - Java 17 or higher
   - Node.js 18 or higher
   - Angular CLI
   - Docker
   - Kubernetes environment (k3d, minikube, or other)

2. **Local Development Setup**:
   ```bash
   # Clone the repository
   git clone https://github.com/yourusername/izykube.git
   cd izykube

   # Backend setup (Spring Boot)
   cd backend
   ./mvnw clean install

   # Frontend setup (Angular)
   cd ../frontend
   npm install
   ```

3. **Running the application**:
   ```bash
   # Start the backend
   cd backend
   ./mvnw spring-boot:run

   # Start the frontend (in a new terminal)
   cd frontend
   ng serve
   ```

   Alternatively, you can use the provided Makefile:
   ```bash
   make run-spring-boot-server
   make run-angular-client
   ```

### Project Structure

- `backend/`: Spring Boot application
  - `src/main/java/`: Java source code
  - `src/main/resources/`: Application configuration
  - `src/test/`: Test files
- `frontend/`: Angular application
  - `src/app/`: Angular components, services, etc.
  - `src/assets/`: Static assets

## How to Contribute

### Reporting Bugs

1. Check if the bug has already been reported by searching the [Issues](https://github.com/yourusername/izykube/issues).
2. If not, create a new issue using the bug report template.
3. Include a clear title and description, steps to reproduce, expected behavior, and any relevant screenshots.
4. Add the `bug` label to the issue.

### Suggesting Enhancements

1. Check if the enhancement has already been suggested by searching the [Issues](https://github.com/yourusername/izykube/issues).
2. If not, create a new issue using the feature request template.
3. Clearly describe the enhancement, the motivation behind it, and any alternatives you've considered.
4. Add the `enhancement` label to the issue.

### Pull Requests

1. Fork the repository and create your branch from `main`.
2. If you've added code, add tests to cover your changes.
3. Ensure all tests pass.
4. Make sure your code follows our style guidelines.
5. Submit a pull request, referencing any related issues.

## Development Workflow

### Branching Strategy

We follow a simplified GitFlow workflow:

- `main`: Production-ready code
- `develop`: Development branch for feature integration
- `feature/*`: Feature branches
- `bugfix/*`: Bug fix branches
- `release/*`: Release preparation branches
- `hotfix/*`: Urgent fixes for production

### Commit Guidelines

We follow conventional commits:

- `feat`: A new feature
- `fix`: A bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

Example: `feat(ui): add kubernetes service configuration panel`

### Testing

- Write unit tests for all new code
- Ensure existing tests pass
- Update tests when changing behavior
- Run the full test suite before submitting PRs

```bash
# Backend tests
cd backend
./mvnw test

# Frontend tests
cd frontend
ng test
```

## Style Guidelines

### Code Style

#### Java
- Follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Use lombok annotations to reduce boilerplate
- Use Spring best practices

#### TypeScript/Angular
- Follow the [Angular Style Guide](https://angular.io/guide/styleguide)
- Format code with Prettier
- Follow RxJS best practices for reactive programming

### Documentation

- Document all public APIs
- Write clear and concise comments
- Update documentation when behavior changes
- Use markdown for documentation files

## Community

- Join our [Discord/Slack] for discussions
- Attend community meetings
- Follow the project on [Twitter/social media]

## License

By contributing to IzyKube, you agree that your contributions will be licensed under the project's [Apache 2.0 License](LICENSE).

---

Thank you for contributing to IzyKube! Your time and expertise help make this project better for everyone.
