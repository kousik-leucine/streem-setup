import { useEffect, useState } from 'react';
import { connectionsApi } from '../api/connections';
import type { Connection, TestConnectionResult } from '../types';
import ConnectionFormModal from '../components/ConnectionFormModal';

export default function ConnectionsPage() {
  const [conns, setConns] = useState<Connection[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<Connection | null>(null);
  const [creating, setCreating] = useState(false);
  const [testResults, setTestResults] = useState<Record<string, TestConnectionResult>>({});
  const [testing, setTesting] = useState<Record<string, boolean>>({});

  async function refresh() {
    setLoading(true);
    try {
      setConns(await connectionsApi.list());
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    refresh();
  }, []);

  async function handleTest(id: string) {
    setTesting(prev => ({ ...prev, [id]: true }));
    try {
      const result = await connectionsApi.test(id);
      setTestResults(prev => ({ ...prev, [id]: result }));
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      setTestResults(prev => ({ ...prev, [id]: { ok: false, error: msg, serverVersion: null } }));
    } finally {
      setTesting(prev => ({ ...prev, [id]: false }));
    }
  }

  async function handleDelete(c: Connection) {
    if (!confirm(`Delete connection "${c.name}"?`)) return;
    await connectionsApi.remove(c.id);
    await refresh();
  }

  return (
    <div className="page">
      <div className="page-header">
        <h2>Connections</h2>
        <button className="primary" onClick={() => setCreating(true)}>
          + Add connection
        </button>
      </div>

      {loading && <p>Loading…</p>}

      {!loading && conns.length === 0 && (
        <div className="empty">
          <p>No connections configured.</p>
          <p>Click <strong>Add connection</strong> to create your first target.</p>
        </div>
      )}

      {!loading && conns.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Environment</th>
              <th>Host</th>
              <th>Database</th>
              <th>User</th>
              <th>Last test</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {conns.map(c => {
              const result = testResults[c.id];
              return (
                <tr key={c.id}>
                  <td>
                    <strong>{c.name}</strong>
                    {c.notes && <div className="muted small">{c.notes}</div>}
                  </td>
                  <td>
                    <span className={`env-badge env-${c.environment.toLowerCase()}`}>
                      {c.environment}
                    </span>
                  </td>
                  <td>{c.host}:{c.port}</td>
                  <td>{c.database}</td>
                  <td>{c.username}</td>
                  <td>
                    {testing[c.id] && <span className="muted">testing…</span>}
                    {!testing[c.id] && result?.ok && (
                      <span className="ok" title={result.serverVersion ?? ''}>✓ ok</span>
                    )}
                    {!testing[c.id] && result && !result.ok && (
                      <span className="fail" title={result.error ?? ''}>✗ {result.error}</span>
                    )}
                    {!testing[c.id] && !result && <span className="muted">—</span>}
                  </td>
                  <td className="actions">
                    <button onClick={() => handleTest(c.id)} disabled={testing[c.id]}>Test</button>
                    <button onClick={() => setEditing(c)}>Edit</button>
                    <button className="danger" onClick={() => handleDelete(c)}>Delete</button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}

      {(creating || editing) && (
        <ConnectionFormModal
          existing={editing}
          onClose={() => { setCreating(false); setEditing(null); }}
          onSaved={async () => {
            setCreating(false);
            setEditing(null);
            await refresh();
          }}
        />
      )}
    </div>
  );
}
