import { useState } from 'react';
import { useConnections } from '../state/ConnectionContext';
import { opsApi, type ExecuteResult, type AddFacilityPayload } from '../api/operations';
import { OrgSelect } from '../components/Selectors';
import PreviewExecuteBar from '../components/PreviewExecuteBar';
import OpResultCard from '../components/OpResultCard';
import { Page, NoTargetMessage } from '../components/PageShell';

const DEFAULTS = {
  timezone: 'UTC',
  dateFormat: 'MMM dd, yyyy',
  dateTimeFormat: 'MMM dd, yyyy HH:mm',
  timeFormat: 'HH:mm',
};

export default function AddFacilityPage() {
  const { selected } = useConnections();
  const [organisationId, setOrgId] = useState<number | null>(null);
  const [name, setName] = useState('');
  const [timezone, setTimezone] = useState(DEFAULTS.timezone);
  const [dateFormat, setDateFormat] = useState(DEFAULTS.dateFormat);
  const [dateTimeFormat, setDateTimeFormat] = useState(DEFAULTS.dateTimeFormat);
  const [timeFormat, setTimeFormat] = useState(DEFAULTS.timeFormat);
  const [result, setResult] = useState<ExecuteResult | null>(null);

  if (!selected) return <NoTargetMessage title="Add facility" />;

  if (result) {
    return (
      <Page title="Add facility">
        <OpResultCard result={result} onReset={() => {
          setResult(null); setName(''); setOrgId(null);
        }} />
      </Page>
    );
  }

  const payload: AddFacilityPayload | null = (organisationId && name && timezone) ? {
    organisationId, name, timezone, dateFormat, dateTimeFormat, timeFormat,
  } : null;

  return (
    <Page title="Add facility" target={selected.name}>
      <section className="step">
        <h3>Facility details</h3>

        <OrgSelect connectionId={selected.id} value={organisationId} onChange={setOrgId} />

        <label>Name<input value={name} onChange={e => setName(e.target.value)} /></label>
        <label>Timezone<input value={timezone} onChange={e => setTimezone(e.target.value)} placeholder="UTC | Asia/Kolkata" /></label>
        <div className="form-row">
          <label className="grow">Date format<input value={dateFormat} onChange={e => setDateFormat(e.target.value)} /></label>
          <label className="grow">Date+time format<input value={dateTimeFormat} onChange={e => setDateTimeFormat(e.target.value)} /></label>
          <label>Time format<input value={timeFormat} onChange={e => setTimeFormat(e.target.value)} /></label>
        </div>

        <PreviewExecuteBar
          target={selected}
          canSubmit={payload !== null}
          preview={() => opsApi.previewAddFacility(selected.id, payload!)}
          execute={() => opsApi.executeAddFacility(selected.id, payload!)}
          onSuccess={setResult}
        />
      </section>
    </Page>
  );
}

