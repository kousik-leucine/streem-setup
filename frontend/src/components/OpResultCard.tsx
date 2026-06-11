import type { ExecuteResult } from '../api/operations';

export default function OpResultCard({ result, onReset }: { result: ExecuteResult; onReset: () => void }) {
  return (
    <div className="result-card">
      <p className="ok">✓ {result.summary}</p>
      <p className="muted small">Audit id: {result.auditId}</p>
      <h4>Generated IDs</h4>
      <pre>{JSON.stringify(result.generatedIds, null, 2)}</pre>
      <button onClick={onReset}>Run another</button>
    </div>
  );
}
