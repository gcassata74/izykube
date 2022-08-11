package com.izylife.izykube.web;

import com.izylife.openapi.api.IzyApi;
import com.izylife.openapi.model.Pullrequest;
import com.izylife.openapi.model.Repository;
import com.izylife.openapi.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/************************************************************************
 * Created: 10/08/22                                                    *
 * Author: Giuseppe Cassata                                             *
 ************************************************************************/
@RestController
public class ExampleController implements IzyApi {

  @Override
  public ResponseEntity<Pullrequest> getPullRequestsById(String username, String slug, String pid) {

    User user = new User();
    user.setUsername("gcassata");
    user.setUuid(UUID.randomUUID().toString());

    Pullrequest pr = new Pullrequest();
    pr.setAuthor(user);
    pr.setId(1);
    pr.setTitle("Prova");
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
