package com.izylife.izykube.web;

import com.izylife.openapi.api.IzyApi;
import com.izylife.openapi.model.Pullrequest;
import com.izylife.openapi.model.Repository;
import com.izylife.openapi.model.User;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/************************************************************************
 * Created: 10/08/22                                                    *
 * Author: Giuseppe Cassata                                             *
 ************************************************************************/
@RestController
public class ExampleController implements IzyApi {
  @Autowired
  KubernetesClient client;


  @Override
  public ResponseEntity<Pullrequest> getPullRequestsById(String username, String slug, String pid) {

    Pullrequest pr = new Pullrequest();


    Deployment deployment = new DeploymentBuilder()
      .withNewMetadata()
      .withName("nginx-deployment")
      .addToLabels("app", "nginx")
      .endMetadata()
      .withNewSpec()
      .withReplicas(1)
      .withNewSelector()
      .addToMatchLabels("app", "nginx")
      .endSelector()
      .withNewTemplate()
      .withNewMetadata()
      .addToLabels("app", "nginx")
      .endMetadata()
      .withNewSpec()
      .addNewContainer()
      .withName("nginx")
      .withImage("nginx:1.7.9")
      .addNewPort().withContainerPort(80).endPort()
      .endContainer()
      .endSpec()
      .endTemplate()
      .endSpec()
      .build();

    client.resource(deployment).inNamespace("default").createOrReplace();

    return new ResponseEntity<Pullrequest>(pr, HttpStatus.OK);

  }

  @Override
  public ResponseEntity<List<Pullrequest>> getPullRequestsByRepository(String username, String slug, String state) {
    return null;
  }

  @Override
  public ResponseEntity<List<Repository>> getRepositoriesByOwner(String username) {
    return null;
  }

  @Override
  public ResponseEntity<Repository> getRepository(String username, String slug) {
    return null;
  }

  @Override
  public ResponseEntity<User> getUserByName(String username) {
    return null;
  }

  @Override
  public ResponseEntity<Void> mergePullRequest(String username, String slug, String pid) {
    return null;
  }
}
