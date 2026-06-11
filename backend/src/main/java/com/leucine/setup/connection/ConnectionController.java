package com.leucine.setup.connection;

import com.leucine.setup.connection.dto.ConnectionResponse;
import com.leucine.setup.connection.dto.CreateConnectionRequest;
import com.leucine.setup.connection.dto.DiscoverDatabasesRequest;
import com.leucine.setup.connection.dto.TestConnectionResult;
import com.leucine.setup.connection.dto.UpdateConnectionRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

  private final ConnectionService service;

  public ConnectionController(ConnectionService service) {
    this.service = service;
  }

  @GetMapping
  public List<ConnectionResponse> list() {
    return service.list().stream().map(ConnectionResponse::from).toList();
  }

  @GetMapping("/{id}")
  public ResponseEntity<ConnectionResponse> get(@PathVariable String id) {
    return service.get(id)
        .map(ConnectionResponse::from)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping
  public ConnectionResponse create(@Valid @RequestBody CreateConnectionRequest req) {
    return ConnectionResponse.from(service.create(req));
  }

  @PutMapping("/{id}")
  public ConnectionResponse update(@PathVariable String id,
                                   @Valid @RequestBody UpdateConnectionRequest req) {
    return ConnectionResponse.from(service.update(id, req));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    return service.delete(id)
        ? ResponseEntity.noContent().build()
        : ResponseEntity.notFound().build();
  }

  @PostMapping("/{id}/test")
  public TestConnectionResult test(@PathVariable String id) {
    return service.test(id);
  }

  /** Enumerate databases on a server from unsaved credentials, to fill the form's picker. */
  @PostMapping("/databases")
  public List<String> databases(@Valid @RequestBody DiscoverDatabasesRequest req) {
    return service.listDatabases(req);
  }
}
