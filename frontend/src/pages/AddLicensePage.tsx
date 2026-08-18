import { useEffect, useState } from 'react';
import { useConnections } from '../state/ConnectionContext';
import {
  opsApi,
  type AddLicensePayload,
  type ExecuteResult,
  type LicenseState,
  type LicenseWorkflow,
} from '../api/operations';
import { inspectApi, type LicenseRow, type NamedRow } from '../api/inspect';
import { OrgSelect } from '../components/Selectors';
import PreviewExecuteBar from '../components/PreviewExecuteBar';
import OpResultCard from '../components/OpResultCard';
import { Page, NoTargetMessage } from '../components/PageShell';

const STATES: Array<{ value: LicenseState; label: string; hint: string }> = [
  { value: 'GENUINE',        label: 'Genuine',        hint: 'paid, renewal far out — no banner' },
  { value: 'INTIMATE',       label: 'Intimate',       hint: 'paid, renewal inside the intimate window' },
  { value: 'GRACE',          label: 'Grace',          hint: 'unpaid, still inside the grace window' },
  { value: 'GRACE_EXCEEDED', label: 'Grace exceeded', hint: 'unpaid, grace window already over' },
  { value: 'CUSTOM',         label: 'Custom dates',   hint: 'type the dates and payment flag yourself' },
];

const WORKFLOWS: LicenseWorkflow[] = ['NOTIFICATION_UNBLOCKED', 'NOTIFICATION_BLOCKED', 'NONE'];

export default function AddLicensePage() {
  const { selected } = useConnections();

  const [orgId, setOrgId] = useState<number | null>(null);
  const [facilityIds, setFacilityIds] = useState<number[]>([]);
  const [useCaseIds, setUseCaseIds] = useState<number[]>([]);

  const [state, setState] = useState<LicenseState>('GENUINE');
  const [product, setProduct] = useState('dwi');
  const [type, setType] = useState('full');
  const [subscriptionPeriod, setSubscriptionPeriod] = useState(365);
  const [intimateBefore, setIntimateBefore] = useState(30);
  const [gracePeriod, setGracePeriod] = useState(30);
  const [workflow, setWorkflow] = useState<LicenseWorkflow>('NOTIFICATION_UNBLOCKED');
  const [featureRestrictions, setFeatureRestrictions] = useState('{}');
  const [skipExisting, setSkipExisting] = useState(true);
  const [startDate, setStartDate] = useState('');
  const [renewalDate, setRenewalDate] = useState('');
  const [paymentDone, setPaymentDone] = useState(true);
  const [showAdvanced, setShowAdvanced] = useState(false);

  const [result, setResult] = useState<ExecuteResult | null>(null);

  if (!selected) return <NoTargetMessage title="Add licenses" />;

  if (result) {
    return (
      <Page title="Add licenses">
        <OpResultCard result={result} onReset={() => { setResult(null); setUseCaseIds([]); }} />
      </Page>
    );
  }

  const pairCount = facilityIds.length * useCaseIds.length;
  const restrictionsValid = isJsonObject(featureRestrictions);
  const customValid = state !== 'CUSTOM' || (isIsoDate(startDate) && isIsoDate(renewalDate));
  const valid = !!orgId && pairCount > 0 && restrictionsValid && customValid;

  const payload: AddLicensePayload | null = valid ? {
    organisationId: orgId!,
    facilityIds,
    useCaseIds,
    product,
    type,
    state,
    subscriptionPeriod,
    intimateBefore,
    gracePeriod,
    workflow,
    featureRestrictions,
    skipExisting,
    ...(state === 'CUSTOM'
      ? { subscriptionStartDate: startDate, subscriptionRenewalDate: renewalDate, paymentDone }
      : {}),
  } : null;

  function resetOrg(v: number | null) {
    setOrgId(v);
    setFacilityIds([]);
    setUseCaseIds([]);
  }

  return (
    <Page title="Add licenses" target={selected.name}>
      <section className="step">
        <h3>1. Pick facilities and use cases</h3>
        <p className="muted small">
          One <code>licenses</code> row is created per facility x use case pair.
        </p>

        <OrgSelect connectionId={selected.id} value={orgId} onChange={resetOrg} />

        {orgId && (
          <div className="form-row">
            <CheckboxPicker
              label="Facilities"
              cacheKey={`fac-${selected.id}-${orgId}`}
              load={() => inspectApi.facilities(selected.id, orgId)}
              selected={facilityIds}
              onChange={setFacilityIds}
              emptyMessage="no facilities on this org"
            />
            <CheckboxPicker
              label="Use cases"
              cacheKey={`uc-${selected.id}-${orgId}`}
              load={() => inspectApi.useCasesForOrg(selected.id, orgId)}
              selected={useCaseIds}
              onChange={setUseCaseIds}
              emptyMessage="no use cases mapped to any facility of this org"
            />
          </div>
        )}

        <p className={pairCount > 0 ? 'ok' : 'muted'}>
          {facilityIds.length} facilit{facilityIds.length === 1 ? 'y' : 'ies'} x {useCaseIds.length} use
          case{useCaseIds.length === 1 ? '' : 's'} = <strong>{pairCount}</strong> license row
          {pairCount === 1 ? '' : 's'}
        </p>
      </section>

      <section className="step">
        <h3>2. Subscription state</h3>
        <div className="form-row">
          {STATES.map(s => (
            <label key={s.value} className="checkbox">
              <input
                type="radio"
                name="license-state"
                checked={state === s.value}
                onChange={() => setState(s.value)}
              />
              <span>{s.label} <span className="muted small">— {s.hint}</span></span>
            </label>
          ))}
        </div>

        {state !== 'CUSTOM' && (
          <p className="muted small">
            Dates are derived from today so jaas resolves this license to <code>{state}</code>. The exact
            values appear in the SQL preview.
          </p>
        )}

        {state === 'CUSTOM' && (
          <div className="form-row">
            <label>Subscription start
              <input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} />
            </label>
            <label>Renewal date
              <input type="date" value={renewalDate} onChange={e => setRenewalDate(e.target.value)} />
            </label>
            <label className="checkbox">
              <input type="checkbox" checked={paymentDone} onChange={e => setPaymentDone(e.target.checked)} />
              <span>Payment done</span>
            </label>
          </div>
        )}
      </section>

      <section className="step">
        <h3>
          3. License fields
          <button className="section-toggle" onClick={() => setShowAdvanced(!showAdvanced)}>
            {showAdvanced ? 'hide' : 'show'}
          </button>
        </h3>

        {showAdvanced && (
          <>
            <div className="form-row">
              <label>Product<input value={product} onChange={e => setProduct(e.target.value)} /></label>
              <label>Type<input value={type} onChange={e => setType(e.target.value)} /></label>
              <label>Workflow
                <select value={workflow} onChange={e => setWorkflow(e.target.value as LicenseWorkflow)}>
                  {WORKFLOWS.map(w => <option key={w} value={w}>{w}</option>)}
                </select>
              </label>
            </div>
            <div className="form-row">
              <label>Subscription period (days)
                <input type="number" value={subscriptionPeriod}
                       onChange={e => setSubscriptionPeriod(Number(e.target.value))} />
              </label>
              <label>Intimate before (days)
                <input type="number" value={intimateBefore}
                       onChange={e => setIntimateBefore(Number(e.target.value))} />
              </label>
              <label>Grace period (days)
                <input type="number" value={gracePeriod}
                       onChange={e => setGracePeriod(Number(e.target.value))} />
              </label>
            </div>
            <label>Feature restrictions (jsonb)
              <input value={featureRestrictions} onChange={e => setFeatureRestrictions(e.target.value)} />
            </label>
            {!restrictionsValid && <div className="error small">Must be a JSON object, e.g. {'{}'}</div>}
          </>
        )}

        <label className="checkbox">
          <input type="checkbox" checked={skipExisting} onChange={e => setSkipExisting(e.target.checked)} />
          <span>Skip pairs that already hold an unarchived license (otherwise the run is refused)</span>
        </label>

        <PreviewExecuteBar
          target={selected}
          canSubmit={payload !== null}
          preview={() => opsApi.previewAddLicense(selected.id, payload!)}
          execute={() => opsApi.executeAddLicense(selected.id, payload!)}
          onSuccess={setResult}
        />
      </section>

      {orgId && <ExistingLicenses connectionId={selected.id} orgId={orgId} />}
    </Page>
  );
}

/** Multi-select as a scrollable checkbox list — ids stay visible, which is what we match on. */
function CheckboxPicker({ label, cacheKey, load, selected, onChange, emptyMessage }: {
  label: string;
  cacheKey: string;
  load: () => Promise<NamedRow[]>;
  selected: number[];
  onChange: (ids: number[]) => void;
  emptyMessage: string;
}) {
  const [rows, setRows] = useState<NamedRow[] | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    let cancel = false;
    setRows(null); setErr(null);
    load().then(r => { if (!cancel) setRows(r); })
          .catch(e => { if (!cancel) setErr(String(e?.message ?? e)); });
    return () => { cancel = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cacheKey]);

  function toggle(id: number) {
    onChange(selected.includes(id) ? selected.filter(x => x !== id) : [...selected, id]);
  }

  const allSelected = !!rows && rows.length > 0 && selected.length === rows.length;

  return (
    <div className="grow">
      <div className="picker-head">
        <strong>{label}</strong>
        {rows && rows.length > 0 && (
          <button className="link" onClick={() => onChange(allSelected ? [] : rows.map(r => r.id))}>
            {allSelected ? 'clear' : 'select all'}
          </button>
        )}
      </div>
      {err && <div className="error small">{err}</div>}
      {!err && rows === null && <div className="muted small">loading…</div>}
      {!err && rows !== null && rows.length === 0 && <div className="muted small">{emptyMessage}</div>}
      {!err && rows && rows.length > 0 && (
        <div className="check-list">
          {rows.map(r => (
            <label key={r.id} className="checkbox">
              <input type="checkbox" checked={selected.includes(r.id)} onChange={() => toggle(r.id)} />
              <span>{r.name}{r.extra ? ` (${r.extra})` : ''} <span className="muted small">id={r.id}</span></span>
            </label>
          ))}
        </div>
      )}
    </div>
  );
}

function ExistingLicenses({ connectionId, orgId }: { connectionId: string; orgId: number }) {
  const [rows, setRows] = useState<LicenseRow[] | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    let cancel = false;
    setRows(null); setErr(null);
    inspectApi.licenses(connectionId, orgId)
      .then(r => { if (!cancel) setRows(r); })
      .catch(e => { if (!cancel) setErr(String(e?.message ?? e)); });
    return () => { cancel = true; };
  }, [connectionId, orgId]);

  return (
    <section className="step">
      <h3>Existing unarchived licenses on this org</h3>
      {err && <div className="error small">{err}</div>}
      {!err && rows === null && <div className="muted small">loading…</div>}
      {!err && rows?.length === 0 && <div className="muted small">none</div>}
      {!err && rows && rows.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>Facility</th><th>Use case</th><th>Product</th><th>Paid</th>
              <th>Start</th><th>Renewal</th><th>Grace</th><th>Workflow</th>
            </tr>
          </thead>
          <tbody>
            {rows.map(r => (
              <tr key={r.id}>
                <td>{r.facilityName ?? '—'} <span className="muted small">{r.facilityId}</span></td>
                <td>{r.useCaseName ?? <span className="muted">none</span>} <span className="muted small">{r.useCaseId ?? ''}</span></td>
                <td>{r.product}/{r.type}</td>
                <td>{r.paymentDone ? 'yes' : 'no'}</td>
                <td>{r.startDate}</td>
                <td>{r.renewalDate}</td>
                <td>{r.gracePeriod}d</td>
                <td>{r.workflow}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function isJsonObject(raw: string): boolean {
  try {
    const v = JSON.parse(raw);
    return !!v && typeof v === 'object' && !Array.isArray(v);
  } catch {
    return false;
  }
}

function isIsoDate(raw: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(raw);
}
