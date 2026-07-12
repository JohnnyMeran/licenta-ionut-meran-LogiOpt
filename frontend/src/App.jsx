import { useEffect, useMemo, useState } from 'react';
import {
  BarChart3,
  Boxes,
  LayoutDashboard,
  Moon,
  RefreshCw,
  Settings,
  Sun,
  Truck,
  Users,
} from 'lucide-react';

import { API } from './config/api';
import { applyTheme, initialTheme } from './config/theme';
import Dashboard from './pages/Dashboard';
import DriversPage from './pages/DriversPage';
import ShipmentsPage from './pages/ShipmentsPage';
import ReportsPage from './pages/ReportsPage';
import SettingsPage from './pages/SettingsPage';
import VehiclesPage from './pages/VehiclesPage';

const PAGES = [
  ['dashboard', LayoutDashboard, 'Panou'],
  ['shipments', Boxes, 'Colete'],
  ['drivers', Users, 'Șoferi'],
  ['vehicles', Truck, 'Flotă'],
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
  // Tema implicită rămâne cea închisă; cea deschisă există pentru tipar (capturile din lucrare).
  const [theme, setTheme] = useState(initialTheme);
  const [optimizationHistory, setOptimizationHistory] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem('logioptOptimizationHistory') || '[]');
    } catch {
      return [];
    }
  });
  const [liveOptimization, setLiveOptimization] = useState({
    status: 'IDLE',
    mode: 'real',
    iteration: 0,
    bestDistanceKm: 0,
    bestCostRon: 0,
    bestDurationMinutes: 0,
  });

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
      setRouteMode('initial');
    } catch {
      setError(
        'Nu pot încărca datele demo. Verifică dacă backend-ul rulează pe portul 8090.'
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
      recordOptimization(data, hypothetical ? 'Ipotetic' : 'Real');
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
      // Endpoint-urile CRUD întorc scenariul NEoptimizat (backend-ul nu rulează solverul la fiecare modificare).
      // Fără resetarea modului, planul inițial ar rămâne etichetat „soluție reală OptaPlanner" după ce ștergi
      // un colet sau salvezi setările — adică ai citi baseline-ul crezând că e rezultatul optimizării.
      setRouteMode('initial');
    } catch {
      setError('Operația CRUD a eșuat. Verifică backend-ul.');
    } finally {
      setLoading(false);
    }
  };

  const recordOptimization = (data, mode) => {
    if (!data?.statistics) return;

    const realRoutes = data.routes || [];
    const hypotheticalRoutes = data.hypotheticalRoutes || [];
    const usedRealRoutes = realRoutes.filter((route) =>
      route.stops?.some((stop) => stop.stopType === 'DELIVERY')
    ).length;
    const usedHypotheticalRoutes = hypotheticalRoutes.filter((route) =>
      route.stops?.some((stop) => stop.stopType === 'DELIVERY')
    ).length;

    setOptimizationHistory((previous) => {
      const next = [
        {
          id: `${Date.now()}-${mode}`,
          date: new Date().toISOString(),
          mode,
          orderCount: data.shipments?.length || 0,
          usedRealRoutes,
          usedHypotheticalRoutes,
          statistics: data.statistics,
        },
        ...previous,
      ].slice(0, 50);

      localStorage.setItem('logioptOptimizationHistory', JSON.stringify(next));
      return next;
    });
  };

  const applyLiveStatus = (data) => {
    setLiveOptimization({
      status: data.status,
      mode: data.mode,
      iteration: data.iteration,
      bestDistanceKm: data.bestDistanceKm,
      bestCostRon: data.bestCostRon,
      bestDurationMinutes: data.bestDurationMinutes,
    });

    if (data.scenario) {
      setScenario(data.scenario);
      setRouteMode(data.mode === 'hypothetical' ? 'hypothetical' : 'solved');
      // Fără setActivePage: statusul live se citește la fiecare 1,5s, iar navigarea forțată pe Panou la fiecare
      // sondaj făcea Coletele/Flota/Rapoartele imposibil de folosit cât timp rulează solverul.
      if (data.status === 'STOPPED') {
        recordOptimization(
          data.scenario,
          data.mode === 'hypothetical' ? 'Live ipotetic' : 'Live real'
        );
      }
    }
  };

  const liveAction = async (action, hypothetical = false) => {
    setError('');

    try {
      const suffix =
        action === 'start'
          ? `/optimize/live/start?hypothetical=${hypothetical}`
          : `/optimize/live/${action}`;
      const res = await fetch(`${API}${suffix}`, {
        method: 'POST',
      });

      if (!res.ok) {
        throw new Error();
      }

      if (action === 'start') setActivePage('dashboard'); // o singură dată, la pornire — nu la fiecare sondaj
      applyLiveStatus(await res.json());
    } catch {
      setError('Optimizarea live nu a pornit. Verifică backend-ul.');
    }
  };

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  useEffect(() => {
    loadScenario();
  }, []);

  useEffect(() => {
    if (liveOptimization.status !== 'RUNNING') return undefined;

    const timer = window.setInterval(async () => {
      try {
        const res = await fetch(`${API}/optimize/live/status`);
        if (res.ok) applyLiveStatus(await res.json());
      } catch {
        setError('Nu pot citi statusul optimizării live.');
      }
    }, 1500);

    return () => window.clearInterval(timer);
  }, [liveOptimization.status]);

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
            <span>rețea națională de curierat</span>
          </div>
        </div>

        <button onClick={() => loadScenario(true)}>
          <RefreshCw size={18} />
          Resetează datele demo
        </button>

        <button onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}>
          {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
          {theme === 'dark' ? 'Temă deschisă (tipar)' : 'Temă închisă'}
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
            <h1>Optimizarea rețelei de curierat</h1>
            <p>
              Ridicare colete → hub de origine → linehaul între orașe → hub
              destinație → livrare pe ultima milă, pe toată România, optimizat cu
              OptaPlanner și OSRM.
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
            liveOptimization={liveOptimization}
            onLiveAction={liveAction}
            onOptimize={optimize}
            theme={theme}
          />
        )}

        {activePage === 'shipments' && (
          <ShipmentsPage scenario={scenario} callApi={callApi} />
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

        {activePage === 'reports' && (
          <ReportsPage
            scenario={scenario}
            stats={stats}
            history={optimizationHistory}
            theme={theme}
          />
        )}

        {activePage === 'settings' && (
          <SettingsPage scenario={scenario} callApi={callApi} />
        )}
      </section>
    </main>
  );
}
