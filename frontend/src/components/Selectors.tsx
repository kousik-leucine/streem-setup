import { useEffect, useState } from 'react';
import { inspectApi, type NamedRow } from '../api/inspect';

interface Props {
  connectionId: string;
  label: string;
  value: number | null;
  onChange: (id: number | null) => void;
  load: () => Promise<NamedRow[]>;
  emptyMessage?: string;
  cacheKey?: string;          // re-fetch when this changes
}

export function NamedRowSelect({ connectionId, label, value, onChange, load, emptyMessage, cacheKey }: Props) {
  const [rows, setRows] = useState<NamedRow[] | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    let cancel = false;
    setRows(null); setErr(null);
    load().then(r => { if (!cancel) setRows(r); })
          .catch(e => { if (!cancel) setErr(String(e?.message ?? e)); });
    return () => { cancel = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connectionId, cacheKey]);

  return (
    <label>{label}
      {err && <div className="error small">{err}</div>}
      {!err && rows === null && <input disabled value="loading…" />}
      {!err && rows !== null && rows.length === 0 && (
        <input disabled value={emptyMessage ?? 'none available'} />
      )}
      {!err && rows && rows.length > 0 && (
        <select value={value ?? ''} onChange={e => onChange(e.target.value ? Number(e.target.value) : null)}>
          <option value="">— select —</option>
          {rows.map(r => (
            <option key={r.id} value={r.id}>
              {r.name}{r.extra ? ` (${r.extra})` : ''} · id={r.id}
            </option>
          ))}
        </select>
      )}
    </label>
  );
}

export function OrgSelect({ connectionId, value, onChange }: { connectionId: string; value: number | null; onChange: (id: number | null) => void }) {
  return (
    <NamedRowSelect
      connectionId={connectionId}
      label="Organisation"
      value={value}
      onChange={onChange}
      load={() => inspectApi.orgs(connectionId)}
      emptyMessage="no organisations on this target"
    />
  );
}

export function FacilitySelect({ connectionId, orgId, value, onChange }: { connectionId: string; orgId: number; value: number | null; onChange: (id: number | null) => void }) {
  return (
    <NamedRowSelect
      connectionId={connectionId}
      label="Facility"
      value={value}
      onChange={onChange}
      cacheKey={`org-${orgId}`}
      load={() => inspectApi.facilities(connectionId, orgId)}
      emptyMessage="no facilities for this org"
    />
  );
}

export function UseCaseSelect({ connectionId, facilityId, mode, value, onChange }: { connectionId: string; facilityId: number; mode: 'mapped' | 'unmapped'; value: number | null; onChange: (id: number | null) => void }) {
  return (
    <NamedRowSelect
      connectionId={connectionId}
      label={mode === 'mapped' ? 'Use case (mapped to this facility)' : 'Use case (not yet mapped to this facility)'}
      value={value}
      onChange={onChange}
      cacheKey={`facility-${facilityId}-${mode}`}
      load={() => mode === 'mapped'
        ? inspectApi.useCases(connectionId, facilityId)
        : inspectApi.useCasesUnmapped(connectionId, facilityId)}
      emptyMessage={mode === 'mapped' ? 'no use cases mapped to this facility' : 'every use case is already mapped'}
    />
  );
}

export function PropertySelect({ connectionId, useCaseId, value, onChange }: { connectionId: string; useCaseId: number; value: number | null; onChange: (id: number | null) => void }) {
  return (
    <NamedRowSelect
      connectionId={connectionId}
      label="Property"
      value={value}
      onChange={onChange}
      cacheKey={`usecase-${useCaseId}`}
      load={() => inspectApi.properties(connectionId, useCaseId)}
      emptyMessage="no properties on this use case"
    />
  );
}
