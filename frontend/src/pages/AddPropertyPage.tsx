import { useState } from 'react';
import { useConnections } from '../state/ConnectionContext';
import { opsApi, type ExecuteResult, type AddPropertyPayload } from '../api/operations';
import { OrgSelect, FacilitySelect, UseCaseSelect } from '../components/Selectors';
import PreviewExecuteBar from '../components/PreviewExecuteBar';
import OpResultCard from '../components/OpResultCard';
import { Page, NoTargetMessage } from '../components/PageShell';

export default function AddPropertyPage() {
  const { selected } = useConnections();
  const [orgId, setOrgId] = useState<number | null>(null);
  const [facilityId, setFacilityId] = useState<number | null>(null);
  const [useCaseId, setUseCaseId] = useState<number | null>(null);
  const [name, setName] = useState('');
  const [label, setLabel] = useState('');
  const [placeholder, setPlaceholder] = useState('');
  const [mandatory, setMandatory] = useState(false);
  const [result, setResult] = useState<ExecuteResult | null>(null);

  if (!selected) return <NoTargetMessage title="Add property" />;

  if (result) {
    return (
      <Page title="Add property">
        <OpResultCard result={result} onReset={() => {
          setResult(null); setName(''); setLabel(''); setPlaceholder(''); setMandatory(false);
        }} />
      </Page>
    );
  }

  const valid = !!facilityId && !!useCaseId && !!name && !!label;
  const payload: AddPropertyPayload | null = valid ? {
    facilityId: facilityId!, useCaseId: useCaseId!, name, label,
    placeholder: placeholder || undefined, mandatory,
  } : null;

  return (
    <Page title="Add property" target={selected.name}>
      <section className="step">
        <h3>Property details</h3>
        <OrgSelect connectionId={selected.id} value={orgId} onChange={v => { setOrgId(v); setFacilityId(null); setUseCaseId(null); }} />
        {orgId && <FacilitySelect connectionId={selected.id} orgId={orgId} value={facilityId} onChange={v => { setFacilityId(v); setUseCaseId(null); }} />}
        {facilityId && <UseCaseSelect connectionId={selected.id} facilityId={facilityId} mode="mapped" value={useCaseId} onChange={setUseCaseId} />}

        <div className="form-row">
          <label className="grow">Name<input value={name} onChange={e => setName(e.target.value)} placeholder="reference-sop-number" /></label>
          <label className="grow">Label<input value={label} onChange={e => setLabel(e.target.value)} placeholder="Reference SOP Number" /></label>
        </div>
        <label>Placeholder <span className="muted small">(optional, defaults to label)</span>
          <input value={placeholder} onChange={e => setPlaceholder(e.target.value)} />
        </label>
        <label className="checkbox">
          <input type="checkbox" checked={mandatory} onChange={e => setMandatory(e.target.checked)} />
          <span>Mandatory</span>
        </label>

        <PreviewExecuteBar
          target={selected}
          canSubmit={payload !== null}
          preview={() => opsApi.previewAddProperty(selected.id, payload!)}
          execute={() => opsApi.executeAddProperty(selected.id, payload!)}
          onSuccess={setResult}
        />
      </section>
    </Page>
  );
}
