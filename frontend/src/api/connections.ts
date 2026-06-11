import axios from 'axios';
import type {
  Connection,
  CreateConnectionPayload,
  UpdateConnectionPayload,
  TestConnectionResult,
  DiscoverDatabasesPayload,
} from '../types';

const http = axios.create({ baseURL: '/api' });

export const connectionsApi = {
  list: () => http.get<Connection[]>('/connections').then(r => r.data),
  get: (id: string) => http.get<Connection>(`/connections/${id}`).then(r => r.data),
  create: (payload: CreateConnectionPayload) =>
    http.post<Connection>('/connections', payload).then(r => r.data),
  update: (id: string, payload: UpdateConnectionPayload) =>
    http.put<Connection>(`/connections/${id}`, payload).then(r => r.data),
  remove: (id: string) => http.delete(`/connections/${id}`).then(() => undefined),
  test: (id: string) =>
    http.post<TestConnectionResult>(`/connections/${id}/test`).then(r => r.data),
  listDatabases: (payload: DiscoverDatabasesPayload) =>
    http.post<string[]>('/connections/databases', payload).then(r => r.data),
};
