import { useEffect, useState } from 'react';
import { useConnections } from '../state/ConnectionContext';
import { inspectApi } from '../api/inspect';
import { opsApi, type ExecuteResult, type SetFeatureFlagsPayload } from '../api/operations';
import { OrgSelect } from '../components/Selectors';
import PreviewExecuteBar from '../components/PreviewExecuteBar';
import OpResultCard from '../components/OpResultCard';
import { Page, NoTargetMessage } from '../components/PageShell';

export default function FeatureFlagsPage() {
  const { selected } = useConnections();
  const [orgId, setOrgId] = useState<number | null>(null);
  const [currentFlags, setCurrentFlags] = useState<Record<string, unknown> | null>(null);
  const [proposedFlags, setProposedFlags] = useState<Record<string, boolean>>({});
  const [newKey, setNewKey] = useState('');
  const [result, setResult] = useState<ExecuteResult | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  // Load current flags whenever the picked org changes.
  useEffect(() => {
    setCurrentFlags(null);
    setProposedFlags({});
    setLoadError(null);
    if (!selected || !orgId) return;

    let cancelled = false;
    inspectApi.featureFlags(selected.id, orgId)
      .then(obj => {
        if (cancelled) return;
        const parsed = obj ?? {};
        setCurrentFlags(parsed);
        // Seed the editable form with the boolean entries only.
        const seed: Record<string, boolean> = {};
        for (const [k, v] of Object.entries(parsed)) {
          if (typeof v === 'boolean') seed[k] = v;
        }
        setProposedFlags(seed);
      })
      .catch(e => {
        if (!cancelled) setLoadError(extractError(e));
      });
    return () => { cancelled = true; };
  }, [selected, orgId]);

  if (!selected) return <NoTargetMessage title="Feature flags" />;

  if (result) {
    return (
      <Page title="Feature flags">
        <OpResultCard result={result} onReset={() => { setResult(null); }} />
      </Page>
    );
  }

  function toggle(key: string, value: boolean) {
    setProposedFlags({ ...proposedFlags, [key]: value });
  }

  function addNewKey() {
    const k = newKey.trim();
    if (!k) return;
    if (k in proposedFlags) { setNewKey(''); return; }
    setProposedFlags({ ...proposedFlags, [k]: false });
    setNewKey('');
  }

  // What's actually changing (so we only merge the diff)
  const diff: Record<string, boolean> = {};
  for (const [k, v] of Object.entries(proposedFlags)) {
    const existing = currentFlags?.[k];
    if (existing !== v) diff[k] = v;
  }

  const payload: SetFeatureFlagsPayload | null = (orgId && Object.keys(diff).length > 0)
    ? { organisationId: orgId, flags: diff }
    : null;

  return (
    <Page title="Feature flags" target={selected.name}>
      <section className="step">
        <h3>Pick organisation</h3>
        <OrgSelect connectionId={selected.id} value={orgId} onChange={v => { setOrgId(v); setResult(null); }} />

        {orgId && (
          <>
            <h4>Flags</h4>
            {loadError && <div className="error">{loadError}</div>}
            {!loadError && currentFlags === null && <p className="muted">Loading current flags…</p>}

            {currentFlags && (
              <>
                {Object.keys(proposedFlags).length === 0 && (
                  <p className="muted small">No boolean flags on this org. Add one below.</p>
                )}

                {Object.entries(proposedFlags).map(([key, value]) => {
                  const wasSet = currentFlags && key in currentFlags;
                  const changed = currentFlags?.[key] !== value;
                  return (
                    <div key={key} className={`flag-row ${changed ? 'changed' : ''}`}>
                      <label className="checkbox">
                        <input type="checkbox" checked={value} onChange={e => toggle(key, e.target.checked)} />
                        <span><code>{key}</code></span>
                      </label>
                      <span className="muted small">
                        {wasSet
                          ? <>current: <strong>{String(currentFlags[key])}</strong></>
                          : <>new</>}
                        {changed && <> → <strong>{String(value)}</strong></>}
                      </span>
                    </div>
                  );
                })}

                <div className="form-row" style={{ marginTop: 12 }}>
                  <input
                    placeholder="new flag key, e.g. metabaseReports"
                    value={newKey}
                    onChange={e => setNewKey(e.target.value)}
                    onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addNewKey(); } }}
                  />
                  <button onClick={addNewKey} disabled={!newKey.trim()}>+ Add flag</button>
                </div>

                {currentFlags && Object.entries(currentFlags).some(([, v]) => typeof v !== 'boolean') && (
                  <div className="warnings">
                    <strong>Non-boolean flags on this org (left untouched):</strong>
                    <ul>
                      {Object.entries(currentFlags)
                        .filter(([, v]) => typeof v !== 'boolean')
                        .map(([k, v]) => <li key={k}><code>{k}</code> = {JSON.stringify(v)}</li>)}
                    </ul>
                  </div>
                )}
              </>
            )}

            <PreviewExecuteBar
              target={selected}
              canSubmit={payload !== null}
              preview={() => opsApi.previewSetFeatureFlags(selected.id, payload!)}
              execute={() => opsApi.executeSetFeatureFlags(selected.id, payload!)}
              onSuccess={setResult}
            />
          </>
        )}
      </section>
    </Page>
  );
}

function extractError(e: unknown): string {
  type AxiosLike = { response?: { data?: { error?: string } }; message?: string };
  const ax = e as AxiosLike;
  return ax?.response?.data?.error ?? ax?.message ?? String(e);
}
