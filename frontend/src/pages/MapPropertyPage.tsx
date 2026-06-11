import { useState } from 'react';
import { useConnections } from '../state/ConnectionContext';
import { opsApi, type ExecuteResult, type MapPropertyPayload } from '../api/operations';
import { OrgSelect, FacilitySelect, UseCaseSelect, PropertySelect } from '../components/Selectors';
import PreviewExecuteBar from '../components/PreviewExecuteBar';
import OpResultCard from '../components/OpResultCard';
import { Page, NoTargetMessage } from '../components/PageShell';

export default function MapPropertyPage() {
  const { selected } = useConnections();
  const [orgId, setOrgId] = useState<number | null>(null);
  const [facilityId, setFacilityId] = useState<number | null>(null);
  const [useCaseId, setUseCaseId] = useState<number | null>(null);
  const [propertyId, setPropertyId] = useState<number | null>(null);
  const [labelAlias, setLabelAlias] = useState('');
  const [placeholderAlias, setPlaceholderAlias] = useState('');
  const [mandatory, setMandatory] = useState(false);
  const [result, setResult] = useState<ExecuteResult | null>(null);

  if (!selected) return <NoTargetMessage title="Map property" />;

  if (result) {
    return (
      <Page title="Map property">
        <OpResultCard result={result} onReset={() => {
          setResult(null); setPropertyId(null); setLabelAlias(''); setPlaceholderAlias(''); setMandatory(false);
        }} />
      </Page>
    );
  }

  const valid = !!facilityId && !!useCaseId && !!propertyId;
  const payload: MapPropertyPayload | null = valid ? {
    facilityId: facilityId!, useCaseId: useCaseId!, propertyId: propertyId!,
    labelAlias: labelAlias || undefined,
    placeholderAlias: placeholderAlias || undefined,
    mandatory,
  } : null;

  return (
    <Page title="Map property to facility–usecase" target={selected.name}>
      <section className="step">
        <h3>Pick what to map</h3>
        <OrgSelect connectionId={selected.id} value={orgId} onChange={v => { setOrgId(v); setFacilityId(null); setUseCaseId(null); setPropertyId(null); }} />
        {orgId && <FacilitySelect connectionId={selected.id} orgId={orgId} value={facilityId} onChange={v => { setFacilityId(v); setUseCaseId(null); setPropertyId(null); }} />}
        {facilityId && <UseCaseSelect connectionId={selected.id} facilityId={facilityId} mode="mapped" value={useCaseId} onChange={v => { setUseCaseId(v); setPropertyId(null); }} />}
        {useCaseId && <PropertySelect connectionId={selected.id} useCaseId={useCaseId} value={propertyId} onChange={setPropertyId} />}

        <div className="form-row">
          <label className="grow">Label alias <span className="muted small">(optional)</span>
            <input value={labelAlias} onChange={e => setLabelAlias(e.target.value)} />
          </label>
          <label className="grow">Placeholder alias <span className="muted small">(optional)</span>
            <input value={placeholderAlias} onChange={e => setPlaceholderAlias(e.target.value)} />
          </label>
        </div>
        <label className="checkbox">
          <input type="checkbox" checked={mandatory} onChange={e => setMandatory(e.target.checked)} />
          <span>Mandatory</span>
        </label>

        <PreviewExecuteBar
          target={selected}
          canSubmit={payload !== null}
          preview={() => opsApi.previewMapProperty(selected.id, payload!)}
          execute={() => opsApi.executeMapProperty(selected.id, payload!)}
          onSuccess={setResult}
        />
      </section>
    </Page>
  );
}
