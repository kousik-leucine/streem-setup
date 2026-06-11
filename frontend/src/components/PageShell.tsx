import type { ReactNode } from 'react';

export function Page({ title, target, children }: { title: string; target?: string; children: ReactNode }) {
  return (
    <div className="page">
      <div className="page-header">
        <h2>{title}</h2>
        {target && <span className="muted small">target: <strong>{target}</strong></span>}
      </div>
      {children}
    </div>
  );
}

export function NoTargetMessage({ title }: { title: string }) {
  return (
    <Page title={title}>
      <div className="empty">
        <p>Select a target connection from the top bar to continue.</p>
      </div>
    </Page>
  );
}
