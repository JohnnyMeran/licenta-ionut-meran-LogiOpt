import { useEffect, useMemo, useState } from 'react';
import {
  BarChart3,
  ClipboardList,
  Gauge,
  LayoutDashboard,
  PlayCircle,
  RefreshCw,
  Settings,
  Truck,
  Users,
} from 'lucide-react';

import { API } from './config/api';
import Dashboard from './pages/Dashboard';
import DriversPage from './pages/DriversPage';
import OrdersPage from './pages/OrdersPage';
import ReportsPage from './pages/ReportsPage';
import SettingsPage from './pages/SettingsPage';
import VehiclesPage from './pages/VehiclesPage';

const PAGES = [
  ['dashboard', LayoutDashboard, 'Dashboard'],
  ['orders', ClipboardList, 'Comenzi'],
  ['drivers', Users, 'Șoferi'],
  ['vehicles', Truck, 'Camioane'],
  ['reports', BarChart3, 'Rapoarte'],
  ['settings', Settings, 'Setări'],
];

export default function App() {
  const [scenario, setScenario] = useState(null);
  const [loading, setLoading] = useState(false);
  const [selectedRoute, setSelectedRoute] = useState('ALL');
  const [routeMode, setRouteMode] = useState('solved');
  const [activePage, setActivePage] = useState('dashboard');
  const [error, setError] = useState('');

  const loadScenario = async (reset = false) => {
    setLoading(true);
    setError('');

    try {
      const res = await fetch(
        `${API}${reset ? '/scenario/reset' : '/scenario/demo'}`,
        {
          method: reset ? 'POST' : 'GET',
        }
      );

      if (!res.ok) {
        throw new Error('Backend indisponibil');
      }

      const data = await res.json();

      setScenario(data);
      setSelectedRoute('ALL');
    } catch {
      setError(
        'Nu pot încărca datele demo. Verifică dacă backend-ul rulează pe portul 8080.'
      );
    } finally {
      setLoading(false);
    }
  };

  const optimize = async (hypothetical = false) => {
    setLoading(true);
    setError('');

    try {
      const res = await fetch(
        `${API}${hypothetical ? '/optimize/hypothetical' : '/optimize'}`,
        {
          method: 'POST',
        }
      );

      if (!res.ok) {
        throw new Error('Optimizarea a eșuat');
      }

      const data = await res.json();

      setScenario(data);
      setSelectedRoute('ALL');
      setRouteMode(hypothetical ? 'hypothetical' : 'solved');
      setActivePage('dashboard');
    } catch {
      setError(
        'Optimizarea nu a pornit. Verifică backend-ul Spring Boot și dependențele Maven.'
      );
    } finally {
      setLoading(false);
    }
  };

  const callApi = async (path, method, body) => {
    setLoading(true);
    setError('');

    try {
      const res = await fetch(`${API}${path}`, {
        method,
        headers: body
          ? {
              'Content-Type': 'application/json',
            }
          : undefined,
        body: body ? JSON.stringify(body) : undefined,
      });

      if (!res.ok) {
        throw new Error();
      }

      const data = await res.json();

      setScenario(data);
      setSelectedRoute('ALL');
    } catch {
      setError('Operația CRUD a eșuat. Verifică backend-ul.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadScenario();
  }, []);

  const stats = scenario?.statistics || {};

  const chartData = useMemo(
    () => [
      {
        name: 'Inițial',
        km: stats.initialDistanceKm || 0,
        cost: stats.initialCostRon || 0,
        fuel: stats.initialFuelLiters || 0,
        timp: Math.round(stats.initialAverageDeliveryMinutes || 0),
      },
      {
        name: 'Real',
        km: stats.optimizedDistanceKm || 0,
        cost: stats.optimizedCostRon || 0,
        fuel: stats.optimizedFuelLiters || 0,
        timp: Math.round(stats.optimizedAverageDeliveryMinutes || 0),
      },
      {
        name: 'Ipotetic',
        km: stats.hypotheticalDistanceKm || 0,
        cost: stats.hypotheticalCostRon || 0,
        fuel: stats.hypotheticalFuelLiters || 0,
        timp: Math.round(stats.hypotheticalAverageDeliveryMinutes || 0),
      },
    ],
    [stats]
  );

  return (
    <main>
      <aside>
        <div className="brand">
          <div className="logo">L</div>

          <div>
            <h1>LogiOpt</h1>
            <span>simulator rute logistice</span>
          </div>
        </div>

        <button onClick={() => loadScenario(true)}>
          <RefreshCw size={18} />
          Resetează datele demo
        </button>

        <button className="primary" onClick={() => optimize(false)}>
          <PlayCircle size={18} />
          Rezolvă
        </button>

        <button className="primary alt" onClick={() => optimize(true)}>
          <Gauge size={18} />
          Rezolvă varianta ipotetică
        </button>

        <nav className="menu-nav">
          {PAGES.map(([key, Icon, label]) => (
            <button
              key={key}
              className={activePage === key ? 'active nav-btn' : 'nav-btn'}
              onClick={() => setActivePage(key)}
            >
              <Icon size={18} />
              {label}
            </button>
          ))}
        </nav>
      </aside>

      <section className="content">
        <header>
          <div>
            <h1>Automatizarea proceselor logistice</h1>
            <p>
              Simulare de distribuție cu rute reale, timp de livrare, reguli
              legale, depozite și stocuri diferite.
            </p>
          </div>

          {loading && <span className="pill">Se procesează...</span>}
        </header>

        {error && <div className="error">{error}</div>}

        {activePage === 'dashboard' && (
          <Dashboard
            scenario={scenario}
            selectedRoute={selectedRoute}
            setSelectedRoute={setSelectedRoute}
            stats={stats}
            chartData={chartData}
            routeMode={routeMode}
            setRouteMode={setRouteMode}
          />
        )}

        {activePage === 'orders' && (
          <OrdersPage scenario={scenario} callApi={callApi} />
        )}

        {activePage === 'drivers' && (
          <DriversPage scenario={scenario} callApi={callApi} />
        )}

        {activePage === 'vehicles' && (
          <VehiclesPage
            scenario={scenario}
            callApi={callApi}
            setSelectedRoute={setSelectedRoute}
          />
        )}

        {activePage === 'reports' && <ReportsPage stats={stats} />}

        {activePage === 'settings' && (
          <SettingsPage scenario={scenario} callApi={callApi} />
        )}
      </section>
    </main>
  );
}
