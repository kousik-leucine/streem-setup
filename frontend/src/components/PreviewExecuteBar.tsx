import { useState } from 'react';
import type { PreviewResult, ExecuteResult } from '../api/operations';
import type { Connection } from '../types';

interface Props {
  target: Connection;
  canSubmit: boolean;
  preview: () => Promise<PreviewResult>;
  execute: () => Promise<ExecuteResult>;
  onSuccess: (result: ExecuteResult) => void;
}

/**
 * Two-stage submit: load preview → show SQL → user confirms → execute.
 * Handles PROD typed-confirmation, error display, and the "back to editing" loop.
 */
export default function PreviewExecuteBar({ target, canSubmit, preview, execute, onSuccess }: Props) {
  const [previewResult, setPreviewResult] = useState<PreviewResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirmText, setConfirmText] = useState('');

  const isProd = target.environment === 'PROD';

  async function load() {
    setError(null); setBusy(true);
    try {
      setPreviewResult(await preview());
    } catch (e: unknown) {
      setError(extractError(e));
    } finally {
      setBusy(false);
    }
  }

  async function run() {
    if (isProd && confirmText !== target.name) {
      setError(`Type "${target.name}" to confirm PROD execution`);
      return;
    }
    setError(null); setBusy(true);
    try {
      const result = await execute();
      onSuccess(result);
    } catch (e: unknown) {
      setError(extractError(e));
    } finally {
      setBusy(false);
    }
  }

  if (!previewResult) {
    return (
      <div className="form-actions">
        {error && <div className="error">{error}</div>}
        <button className="primary" disabled={!canSubmit || busy} onClick={load}>
          {busy ? 'Loading…' : 'Preview SQL'}
        </button>
      </div>
    );
  }

  return (
    <div className="preview-block">
      <p className="muted">{previewResult.summary}</p>

      {previewResult.warnings.length > 0 && (
        <div className="warnings">
          <strong>Warnings:</strong>
          <ul>{previewResult.warnings.map((w, i) => <li key={i}>{w}</li>)}</ul>
        </div>
      )}

      <pre className="sql-preview">{previewResult.sql}</pre>

      {isProd && (
        <div className="prod-confirm">
          <p>Writing to <strong>PROD</strong>. Type <code>{target.name}</code> to confirm:</p>
          <input value={confirmText} onChange={e => setConfirmText(e.target.value)} placeholder={target.name} />
        </div>
      )}

      {error && <div className="error">{error}</div>}

      <div className="form-actions">
        <button onClick={() => { setPreviewResult(null); setConfirmText(''); }}>Back to edit</button>
        <button className={isProd ? 'danger' : 'primary'} disabled={busy} onClick={run}>
          {busy ? 'Executing…' : 'Execute'}
        </button>
      </div>
    </div>
  );
}

function extractError(e: unknown): string {
  type AxiosLike = { response?: { data?: { error?: string } }; message?: string };
  const ax = e as AxiosLike;
  return ax?.response?.data?.error ?? ax?.message ?? String(e);
}
