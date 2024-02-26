#!/usr/bin/env node

const shell = require('shelljs');
const yargs = require('yargs/yargs');
const { hideBin } = require('yargs/helpers');

const runChromeDev = () => {
  shell.exec('google-chrome --incognito --new-window "http://127.0.0.1:4200" --remote-debugging-port=9222 --disable-web-security --user-data-dir="~/ChromeDevSession"');
};

const runAngularClient = () => {
  shell.cd('frontend');
  shell.exec('npm start');
  shell.cd('..');  // Return to the project root directory
};

const runSpringBootServer = () => {
  shell.cd('backend');
  shell.exec('MAVEN_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" mvn spring-boot:run');
  shell.cd('..');  // Return to the project root directory
};

const createDockerRegistry = () => {
  shell.exec('docker run -d --name k3d-test-app-registry -p 5000:5000 --restart=always registry:2');
};

const createK3dRegistry = () => {
  shell.exec('k3d registry create test-app-registry --port 5000');
};

const deleteDockerRegistry = () => {
  shell.exec('docker stop k3d-test-app-registry && docker rm -v k3d-test-app-registry');
};

const deleteK3dRegistry = () => {
  shell.exec('k3d registry delete test-app-registry');
};

const createK3dCluster = () => {
  createK3dRegistry();
  shell.exec('k3d cluster create -p "9000:80@loadbalancer" --registry-use k3d-test-app-registry:5000');
};

const deleteK3dCluster = () => {
  shell.exec('k3d cluster delete');
};

const startK3dCluster = () => {
  createK3dRegistry();
  createK3dCluster();
};

const restartK3dCluster = () => {
  deleteK3dCluster();
  startK3dCluster();
};

//izy.js build-and-register-image --dir=./path/to/dockerfile-directory --imgName=custom-image-name --id=asset-id --name=asset-name --description="asset description" --port=1234

const buildAndRegisterImage = (dir, imgName, id, name, description, port) => {
  console.log(`Building Docker image from directory: ${dir} with image name: ${imgName}`);
  shell.exec(`docker build ${dir} -t ${imgName}:latest && \
              docker tag ${imgName}:latest localhost:5000/${imgName}:latest && \
              docker push localhost:5000/${imgName}:latest`);
  shell.exec(`curl -X POST -H "Content-Type: application/json" \
              -d '{ "id": "${id}", "name": "${name}", "description": "${description}", "port": ${port}, "image": "localhost:5000/${imgName}:latest", "version": "latest" }' \
              http://localhost:8090/api/asset`);
};

// Define CLI using yargs
yargs(hideBin(process.argv))
  .command('run-chrome-dev', 'Run Chrome in development mode', {}, runChromeDev)
  .command('run-angular-client', 'Start the Angular client', {}, runAngularClient)
  .command('run-spring-boot-server', 'Start the Spring Boot server', {}, runSpringBootServer)
  .command('create-docker-registry', 'Create a local Docker registry', {}, createDockerRegistry)
  .command('create-k3d-registry', 'Create a K3d registry', {}, createK3dRegistry)
  .command('delete-docker-registry', 'Delete the local Docker registry', {}, deleteDockerRegistry)
  .command('delete-k3d-registry', 'Delete the K3d registry', {}, deleteK3dRegistry)
  .command('create-k3d-cluster', 'Create a K3d cluster', {}, createK3dCluster)
  .command('delete-k3d-cluster', 'Delete the K3d cluster', {}, deleteK3dCluster)
  .command('start-k3d-cluster', 'Start the K3d cluster', {}, startK3dCluster)
  .command('restart-k3d-cluster', 'Restart the K3d cluster', {}, restartK3dCluster)
  .command('build-and-register-image', 'Build a Docker image and register it', (yargs) => {
    yargs.option('dir', { describe: 'Directory containing the Dockerfile', demandOption: true })
         .option('imgName', { describe: 'Name of the Docker image', demandOption: true })
         .option('id', { describe: 'Asset ID', demandOption: true })
         .option('name', { describe: 'Asset Name', demandOption: true })
         .option('description', { describe: 'Asset Description', demandOption: true })
         .option('port', { describe: 'Asset Port', demandOption: true });
  }, (argv) => {
    buildAndRegisterImage(argv.dir, argv.imgName, argv.id, argv.name, argv.description, argv.port);
  })
  .demandCommand(1, 'You need to specify at least one command.')
  .help()
  .argv;
