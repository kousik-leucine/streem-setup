package com.leucine.setup.operations.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leucine.setup.audit.AuditService;
import com.leucine.setup.connection.Connection;
import com.leucine.setup.connection.ConnectionService;
import com.leucine.setup.datasource.TargetConnection;
import com.leucine.setup.datasource.TargetDataSourceFactory;
import com.leucine.setup.operations.ExecuteResult;
import com.leucine.setup.operations.IdGenerator;
import com.leucine.setup.operations.Operation;
import com.leucine.setup.operations.OperationContext;
import com.leucine.setup.operations.PreviewResult;
import com.leucine.setup.operations.addFacility.AddFacilityOperation;
import com.leucine.setup.operations.addFacility.AddFacilityPayload;
import com.leucine.setup.operations.addLicense.AddLicenseOperation;
import com.leucine.setup.operations.addLicense.AddLicensePayload;
import com.leucine.setup.operations.addProperty.AddPropertyOperation;
import com.leucine.setup.operations.addProperty.AddPropertyPayload;
import com.leucine.setup.operations.addUseCase.AddUseCaseOperation;
import com.leucine.setup.operations.addUseCase.AddUseCasePayload;
import com.leucine.setup.operations.mapProperty.MapPropertyOperation;
import com.leucine.setup.operations.mapProperty.MapPropertyPayload;
import com.leucine.setup.operations.mapUseCase.MapUseCaseOperation;
import com.leucine.setup.operations.mapUseCase.MapUseCasePayload;
import com.leucine.setup.operations.newOrg.NewOrgOperation;
import com.leucine.setup.operations.newOrg.NewOrgPayload;
import com.leucine.setup.operations.setFeatureFlags.SetFeatureFlagsOperation;
import com.leucine.setup.operations.setFeatureFlags.SetFeatureFlagsPayload;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ops")
public class OperationController {

  private static final Logger log = LoggerFactory.getLogger(OperationController.class);

  private final ConnectionService connections;
  private final TargetDataSourceFactory dsFactory;
  private final AuditService audit;
  private final ObjectMapper json;

  private final NewOrgOperation newOrg;
  private final AddFacilityOperation addFacility;
  private final AddUseCaseOperation addUseCase;
  private final AddPropertyOperation addProperty;
  private final AddLicenseOperation addLicense;
  private final MapUseCaseOperation mapUseCase;
  private final MapPropertyOperation mapProperty;
  private final SetFeatureFlagsOperation setFeatureFlags;

  public OperationController(ConnectionService connections,
                             TargetDataSourceFactory dsFactory,
                             AuditService audit,
                             ObjectMapper json,
                             NewOrgOperation newOrg,
                             AddFacilityOperation addFacility,
                             AddUseCaseOperation addUseCase,
                             AddPropertyOperation addProperty,
                             AddLicenseOperation addLicense,
                             MapUseCaseOperation mapUseCase,
                             MapPropertyOperation mapProperty,
                             SetFeatureFlagsOperation setFeatureFlags) {
    this.connections = connections;
    this.dsFactory = dsFactory;
    this.audit = audit;
    this.json = json;
    this.newOrg = newOrg;
    this.addFacility = addFacility;
    this.addUseCase = addUseCase;
    this.addProperty = addProperty;
    this.addLicense = addLicense;
    this.mapUseCase = mapUseCase;
    this.mapProperty = mapProperty;
    this.setFeatureFlags = setFeatureFlags;
  }

  // ----- new-org -----
  @PostMapping("/new-org/preview")
  public PreviewResult newOrgPreview(@Valid @RequestBody OpRequest<NewOrgPayload> req) { return runPreview(req, newOrg); }
  @PostMapping("/new-org/execute")
  public ExecuteResult newOrgExecute(@Valid @RequestBody OpRequest<NewOrgPayload> req) { return runExecute(req, newOrg); }

  // ----- add-facility -----
  @PostMapping("/add-facility/preview")
  public PreviewResult addFacilityPreview(@Valid @RequestBody OpRequest<AddFacilityPayload> req) { return runPreview(req, addFacility); }
  @PostMapping("/add-facility/execute")
  public ExecuteResult addFacilityExecute(@Valid @RequestBody OpRequest<AddFacilityPayload> req) { return runExecute(req, addFacility); }

  // ----- add-usecase -----
  @PostMapping("/add-usecase/preview")
  public PreviewResult addUseCasePreview(@Valid @RequestBody OpRequest<AddUseCasePayload> req) { return runPreview(req, addUseCase); }
  @PostMapping("/add-usecase/execute")
  public ExecuteResult addUseCaseExecute(@Valid @RequestBody OpRequest<AddUseCasePayload> req) { return runExecute(req, addUseCase); }

  // ----- add-property -----
  @PostMapping("/add-property/preview")
  public PreviewResult addPropertyPreview(@Valid @RequestBody OpRequest<AddPropertyPayload> req) { return runPreview(req, addProperty); }
  @PostMapping("/add-property/execute")
  public ExecuteResult addPropertyExecute(@Valid @RequestBody OpRequest<AddPropertyPayload> req) { return runExecute(req, addProperty); }

  // ----- add-license -----
  @PostMapping("/add-license/preview")
  public PreviewResult addLicensePreview(@Valid @RequestBody OpRequest<AddLicensePayload> req) { return runPreview(req, addLicense); }
  @PostMapping("/add-license/execute")
  public ExecuteResult addLicenseExecute(@Valid @RequestBody OpRequest<AddLicensePayload> req) { return runExecute(req, addLicense); }

  // ----- map-usecase -----
  @PostMapping("/map-usecase/preview")
  public PreviewResult mapUseCasePreview(@Valid @RequestBody OpRequest<MapUseCasePayload> req) { return runPreview(req, mapUseCase); }
  @PostMapping("/map-usecase/execute")
  public ExecuteResult mapUseCaseExecute(@Valid @RequestBody OpRequest<MapUseCasePayload> req) { return runExecute(req, mapUseCase); }

  // ----- map-property -----
  @PostMapping("/map-property/preview")
  public PreviewResult mapPropertyPreview(@Valid @RequestBody OpRequest<MapPropertyPayload> req) { return runPreview(req, mapProperty); }
  @PostMapping("/map-property/execute")
  public ExecuteResult mapPropertyExecute(@Valid @RequestBody OpRequest<MapPropertyPayload> req) { return runExecute(req, mapProperty); }

  // ----- set-feature-flags -----
  @PostMapping("/set-feature-flags/preview")
  public PreviewResult setFeatureFlagsPreview(@Valid @RequestBody OpRequest<SetFeatureFlagsPayload> req) { return runPreview(req, setFeatureFlags); }
  @PostMapping("/set-feature-flags/execute")
  public ExecuteResult setFeatureFlagsExecute(@Valid @RequestBody OpRequest<SetFeatureFlagsPayload> req) { return runExecute(req, setFeatureFlags); }

  // ----- shared plumbing -----

  private <P> PreviewResult runPreview(OpRequest<P> req, Operation<P> op) {
    Connection conn = lookup(req.connectionId());
    try (TargetConnection tc = dsFactory.open(conn, connections.decryptedSecrets(conn.id()))) {
      OperationContext ctx = new OperationContext(conn, tc.jdbc(), new IdGenerator());
      return op.preview(req.payload(), ctx);
    }
  }

  private <P> ExecuteResult runExecute(OpRequest<P> req, Operation<P> op) {
    Connection conn = lookup(req.connectionId());
    try (TargetConnection tc = dsFactory.open(conn, connections.decryptedSecrets(conn.id()))) {
      OperationContext ctx = new OperationContext(conn, tc.jdbc(), new IdGenerator());
      PreviewResult preview = op.preview(req.payload(), ctx);
      String payloadJson = serialize(req.payload());
      long auditId = audit.start(conn.id(), op.name(), payloadJson, preview.sql());
      try {
        ExecuteResult result = op.execute(req.payload(), ctx);
        audit.succeed(auditId, preview.sql());
        log.info("{} completed against {} (audit id {})", op.name(), conn.name(), auditId);
        return new ExecuteResult(auditId, result.generatedIds(), result.summary());
      } catch (RuntimeException e) {
        audit.fail(auditId, preview.sql(), e);
        throw e;
      }
    }
  }

  private Connection lookup(String connectionId) {
    return connections.get(connectionId)
        .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));
  }

  private String serialize(Object o) {
    try { return json.writeValueAsString(o); }
    catch (JsonProcessingException e) { return "<<serialize error: " + e.getMessage() + ">>"; }
  }
}
