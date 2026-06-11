import { useState } from 'react';
import { useConnections } from '../state/ConnectionContext';
import { opsApi, type ExecuteResult, type AddUseCasePayload } from '../api/operations';
import { OrgSelect, FacilitySelect } from '../components/Selectors';
import PreviewExecuteBar from '../components/PreviewExecuteBar';
import OpResultCard from '../components/OpResultCard';
import { Page, NoTargetMessage } from '../components/PageShell';

type Property = AddUseCasePayload['properties'][number];

export default function AddUseCasePage() {
  const { selected } = useConnections();
  const [orgId, setOrgId] = useState<number | null>(null);
  const [facilityId, setFacilityId] = useState<number | null>(null);
  const [name, setName] = useState('');
  const [label, setLabel] = useState('');
  const [cardColor, setCardColor] = useState('#EDF5FF');
  const [properties, setProperties] = useState<Property[]>([
    { name: '', label: '', mandatory: false },
  ]);
  const [result, setResult] = useState<ExecuteResult | null>(null);

  if (!selected) return <NoTargetMessage title="Add use case" />;

  if (result) {
    return (
      <Page title="Add use case">
        <OpResultCard result={result} onReset={() => {
          setResult(null); setName(''); setLabel('');
          setProperties([{ name: '', label: '', mandatory: false }]);
        }} />
      </Page>
    );
  }

  function patchProp(i: number, patch: Partial<Property>) {
    const next = [...properties];
    next[i] = { ...next[i], ...patch };
    setProperties(next);
  }

  const valid = !!facilityId && !!name && !!label
    && properties.length > 0
    && properties.every(p => p.name && p.label);

  const payload: AddUseCasePayload | null = valid
    ? { facilityId: facilityId!, name, label, cardColor, properties }
    : null;

  return (
    <Page title="Add use case" target={selected.name}>
      <section className="step">
        <h3>Use case details</h3>
        <OrgSelect connectionId={selected.id} value={orgId} onChange={v => { setOrgId(v); setFacilityId(null); }} />
        {orgId && (
          <FacilitySelect connectionId={selected.id} orgId={orgId} value={facilityId} onChange={setFacilityId} />
        )}
        <div className="form-row">
          <label className="grow">Use case name<input value={name} onChange={e => setName(e.target.value)} /></label>
          <label className="grow">Label<input value={label} onChange={e => setLabel(e.target.value)} /></label>
          <label>Card color<input value={cardColor} onChange={e => setCardColor(e.target.value)} /></label>
        </div>

        <h4>Properties</h4>
        {properties.map((p, i) => (
          <div key={i} className="form-row property-row">
            <label className="grow">Name<input value={p.name} onChange={e => patchProp(i, { name: e.target.value })} /></label>
            <label className="grow">Label<input value={p.label} onChange={e => patchProp(i, { label: e.target.value })} /></label>
            <label className="checkbox">
              <input type="checkbox" checked={p.mandatory} onChange={e => patchProp(i, { mandatory: e.target.checked })} />
              <span>Mandatory</span>
            </label>
            <button className="danger" onClick={() => setProperties(properties.filter((_, idx) => idx !== i))}>×</button>
          </div>
        ))}
        <button onClick={() => setProperties([...properties, { name: '', label: '', mandatory: false }])}>+ Add property</button>

        <PreviewExecuteBar
          target={selected}
          canSubmit={payload !== null}
          preview={() => opsApi.previewAddUseCase(selected.id, payload!)}
          execute={() => opsApi.executeAddUseCase(selected.id, payload!)}
          onSuccess={setResult}
        />
      </section>
    </Page>
  );
}
