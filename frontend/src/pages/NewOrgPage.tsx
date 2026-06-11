import { useState } from 'react';
import { useConnections } from '../state/ConnectionContext';
import { opsApi, type NewOrgPayload, type PreviewResult, type ExecuteResult } from '../api/operations';

const DEFAULT_PAYLOAD: NewOrgPayload = {
  organisation: { name: '', fqdn: '', serviceId: '', serviceFqdn: '' },
  facility: {
    name: '', timezone: 'UTC',
    dateFormat: 'MMM dd, yyyy',
    dateTimeFormat: 'MMM dd, yyyy HH:mm',
    timeFormat: 'HH:mm',
  },
  useCases: [{
    name: 'Digital Logbooks', label: 'Digital Logbooks', cardColor: '#EDF5FF',
    properties: [
      { name: 'reference-sop-number', label: 'Reference SOP Number', mandatory: false },
      { name: 'format-number', label: 'Format Number', mandatory: false },
      { name: 'change-control-reference', label: 'Change Control Reference', mandatory: true },
    ],
  }],
  accountOwner: {
    username: '', email: '', firstName: '', lastName: '',
    employeeId: '', department: 'SDE-0',
    password: '',
    challengeQuestionId: 1, challengeAnswer: 'leucine',
  },
};

export default function NewOrgPage() {
  const { selected } = useConnections();
  const [step, setStep] = useState(1);
  const [data, setData] = useState<NewOrgPayload>(DEFAULT_PAYLOAD);
  const [preview, setPreview] = useState<PreviewResult | null>(null);
  const [result, setResult] = useState<ExecuteResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirmText, setConfirmText] = useState('');

  if (!selected) {
    return (
      <div className="page">
        <h2>New Organisation</h2>
        <div className="empty">
          <p>Select a target connection from the top bar to continue.</p>
        </div>
      </div>
    );
  }

  if (result) {
    return (
      <div className="page">
        <h2>Organisation created</h2>
        <div className="result-card">
          <p className="ok">✓ {result.summary}</p>
          <p className="muted small">Audit id: {result.auditId}</p>
          <h4>Generated IDs</h4>
          <pre>{JSON.stringify(result.generatedIds, null, 2)}</pre>
          <button onClick={() => { setResult(null); setStep(1); setData(DEFAULT_PAYLOAD); setPreview(null); }}>
            Start another
          </button>
        </div>
      </div>
    );
  }

  async function loadPreview() {
    setBusy(true); setError(null);
    try {
      const p = await opsApi.previewNewOrg(selected!.id, data);
      setPreview(p);
      setStep(5);
    } catch (e: unknown) {
      setError(extractError(e));
    } finally {
      setBusy(false);
    }
  }

  async function executeNow() {
    if (selected!.environment === 'PROD' && confirmText !== selected!.name) {
      setError(`Type "${selected!.name}" to confirm PROD execution`);
      return;
    }
    setBusy(true); setError(null);
    try {
      const r = await opsApi.executeNewOrg(selected!.id, data);
      setResult(r);
    } catch (e: unknown) {
      setError(extractError(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page">
      <div className="page-header">
        <h2>New Organisation</h2>
        <span className="muted small">target: <strong>{selected.name}</strong></span>
      </div>

      <ol className="stepper">
        {['Organisation', 'Facility', 'Use cases', 'Account owner', 'Review'].map((label, i) => {
          const n = i + 1;
          const cls = step === n ? 'current' : step > n ? 'done' : '';
          return <li key={label} className={cls} onClick={() => step > n && setStep(n)}>{n}. {label}</li>;
        })}
      </ol>

      {error && <div className="error">{error}</div>}

      {step === 1 && <StepOrganisation data={data} setData={setData} onNext={() => setStep(2)} />}
      {step === 2 && <StepFacility data={data} setData={setData} onBack={() => setStep(1)} onNext={() => setStep(3)} />}
      {step === 3 && <StepUseCases data={data} setData={setData} onBack={() => setStep(2)} onNext={() => setStep(4)} />}
      {step === 4 && <StepOwner data={data} setData={setData} onBack={() => setStep(3)} onNext={loadPreview} busy={busy} />}
      {step === 5 && preview && (
        <StepReview
          preview={preview}
          target={selected.name}
          environment={selected.environment}
          confirmText={confirmText}
          setConfirmText={setConfirmText}
          onBack={() => setStep(4)}
          onExecute={executeNow}
          busy={busy}
        />
      )}
    </div>
  );
}

function extractError(e: unknown): string {
  // axios error shape
  type AxiosLike = { response?: { data?: { error?: string } }; message?: string };
  const ax = e as AxiosLike;
  return ax?.response?.data?.error ?? ax?.message ?? String(e);
}

// ---------- step components ----------

interface StepProps {
  data: NewOrgPayload;
  setData: (d: NewOrgPayload) => void;
  onBack?: () => void;
  onNext: () => void;
  busy?: boolean;
}

function StepOrganisation({ data, setData, onNext }: StepProps) {
  const o = data.organisation;
  const set = (patch: Partial<typeof o>) =>
    setData({ ...data, organisation: { ...o, ...patch } });
  return (
    <section className="step">
      <h3>Organisation</h3>
      <label>Name<input value={o.name} onChange={e => set({ name: e.target.value })} /></label>
      <label>FQDN<input value={o.fqdn} onChange={e => set({ fqdn: e.target.value })} placeholder="https://customer.platform.leucine.tech/" /></label>
      <label>Service ID<input value={o.serviceId} onChange={e => set({ serviceId: e.target.value })} placeholder="c6d8285b72a84efb8fbd608c7cada484" /></label>
      <label>Service FQDN <span className="muted small">(optional, defaults to FQDN)</span>
        <input value={o.serviceFqdn ?? ''} onChange={e => set({ serviceFqdn: e.target.value })} />
      </label>
      <div className="form-actions">
        <button className="primary" disabled={!o.name || !o.fqdn || !o.serviceId} onClick={onNext}>Next</button>
      </div>
    </section>
  );
}

function StepFacility({ data, setData, onBack, onNext }: StepProps) {
  const f = data.facility;
  const set = (patch: Partial<typeof f>) =>
    setData({ ...data, facility: { ...f, ...patch } });
  return (
    <section className="step">
      <h3>First facility</h3>
      <label>Name<input value={f.name} onChange={e => set({ name: e.target.value })} /></label>
      <label>Timezone<input value={f.timezone} onChange={e => set({ timezone: e.target.value })} placeholder="UTC | Asia/Kolkata | America/New_York" /></label>
      <div className="form-row">
        <label className="grow">Date format<input value={f.dateFormat ?? ''} onChange={e => set({ dateFormat: e.target.value })} /></label>
        <label className="grow">Date+time format<input value={f.dateTimeFormat ?? ''} onChange={e => set({ dateTimeFormat: e.target.value })} /></label>
        <label>Time format<input value={f.timeFormat ?? ''} onChange={e => set({ timeFormat: e.target.value })} /></label>
      </div>
      <div className="form-actions">
        <button onClick={onBack}>Back</button>
        <button className="primary" disabled={!f.name || !f.timezone} onClick={onNext}>Next</button>
      </div>
    </section>
  );
}

function StepUseCases({ data, setData, onBack, onNext }: StepProps) {
  function patchUC(i: number, patch: Partial<NewOrgPayload['useCases'][number]>) {
    const next = [...data.useCases];
    next[i] = { ...next[i], ...patch };
    setData({ ...data, useCases: next });
  }
  function addUC() {
    setData({ ...data, useCases: [...data.useCases, { name: '', label: '', cardColor: '#EDF5FF', properties: [] }] });
  }
  function removeUC(i: number) {
    setData({ ...data, useCases: data.useCases.filter((_, idx) => idx !== i) });
  }
  function patchProp(uci: number, pi: number, patch: Partial<NewOrgPayload['useCases'][number]['properties'][number]>) {
    const ucs = [...data.useCases];
    const props = [...ucs[uci].properties];
    props[pi] = { ...props[pi], ...patch };
    ucs[uci] = { ...ucs[uci], properties: props };
    setData({ ...data, useCases: ucs });
  }
  function addProp(uci: number) {
    const ucs = [...data.useCases];
    ucs[uci] = { ...ucs[uci], properties: [...ucs[uci].properties, { name: '', label: '', mandatory: false }] };
    setData({ ...data, useCases: ucs });
  }
  function removeProp(uci: number, pi: number) {
    const ucs = [...data.useCases];
    ucs[uci] = { ...ucs[uci], properties: ucs[uci].properties.filter((_, idx) => idx !== pi) };
    setData({ ...data, useCases: ucs });
  }

  const valid = data.useCases.length > 0
    && data.useCases.every(uc => uc.name && uc.label
      && uc.properties.length > 0
      && uc.properties.every(p => p.name && p.label));

  return (
    <section className="step">
      <h3>Use cases &amp; properties</h3>
      {data.useCases.map((uc, i) => (
        <div key={i} className="usecase-card">
          <div className="form-row">
            <label className="grow">Use case name<input value={uc.name} onChange={e => patchUC(i, { name: e.target.value })} /></label>
            <label className="grow">Label<input value={uc.label} onChange={e => patchUC(i, { label: e.target.value })} /></label>
            <label>Card color<input value={uc.cardColor ?? ''} onChange={e => patchUC(i, { cardColor: e.target.value })} /></label>
            <button className="danger" onClick={() => removeUC(i)}>Remove</button>
          </div>

          <h4>Properties</h4>
          {uc.properties.map((p, pi) => (
            <div key={pi} className="form-row property-row">
              <label className="grow">Name<input value={p.name} onChange={e => patchProp(i, pi, { name: e.target.value })} /></label>
              <label className="grow">Label<input value={p.label} onChange={e => patchProp(i, pi, { label: e.target.value })} /></label>
              <label className="checkbox">
                <input type="checkbox" checked={p.mandatory} onChange={e => patchProp(i, pi, { mandatory: e.target.checked })} />
                <span>Mandatory</span>
              </label>
              <button className="danger" onClick={() => removeProp(i, pi)}>×</button>
            </div>
          ))}
          <button onClick={() => addProp(i)}>+ Add property</button>
        </div>
      ))}
      <button onClick={addUC}>+ Add use case</button>

      <div className="form-actions">
        <button onClick={onBack}>Back</button>
        <button className="primary" disabled={!valid} onClick={onNext}>Next</button>
      </div>
    </section>
  );
}

function StepOwner({ data, setData, onBack, onNext, busy }: StepProps) {
  const o = data.accountOwner;
  const set = (patch: Partial<typeof o>) =>
    setData({ ...data, accountOwner: { ...o, ...patch } });
  const valid = o.username && o.email && o.firstName && o.lastName && o.employeeId && o.password;
  return (
    <section className="step">
      <h3>Account owner</h3>
      <div className="form-row">
        <label className="grow">Username<input value={o.username} onChange={e => set({ username: e.target.value })} placeholder="account.owner.01" /></label>
        <label className="grow">Email<input type="email" value={o.email} onChange={e => set({ email: e.target.value })} /></label>
      </div>
      <div className="form-row">
        <label className="grow">First name<input value={o.firstName} onChange={e => set({ firstName: e.target.value })} /></label>
        <label className="grow">Last name<input value={o.lastName} onChange={e => set({ lastName: e.target.value })} /></label>
      </div>
      <div className="form-row">
        <label className="grow">Employee ID<input value={o.employeeId} onChange={e => set({ employeeId: e.target.value })} /></label>
        <label className="grow">Department<input value={o.department ?? ''} onChange={e => set({ department: e.target.value })} /></label>
      </div>
      <div className="form-row">
        <label className="grow">Initial password (plaintext, will be bcrypted)
          <input type="password" value={o.password} onChange={e => set({ password: e.target.value })} />
        </label>
      </div>
      <div className="form-row">
        <label>Challenge question id<input type="number" value={o.challengeQuestionId ?? 1} onChange={e => set({ challengeQuestionId: Number(e.target.value) })} /></label>
        <label className="grow">Challenge answer<input value={o.challengeAnswer ?? ''} onChange={e => set({ challengeAnswer: e.target.value })} /></label>
      </div>
      <div className="form-actions">
        <button onClick={onBack}>Back</button>
        <button className="primary" disabled={!valid || busy} onClick={onNext}>
          {busy ? 'Loading preview…' : 'Preview SQL'}
        </button>
      </div>
    </section>
  );
}

interface ReviewProps {
  preview: PreviewResult;
  target: string;
  environment: string;
  confirmText: string;
  setConfirmText: (s: string) => void;
  onBack: () => void;
  onExecute: () => void;
  busy: boolean;
}

function StepReview({ preview, target, environment, confirmText, setConfirmText, onBack, onExecute, busy }: ReviewProps) {
  const isProd = environment === 'PROD';
  return (
    <section className="step">
      <h3>Review &amp; execute</h3>
      <p className="muted">{preview.summary}</p>

      {preview.warnings.length > 0 && (
        <div className="warnings">
          <strong>Warnings:</strong>
          <ul>{preview.warnings.map((w, i) => <li key={i}>{w}</li>)}</ul>
        </div>
      )}

      <pre className="sql-preview">{preview.sql}</pre>

      {isProd && (
        <div className="prod-confirm">
          <p>You are about to write to a <strong>PROD</strong> target. Type the connection name <code>{target}</code> to confirm:</p>
          <input value={confirmText} onChange={e => setConfirmText(e.target.value)} placeholder={target} />
        </div>
      )}

      <div className="form-actions">
        <button onClick={onBack}>Back</button>
        <button className={isProd ? 'danger' : 'primary'} onClick={onExecute} disabled={busy}>
          {busy ? 'Executing…' : 'Execute'}
        </button>
      </div>
    </section>
  );
}
