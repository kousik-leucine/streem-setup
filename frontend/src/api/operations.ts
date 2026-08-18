import axios from 'axios';

const http = axios.create({ baseURL: '/api' });

export interface PreviewResult {
  sql: string;
  summary: string;
  warnings: string[];
}

export interface ExecuteResult {
  auditId: number;
  generatedIds: Record<string, unknown>;
  summary: string;
}

export interface NewOrgPayload {
  organisation: {
    name: string;
    fqdn: string;
    serviceId: string;
    serviceFqdn?: string;
  };
  facility: {
    name: string;
    timezone: string;
    dateFormat?: string;
    dateTimeFormat?: string;
    timeFormat?: string;
  };
  useCases: Array<{
    name: string;
    label: string;
    cardColor?: string;
    properties: Array<{
      name: string;
      label: string;
      placeholder?: string;
      mandatory: boolean;
    }>;
  }>;
  accountOwner: {
    username: string;
    email: string;
    firstName: string;
    lastName: string;
    employeeId: string;
    department?: string;
    password: string;
    challengeQuestionId?: number;
    challengeAnswer?: string;
  };
  passwordPolicy?: {
    maxAgeSeconds?: number;
    minLength?: number;
    minLowercase?: number;
    minUppercase?: number;
    minNumeric?: number;
    minSpecial?: number;
    minHistory?: number;
    expirationDays?: number;
  };
}

export interface AddFacilityPayload {
  organisationId: number;
  name: string;
  timezone: string;
  dateFormat?: string;
  dateTimeFormat?: string;
  timeFormat?: string;
}

export interface AddUseCasePayload {
  facilityId: number;
  name: string;
  label: string;
  cardColor?: string;
  properties: Array<{ name: string; label: string; placeholder?: string; mandatory: boolean }>;
}

export interface AddPropertyPayload {
  facilityId: number;
  useCaseId: number;
  name: string;
  label: string;
  placeholder?: string;
  mandatory: boolean;
}

/** Matches Misc.LicenseWorkflow in java-common-services/jaas. */
export type LicenseWorkflow = 'NONE' | 'NOTIFICATION_UNBLOCKED' | 'NOTIFICATION_BLOCKED';

/**
 * GENUINE / INTIMATE / GRACE / GRACE_EXCEEDED derive the dates so jaas reports
 * that state; CUSTOM uses subscriptionStartDate + subscriptionRenewalDate as typed.
 */
export type LicenseState = 'GENUINE' | 'INTIMATE' | 'GRACE' | 'GRACE_EXCEEDED' | 'CUSTOM';

export interface AddLicensePayload {
  organisationId: number;
  facilityIds: number[];
  useCaseIds: number[];
  product?: string;
  type?: string;
  state?: LicenseState;
  subscriptionStartDate?: string;      // ISO, only read when state = CUSTOM
  subscriptionRenewalDate?: string;    // ISO, only read when state = CUSTOM
  paymentDone?: boolean;               // only read when state = CUSTOM
  subscriptionPeriod?: number;
  intimateBefore?: number;
  gracePeriod?: number;
  workflow?: LicenseWorkflow;
  featureRestrictions?: string;
  skipExisting?: boolean;
}

export interface MapUseCasePayload {
  facilityId: number;
  useCaseId: number;
  includeProperties?: boolean;
}

export interface MapPropertyPayload {
  facilityId: number;
  useCaseId: number;
  propertyId: number;
  labelAlias?: string;
  placeholderAlias?: string;
  mandatory: boolean;
}

export interface SetFeatureFlagsPayload {
  organisationId: number;
  flags: Record<string, boolean>;
}

function preview<T>(op: string) {
  return (connectionId: string, payload: T) =>
    http.post<PreviewResult>(`/ops/${op}/preview`, { connectionId, payload }).then(r => r.data);
}
function execute<T>(op: string) {
  return (connectionId: string, payload: T) =>
    http.post<ExecuteResult>(`/ops/${op}/execute`, { connectionId, payload }).then(r => r.data);
}

export const opsApi = {
  previewNewOrg:       preview<NewOrgPayload>('new-org'),
  executeNewOrg:       execute<NewOrgPayload>('new-org'),
  previewAddFacility:  preview<AddFacilityPayload>('add-facility'),
  executeAddFacility:  execute<AddFacilityPayload>('add-facility'),
  previewAddUseCase:   preview<AddUseCasePayload>('add-usecase'),
  executeAddUseCase:   execute<AddUseCasePayload>('add-usecase'),
  previewAddProperty:  preview<AddPropertyPayload>('add-property'),
  executeAddProperty:  execute<AddPropertyPayload>('add-property'),
  previewAddLicense:   preview<AddLicensePayload>('add-license'),
  executeAddLicense:   execute<AddLicensePayload>('add-license'),
  previewMapUseCase:   preview<MapUseCasePayload>('map-usecase'),
  executeMapUseCase:   execute<MapUseCasePayload>('map-usecase'),
  previewMapProperty:  preview<MapPropertyPayload>('map-property'),
  executeMapProperty:  execute<MapPropertyPayload>('map-property'),
  previewSetFeatureFlags: preview<SetFeatureFlagsPayload>('set-feature-flags'),
  executeSetFeatureFlags: execute<SetFeatureFlagsPayload>('set-feature-flags'),
};
