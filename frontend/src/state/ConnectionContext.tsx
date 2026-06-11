import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { connectionsApi } from '../api/connections';
import type { Connection } from '../types';

interface Ctx {
  connections: Connection[];
  selected: Connection | null;
  select: (id: string | null) => void;
  refresh: () => Promise<void>;
}

const ConnectionContext = createContext<Ctx | null>(null);

export function ConnectionProvider({ children }: { children: ReactNode }) {
  const [connections, setConnections] = useState<Connection[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(
    () => localStorage.getItem('selectedConnectionId'),
  );

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
