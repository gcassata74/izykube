package com.izylife.izykube.web;


import com.izylife.izykube.services.DockerService;
import com.izylife.openapi.api.DockerApi;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/************************************************************************
 * Created: 10/08/22                                                    *
 * Author: Giuseppe Cassata                                             *
 ************************************************************************/
@RestController
public class DockerController implements DockerApi {

  @Autowired
  DockerService dockerService;

  @Autowired
  KubernetesClient client;

  @Override
  public ResponseEntity<String> createImagePost(@RequestParam("path") String path) {

    String imageId;
    // Use the Dockerfile to create an image
    try {
      imageId = dockerService.createImageFromDockerfile(path);
    } catch (Exception e) {
      return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
    return new ResponseEntity<>("Image created successfully", HttpStatus.OK);
  }


}
