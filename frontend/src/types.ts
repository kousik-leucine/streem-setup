export type Environment = 'LOCAL' | 'DEV' | 'UAT' | 'PROD';
export type SshAuthMethod = 'PASSWORD' | 'KEY';

export interface SshConfig {
  host: string;
  port: number;
  username: string;
  authMethod: SshAuthMethod;
  privateKeyPath: string | null;
  strictHostKeyCheck: boolean;
}

export interface SshConfigInput extends SshConfig {
  password?: string;          // only on create / update-with-change
  keyPassphrase?: string;
}

export interface Connection {
  id: string;
  name: string;
  host: string;
  port: number;
  database: string;
  username: string;
  sslMode: string | null;
  environment: Environment;
  notes: string | null;
  createdAt: number;
  lastUsedAt: number | null;
  ssh: SshConfig | null;
}

export interface CreateConnectionPayload {
  name: string;
  host: string;
  port: number;
  database: string;
  username: string;
  password: string;
  sslMode?: string;
  environment: Environment;
  notes?: string;
  ssh?: SshConfigInput | null;
}

export type UpdateConnectionPayload = Omit<CreateConnectionPayload, 'password'> & {
  password?: string;
};

export interface TestConnectionResult {
  ok: boolean;
  error: string | null;
  serverVersion: string | null;
}

export interface DiscoverDatabasesPayload {
  host: string;
  port: number;
  username: string;
  password: string;
  sslMode?: string;
  maintenanceDatabase?: string;
  ssh?: SshConfigInput | null;
}
