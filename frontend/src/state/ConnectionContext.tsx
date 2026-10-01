import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { connectionsApi } from '../api/connections';
import type { Connection } from '../types';

interface Ctx {
  connections: Connection[];
  selected: Connection | null;
  select: (id: string | null) => void;
  refresh: () => Promise<void>;
}

const ConnectionContext = createContext<Ctx | null>(null);

/** Re-warm a target at most this often; the backend keeps a tunnel alive far longer. */
const REWARM_INTERVAL_MS = 4 * 60 * 1000;

export function ConnectionProvider({ children }: { children: ReactNode }) {
  const [connections, setConnections] = useState<Connection[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(
    () => localStorage.getItem('selectedConnectionId'),
  );
  const lastWarmedAt = useRef<Record<string, number>>({});

  async function refresh() {
    const list = await connectionsApi.list();
    setConnections(list);
    if (selectedId && !list.find(c => c.id === selectedId)) {
      setSelectedId(null);
      localStorage.removeItem('selectedConnectionId');
    }
  }

  useEffect(() => {
    refresh();
  }, []);

  /**
   * Open the SSH tunnel and JDBC pool for the selected target in the background, so the
   * first dropdown load on a page doesn't pay for the handshake. Runs on selection and on
   * a reload that restores a selection, and is throttled so navigating between pages
   * doesn't spam the endpoint.
   */
  useEffect(() => {
    if (!selectedId) return;
    const last = lastWarmedAt.current[selectedId] ?? 0;
    if (Date.now() - last < REWARM_INTERVAL_MS) return;
    lastWarmedAt.current[selectedId] = Date.now();
    connectionsApi.warm(selectedId);
  }, [selectedId]);

  function select(id: string | null) {
    setSelectedId(id);
    if (id) localStorage.setItem('selectedConnectionId', id);
    else localStorage.removeItem('selectedConnectionId');
  }

  const selected = selectedId ? connections.find(c => c.id === selectedId) ?? null : null;

  return (
    <ConnectionContext.Provider value={{ connections, selected, select, refresh }}>
      {children}
    </ConnectionContext.Provider>
  );
}

export function useConnections() {
  const ctx = useContext(ConnectionContext);
  if (!ctx) throw new Error('useConnections must be inside ConnectionProvider');
  return ctx;
}
