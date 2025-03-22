# IzyKube

<p align="center">
  <img src="./assets/izykube-logo.png" alt="IzyKube Logo" width="300"/>
</p>

<p align="center">
  A visual Kubernetes resource editor and deployment tool
</p>

## Overview

IzyKube is an open-source visual editor for Kubernetes resources. It allows users to create, configure, and deploy
Kubernetes components using an intuitive drag-and-drop interface. The application simplifies Kubernetes deployments
through visual representation of resources and their relationships.

### Key Features

- 🔄 **Interactive Diagram Editor** - Visually build your Kubernetes resources with drag-and-drop functionality
- 🔌 **Resource Linking** - Create connections between resources like Services, Deployments, ConfigMaps, etc.
- 🚀 **One-Click Deployment** - Deploy your resources to Kubernetes with a single click
- 📝 **Form-Based Configuration** - Configure resources using intuitive forms
- 🔍 **Template Preview** - See the generated YAML before deployment
- 📊 **Status Monitoring** - Keep track of deployed resources

## Getting Started

### Prerequisites

- Java 17 or higher
- Node.js 18 or higher
- Angular CLI 16 or higher
- Docker
- A Kubernetes environment (k3d, minikube, or access to a cluster)

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/izykube.git
   cd izykube
   ```

2. Set up the backend (Spring Boot):
   ```bash
   cd backend
   ./mvnw clean install
   ```

3. Set up the frontend (Angular):
   ```bash
   cd frontend
   npm install
   ```

### Running the Application

You can use our Makefile to simplify running the application:

```bash
# Start the Spring Boot backend
make run-spring-boot-server

# In another terminal, start the Angular frontend
make run-angular-client

# For debugging, open Chrome with special flags
make run-chrome-dev
```

Alternatively, run the applications directly:

```bash
# Backend
cd backend
./mvnw spring-boot:run

# Frontend
cd frontend
ng serve
```

### Setting Up a Local Kubernetes Environment

IzyKube includes Makefile commands to easily set up a local Kubernetes environment using k3d:

```bash
# Create a k3d registry
make create-k3d-registry

# Create a k3d cluster with the registry
make create-k3d-cluster

# Or do both in one command
make start-k3d-cluster

# Set up a cluster with Istio installed
make start-k3d-cluster-with-istio

# Delete the cluster
make delete-k3d-cluster

# Delete the registry
make delete-k3d-registry

# Restart the cluster (delete and recreate)
make restart-k3d-cluster
```

### Internationalization

IzyKube supports multiple languages. Use the following Makefile commands for i18n:

```bash
# Extract i18n messages
make run-i18n-extract

# Build with specific locale (e.g., French)
make run-i18n-build LOCALE=fr

# Serve with specific locale
make run-i18n-serve LOCALE=fr
```

## Architecture

IzyKube consists of:

1. **Frontend**: Angular application with GoJS for diagram editing
2. **Backend**: Spring Boot application that interacts with the Kubernetes API
3. **Database**: MongoDB for storing cluster templates and configurations

<p align="center">
  <img src="./assets/architecture.png" alt="IzyKube Architecture" width="600"/>
</p>

## Usage Guide

### Creating a New Cluster

1. Navigate to the Clusters view
2. Click "Add" to create a new cluster
3. Provide a name and namespace
4. Open the cluster diagram editor

### Building a Deployment

1. Drag and drop resources from the palette
2. Connect resources by dragging from one node to another
3. Configure each resource using the form editor
4. Save your diagram

### Deploying to Kubernetes

1. Click "Create Template" to generate Kubernetes manifests
2. Review the generated YAML
3. Click "Deploy" to apply the resources to your cluster

## Contributing

Contributions are welcome! Please read our [Contributing Guide](CONTRIBUTING.md) for details on our code of conduct and
the process for submitting pull requests.

### Makefile Reference

The project includes a comprehensive Makefile to simplify development tasks:

| Command                             | Description                                                             |
|-------------------------------------|-------------------------------------------------------------------------|
| `make run-spring-boot-server`       | Start the Spring Boot backend with debugging enabled                    |
| `make run-angular-client`           | Start the Angular frontend (and kill any existing process on port 4200) |
| `make run-chrome-dev`               | Open Chrome with flags for development and debugging                    |
| `make create-docker-registry`       | Create a Docker registry container                                      |
| `make create-k3d-registry`          | Create a k3d registry                                                   |
| `make delete-docker-registry`       | Remove the Docker registry container                                    |
| `make delete-k3d-registry`          | Delete the k3d registry                                                 |
| `make create-k3d-cluster`           | Create a k3d cluster configured for use with IzyKube                    |
| `make delete-k3d-cluster`           | Delete the k3d cluster                                                  |
| `make start-k3d-cluster`            | Create registry and cluster in one command                              |
| `make restart-k3d-cluster`          | Delete and recreate the k3d cluster                                     |
| `make install-istio`                | Install Istio service mesh into the cluster                             |
| `make start-k3d-cluster-with-istio` | Create cluster and install Istio                                        |
| `make run-i18n-extract`             | Extract i18n messages from the Angular frontend                         |
| `make run-i18n-build LOCALE=xx`     | Build the app with a specific locale                                    |
| `make run-i18n-serve LOCALE=xx`     | Serve the app with a specific locale                                    |

## License

This project is licensed under the [Apache License 2.0](LICENSE) - see the LICENSE file for details.

## Acknowledgments

- [GoJS](https://gojs.net/) for the diagram components
- [Kubernetes](https://kubernetes.io/) and [Spring Boot](https://spring.io/projects/spring-boot) communities
- All [contributors](https://github.com/yourusername/izykube/contributors) who have helped shape IzyKube