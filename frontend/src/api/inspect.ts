import axios from 'axios';

const http = axios.create({ baseURL: '/api' });

export interface NamedRow {
  id: number;
  name: string;
  extra: string | null;
}

export interface LicenseRow {
  id: number;
  facilityId: number;
  facilityName: string | null;
  useCaseId: number | null;
  useCaseName: string | null;
  product: string | null;
  type: string | null;
  paymentDone: boolean;
  startDate: string | null;
  renewalDate: string | null;
  gracePeriod: number;
  intimateBefore: number;
  workflow: string | null;
}

export const inspectApi = {
  orgs: (connId: string) =>
    http.get<NamedRow[]>(`/inspect/${connId}/organisations`).then(r => r.data),
  useCasesForOrg: (connId: string, orgId: number) =>
    http.get<NamedRow[]>(`/inspect/${connId}/organisations/${orgId}/usecases`).then(r => r.data),
  licenses: (connId: string, orgId: number) =>
    http.get<LicenseRow[]>(`/inspect/${connId}/organisations/${orgId}/licenses`).then(r => r.data),
  facilities: (connId: string, orgId: number) =>
    http.get<NamedRow[]>(`/inspect/${connId}/organisations/${orgId}/facilities`).then(r => r.data),
  useCases: (connId: string, facilityId: number) =>
    http.get<NamedRow[]>(`/inspect/${connId}/facilities/${facilityId}/usecases`).then(r => r.data),
  useCasesUnmapped: (connId: string, facilityId: number) =>
    http.get<NamedRow[]>(`/inspect/${connId}/facilities/${facilityId}/usecases-unmapped`).then(r => r.data),
  properties: (connId: string, useCaseId: number) =>
    http.get<NamedRow[]>(`/inspect/${connId}/usecases/${useCaseId}/properties`).then(r => r.data),
  featureFlags: (connId: string, orgId: number) =>
    http.get<Record<string, unknown> | null>(`/inspect/${connId}/organisations/${orgId}/feature-flags`).then(r => r.data),
};
