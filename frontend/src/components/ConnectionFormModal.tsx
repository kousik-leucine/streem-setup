import { FormEvent, useState } from 'react';
import { connectionsApi } from '../api/connections';
import type { Connection, Environment, SshAuthMethod, SshConfigInput } from '../types';

interface Props {
  existing: Connection | null;
  onClose: () => void;
  onSaved: () => void;
}

const ENVIRONMENTS: Environment[] = ['LOCAL', 'DEV', 'UAT', 'PROD'];
const SSL_MODES = ['', 'disable', 'require', 'verify-ca', 'verify-full'];

export default function ConnectionFormModal({ existing, onClose, onSaved }: Props) {
  const [name, setName] = useState(existing?.name ?? '');
  const [host, setHost] = useState(existing?.host ?? 'localhost');
  const [port, setPort] = useState(existing?.port ?? 5432);
  const [database, setDatabase] = useState(existing?.database ?? 'streem');
  const [username, setUsername] = useState(existing?.username ?? 'postgres');
  const [password, setPassword] = useState('');
  const [sslMode, setSslMode] = useState(existing?.sslMode ?? '');
  const [environment, setEnvironment] = useState<Environment>(existing?.environment ?? 'LOCAL');
  const [notes, setNotes] = useState(existing?.notes ?? '');

  // SSH section state
  const [useSsh, setUseSsh] = useState<boolean>(!!existing?.ssh);
  const [sshHost, setSshHost] = useState(existing?.ssh?.host ?? '');
  const [sshPort, setSshPort] = useState(existing?.ssh?.port ?? 22);
  const [sshUsername, setSshUsername] = useState(existing?.ssh?.username ?? '');
  const [sshAuth, setSshAuth] = useState<SshAuthMethod>(existing?.ssh?.authMethod ?? 'KEY');
  const [sshPassword, setSshPassword] = useState('');
  const [sshKeyPath, setSshKeyPath] = useState(existing?.ssh?.privateKeyPath ?? '');
  const [sshKeyPassphrase, setSshKeyPassphrase] = useState('');
  const [strictHostKey, setStrictHostKey] = useState(existing?.ssh?.strictHostKeyCheck ?? false);

  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // Database discovery: fetch the server's database list to offer a pick-list.
  const [dbOptions, setDbOptions] = useState<string[]>([]);
  const [loadingDbs, setLoadingDbs] = useState(false);
  const [dbError, setDbError] = useState<string | null>(null);

  const isEdit = !!existing;
  const passwordRequired = !isEdit;

  function buildSshPayload(): SshConfigInput | null {
    if (!useSsh) return null;
    return {
      host: sshHost,
      port: sshPort,
      username: sshUsername,
      authMethod: sshAuth,
      privateKeyPath: sshAuth === 'KEY' ? sshKeyPath || null : null,
      strictHostKeyCheck: strictHostKey,
      // Secrets only included when present; backend leaves stored value untouched on blank.
      password: sshAuth === 'PASSWORD' && sshPassword ? sshPassword : undefined,
      keyPassphrase: sshAuth === 'KEY' && sshKeyPassphrase ? sshKeyPassphrase : undefined,
    };
  }

  async function loadDatabases() {
    setDbError(null);
    if (!password) {
      // No stored secret is sent to the browser, so discovery needs the password in-hand.
      setDbError(isEdit ? 'Re-enter the password to load databases.' : 'Enter the password first.');
      return;
    }
    setLoadingDbs(true);
    try {
      const names = await connectionsApi.listDatabases({
        host, port: Number(port), username, password,
        sslMode: sslMode || undefined,
        ssh: buildSshPayload(),
      });
      setDbOptions(names);
      if (names.length === 0) setDbError('No databases visible to this user.');
    } catch (e: unknown) {
      setDbError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoadingDbs(false);
    }
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSaving(true);
    try {
      const ssh = buildSshPayload();
      const payload = {
        name, host, port: Number(port), database, username,
        password: password || undefined,
        sslMode: sslMode || undefined,
        environment,
        notes: notes || undefined,
        ssh,
      };
      if (isEdit) {
        await connectionsApi.update(existing!.id, payload);
      } else {
        await connectionsApi.create({ ...payload, password });
      }
      onSaved();
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      setError(msg);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={e => e.stopPropagation()}>
        <h3>{isEdit ? `Edit ${existing!.name}` : 'Add connection'}</h3>

        <form onSubmit={submit}>
          <h4 className="section-title">Postgres target</h4>

          <div className="form-row">
            <label className="grow">Name<input value={name} onChange={e => setName(e.target.value)} required /></label>
            <label>Environment
              <select value={environment} onChange={e => setEnvironment(e.target.value as Environment)}>
                {ENVIRONMENTS.map(env => <option key={env} value={env}>{env}</option>)}
              </select>
            </label>
          </div>

          <div className="form-row">
            <label className="grow">Host<input value={host} onChange={e => setHost(e.target.value)} required /></label>
            <label>Port<input type="number" value={port} onChange={e => setPort(Number(e.target.value))} required /></label>
          </div>

          <div className="form-row">
            <label className="grow">Database
              <input
                value={database}
                onChange={e => setDatabase(e.target.value)}
                list="discovered-databases"
                required
              />
              <datalist id="discovered-databases">
                {dbOptions.map(db => <option key={db} value={db} />)}
              </datalist>
            </label>
            <label>SSL
              <select value={sslMode ?? ''} onChange={e => setSslMode(e.target.value)}>
                {SSL_MODES.map(m => <option key={m} value={m}>{m || '(default)'}</option>)}
              </select>
            </label>
          </div>
          <div className="form-row">
            <button type="button" onClick={loadDatabases} disabled={loadingDbs || !host || !username}>
              {loadingDbs ? 'Loading…' : 'Load databases'}
            </button>
            {dbOptions.length > 0 && !dbError && (
              <span className="muted small">{dbOptions.length} database(s) found — pick one above or type.</span>
            )}
            {dbError && <span className="error small">{dbError}</span>}
          </div>

          <div className="form-row">
            <label className="grow">Username<input value={username} onChange={e => setUsername(e.target.value)} required /></label>
            <label className="grow">Password
              <input
                type="password"
                value={password}
                placeholder={isEdit ? '(unchanged)' : ''}
                onChange={e => setPassword(e.target.value)}
                required={passwordRequired}
              />
            </label>
          </div>

          <label className="checkbox section-toggle">
            <input type="checkbox" checked={useSsh} onChange={e => setUseSsh(e.target.checked)} />
            <span>Connect via SSH bastion</span>
          </label>

          {useSsh && (
            <div className="ssh-section">
              <p className="muted small">
                The tool will open an SSH session to the bastion, forward a local port to{' '}
                <code>{host || 'host'}:{port}</code> (as seen from the bastion), and connect Postgres through it.
              </p>

              <div className="form-row">
                <label className="grow">SSH host<input value={sshHost} onChange={e => setSshHost(e.target.value)} required={useSsh} placeholder="bastion.example.com" /></label>
                <label>SSH port<input type="number" value={sshPort} onChange={e => setSshPort(Number(e.target.value))} /></label>
              </div>

              <label>SSH username<input value={sshUsername} onChange={e => setSshUsername(e.target.value)} required={useSsh} /></label>

              <div className="form-row">
                <label className="checkbox">
                  <input type="radio" name="sshAuth" value="KEY" checked={sshAuth === 'KEY'} onChange={() => setSshAuth('KEY')} />
                  <span>Key file</span>
                </label>
                <label className="checkbox">
                  <input type="radio" name="sshAuth" value="PASSWORD" checked={sshAuth === 'PASSWORD'} onChange={() => setSshAuth('PASSWORD')} />
                  <span>Password</span>
                </label>
              </div>

              {sshAuth === 'KEY' && (
                <>
                  <label>Private key path
                    <input value={sshKeyPath} onChange={e => setSshKeyPath(e.target.value)} placeholder="C:\Users\you\.ssh\id_rsa" />
                  </label>
                  <label>Key passphrase <span className="muted small">(if the key is encrypted)</span>
                    <input
                      type="password"
                      value={sshKeyPassphrase}
                      placeholder={isEdit ? '(unchanged)' : ''}
                      onChange={e => setSshKeyPassphrase(e.target.value)}
                    />
                  </label>
                </>
              )}
              {sshAuth === 'PASSWORD' && (
                <label>SSH password
                  <input
                    type="password"
                    value={sshPassword}
                    placeholder={isEdit ? '(unchanged)' : ''}
                    onChange={e => setSshPassword(e.target.value)}
                  />
                </label>
              )}

              <label className="checkbox">
                <input type="checkbox" checked={strictHostKey} onChange={e => setStrictHostKey(e.target.checked)} />
                <span>Strict host key checking <span className="muted small">(off = auto-accept; recommended only after first connect)</span></span>
              </label>
            </div>
          )}

          <label>Notes
            <textarea value={notes ?? ''} onChange={e => setNotes(e.target.value)} rows={2} />
          </label>

          {error && <div className="error">{error}</div>}

          <div className="form-actions">
            <button type="button" onClick={onClose}>Cancel</button>
            <button type="submit" className="primary" disabled={saving}>
              {saving ? 'Saving…' : (isEdit ? 'Save' : 'Create')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
