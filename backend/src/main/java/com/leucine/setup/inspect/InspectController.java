package com.leucine.setup.inspect;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inspect/{connectionId}")
public class InspectController {

  private final InspectService inspect;

  public InspectController(InspectService inspect) {
    this.inspect = inspect;
  }

  @GetMapping("/organisations")
  public List<InspectService.NamedRow> orgs(@PathVariable String connectionId) {
    return inspect.organisations(connectionId);
  }

  @GetMapping("/organisations/{orgId}/facilities")
  public List<InspectService.NamedRow> facilities(@PathVariable String connectionId,
                                                  @PathVariable long orgId) {
    return inspect.facilities(connectionId, orgId);
  }

  @GetMapping("/organisations/{orgId}/usecases")
  public List<InspectService.NamedRow> useCasesForOrg(@PathVariable String connectionId,
                                                      @PathVariable long orgId) {
    return inspect.useCasesForOrganisation(connectionId, orgId);
  }

  @GetMapping("/organisations/{orgId}/licenses")
  public List<InspectService.LicenseRow> licenses(@PathVariable String connectionId,
                                                  @PathVariable long orgId) {
    return inspect.licensesForOrganisation(connectionId, orgId);
  }

  @GetMapping("/facilities/{facilityId}/usecases")
  public List<InspectService.NamedRow> useCases(@PathVariable String connectionId,
                                                @PathVariable long facilityId) {
    return inspect.useCasesForFacility(connectionId, facilityId);
  }

  @GetMapping("/facilities/{facilityId}/usecases-unmapped")
  public List<InspectService.NamedRow> useCasesUnmapped(@PathVariable String connectionId,
                                                        @PathVariable long facilityId) {
    return inspect.useCasesUnmappedToFacility(connectionId, facilityId);
  }

  @GetMapping("/usecases/{useCaseId}/properties")
  public List<InspectService.NamedRow> properties(@PathVariable String connectionId,
                                                  @PathVariable long useCaseId) {
    return inspect.propertiesForUseCase(connectionId, useCaseId);
  }

  @GetMapping("/organisations/{orgId}/feature-flags")
  public Map<String, Object> featureFlags(@PathVariable String connectionId, @PathVariable long orgId) {
    return inspect.featureFlags(connectionId, orgId);
  }
}
