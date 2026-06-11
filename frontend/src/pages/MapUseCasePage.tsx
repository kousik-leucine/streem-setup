import { useState } from 'react';
import { useConnections } from '../state/ConnectionContext';
import { opsApi, type ExecuteResult, type MapUseCasePayload } from '../api/operations';
import { OrgSelect, FacilitySelect, UseCaseSelect } from '../components/Selectors';
import PreviewExecuteBar from '../components/PreviewExecuteBar';
import OpResultCard from '../components/OpResultCard';
import { Page, NoTargetMessage } from '../components/PageShell';

export default function MapUseCasePage() {
  const { selected } = useConnections();
  const [orgId, setOrgId] = useState<number | null>(null);
  const [facilityId, setFacilityId] = useState<number | null>(null);
  const [useCaseId, setUseCaseId] = useState<number | null>(null);
  const [includeProperties, setIncludeProperties] = useState(true);
  const [result, setResult] = useState<ExecuteResult | null>(null);

  if (!selected) return <NoTargetMessage title="Map use case" />;

  if (result) {
    return (
      <Page title="Map use case">
        <OpResultCard result={result} onReset={() => {
          setResult(null); setUseCaseId(null);
        }} />
      </Page>
    );
  }

  const valid = !!facilityId && !!useCaseId;
  const payload: MapUseCasePayload | null = valid
    ? { facilityId: facilityId!, useCaseId: useCaseId!, includeProperties }
    : null;

  return (
    <Page title="Map use case to facility" target={selected.name}>
      <section className="step">
        <h3>Pick what to map</h3>
        <OrgSelect connectionId={selected.id} value={orgId} onChange={v => { setOrgId(v); setFacilityId(null); setUseCaseId(null); }} />
        {orgId && <FacilitySelect connectionId={selected.id} orgId={orgId} value={facilityId} onChange={v => { setFacilityId(v); setUseCaseId(null); }} />}
        {facilityId && <UseCaseSelect connectionId={selected.id} facilityId={facilityId} mode="unmapped" value={useCaseId} onChange={setUseCaseId} />}

        <label className="checkbox">
          <input type="checkbox" checked={includeProperties} onChange={e => setIncludeProperties(e.target.checked)} />
          <span>Also map all properties of this use case to the facility (recommended)</span>
        </label>

        <PreviewExecuteBar
          target={selected}
          canSubmit={payload !== null}
          preview={() => opsApi.previewMapUseCase(selected.id, payload!)}
          execute={() => opsApi.executeMapUseCase(selected.id, payload!)}
          onSuccess={setResult}
        />
      </section>
    </Page>
  );
}
