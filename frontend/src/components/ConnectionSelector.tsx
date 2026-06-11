import { useConnections } from '../state/ConnectionContext';

export default function ConnectionSelector() {
  const { connections, selected, select } = useConnections();

  if (connections.length === 0) {
    return <span className="muted small">No connections — add one in <strong>Connections</strong></span>;
  }

  return (
    <label className="connection-picker">
      <span className="muted small">Target:</span>
      <select
        value={selected?.id ?? ''}
        onChange={e => select(e.target.value || null)}
      >
        <option value="">— select target —</option>
        {connections.map(c => (
          <option key={c.id} value={c.id}>{c.name} ({c.environment})</option>
        ))}
      </select>
      {selected && (
        <span className={`env-badge env-${selected.environment.toLowerCase()}`}>
          {selected.environment}
        </span>
      )}
    </label>
  );
}
