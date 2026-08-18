import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import ConnectionsPage from './pages/ConnectionsPage';
import NewOrgPage from './pages/NewOrgPage';
import AddFacilityPage from './pages/AddFacilityPage';
import AddUseCasePage from './pages/AddUseCasePage';
import AddPropertyPage from './pages/AddPropertyPage';
import MapUseCasePage from './pages/MapUseCasePage';
import MapPropertyPage from './pages/MapPropertyPage';
import AddLicensePage from './pages/AddLicensePage';
import FeatureFlagsPage from './pages/FeatureFlagsPage';
import { ConnectionProvider } from './state/ConnectionContext';
import ConnectionSelector from './components/ConnectionSelector';

export default function App() {
  return (
    <ConnectionProvider>
      <div className="app">
        <aside className="sidebar">
          <h1 className="logo">streem-setup</h1>
          <nav>
            <div className="nav-section">Targets</div>
            <NavLink to="/connections">Connections</NavLink>

            <div className="nav-section">Bootstrap</div>
            <NavLink to="/new-org">New Organisation</NavLink>

            <div className="nav-section">Existing org</div>
            <NavLink to="/add-facility">Add facility</NavLink>
            <NavLink to="/add-usecase">Add use case</NavLink>
            <NavLink to="/add-property">Add property</NavLink>
            <NavLink to="/map-usecase">Map use case</NavLink>
            <NavLink to="/map-property">Map property</NavLink>
            <NavLink to="/add-license">Add licenses</NavLink>

            <div className="nav-section">Settings</div>
            <NavLink to="/feature-flags">Feature flags</NavLink>

            <div className="nav-section">Records</div>
            <NavLink to="/history" className="disabled" aria-disabled>
              History <span className="badge">soon</span>
            </NavLink>
          </nav>
        </aside>

        <div className="main-wrap">
          <header className="topbar">
            <ConnectionSelector />
          </header>

          <main className="main">
            <Routes>
              <Route path="/" element={<Navigate to="/connections" replace />} />
              <Route path="/connections" element={<ConnectionsPage />} />
              <Route path="/new-org" element={<NewOrgPage />} />
              <Route path="/add-facility" element={<AddFacilityPage />} />
              <Route path="/add-usecase" element={<AddUseCasePage />} />
              <Route path="/add-property" element={<AddPropertyPage />} />
              <Route path="/map-usecase" element={<MapUseCasePage />} />
              <Route path="/map-property" element={<MapPropertyPage />} />
              <Route path="/add-license" element={<AddLicensePage />} />
              <Route path="/feature-flags" element={<FeatureFlagsPage />} />
              <Route path="*" element={<Navigate to="/connections" replace />} />
            </Routes>
          </main>
        </div>
      </div>
    </ConnectionProvider>
  );
}
