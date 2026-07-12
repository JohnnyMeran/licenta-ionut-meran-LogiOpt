import { useMemo, useState } from 'react';
import {
  Boxes,
  Building2,
  Gauge,
  PauseCircle,
  PiggyBank,
  PlayCircle,
  Route,
  Sparkles,
  Square,
  TrendingUp,
  Truck,
  Wallet,
} from 'lucide-react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import StatCard from '../components/StatCard';
import { chartTheme } from '../config/theme';
import VehicleSelector from '../components/VehicleSelector';
import RoutesMap from '../map/RoutesMap';

const ron = (value) => `${Number(value || 0).toFixed(0)} RON`;

export default function Dashboard({
  scenario,
  selectedRoute,
  setSelectedRoute,
  stats,
  chartData,
  routeMode,
  setRouteMode,
  liveOptimization,
  onLiveAction,
  onOptimize,
  theme,
}) {
  const [cityFilter, setCityFilter] = useState('ALL');
  const chart = chartTheme(theme);

  const routeSets = {
    initial: scenario?.initialRoutes || scenario?.routes || [],
    solved: scenario?.routes || [],
    hypothetical: scenario?.hypotheticalRoutes || [],
  };

  const routes = routeSets[routeMode] || routeSets.solved;
  const solveHypothetical = routeMode === 'hypothetical';
  // Tabul Ipotetic gol înseamnă că scenariul nu a fost încă rulat — nu că nu are soluție.
  const hypotheticalMissing = solveHypothetical && routes.length === 0;
  const modeLabel =
    routeMode === 'initial'
      ? 'plan inițial neoptimizat'
      : routeMode === 'hypothetical'
        ? 'limită de cost cu flotă nelimitată'
        : 'soluție reală OptaPlanner';

  // Filtrare ierarhică: mai întâi orașul (hub), apoi duba. „Curse interurbane" = linehaul.
  const cityOptions = useMemo(
    () =>
      (scenario?.depots || []).filter((hub) =>
        routes.some((r) => r.kind === 'REGIONAL' && r.depot?.id === hub.id)
      ),
    [scenario, routes]
  );
  const hasLineHaul = routes.some((r) => r.kind === 'LINEHAUL');
  const filteredRoutes =
    cityFilter === 'ALL'
      ? routes
      : cityFilter === 'LINEHAUL'
        ? routes.filter((r) => r.kind === 'LINEHAUL')
        : routes.filter((r) => r.kind === 'REGIONAL' && r.depot?.id === cityFilter);

  const changeCity = (value) => {
    setCityFilter(value);
    setSelectedRoute('ALL');
  };
  // Schimbarea scenariului (inițial / real / ipotetic) păstrează orașul selectat: te uiți la același oraș, în alt
  // plan. Doar vehiculul ales se deselectează, pentru că în celălalt plan are alt traseu (sau nici nu există).
  const changeMode = (value) => {
    setRouteMode(value);
    setSelectedRoute('ALL');
  };

  const selected = filteredRoutes.find((route) => route.vehicleCode === selectedRoute);
  const totalCost = routes.reduce((sum, route) => sum + route.costRon, 0);
  // Se numără rutele VIZIBILE: cu un filtru pe oraș, harta nu are nicio linie întreruptă, iar textul de sub ea
  // pretindea totuși că „14 rute de linehaul" transferă coletele.
  const lineHaulCount = filteredRoutes.filter((r) => r.kind === 'LINEHAUL').length;
  const monthly = scenario?.monthlyReports || [];
  const monthlySaved = monthly.reduce((sum, m) => sum + (m.savedByLogiOptRon || 0), 0);
  const monthlyProfit = monthly.reduce((sum, m) => sum + (m.profitRon || 0), 0);

  return (
    <>
      <div className="grid stats-grid stats-grid-five">
        <StatCard
          icon={Boxes}
          title="Colete"
          value={scenario?.shipments?.length || 0}
          hint="de ridicat și livrat"
        />

        <StatCard
          icon={Truck}
          title="Flotă"
          value={scenario?.vehicles?.length || 0}
          hint="dube + camioane linehaul"
        />

        <StatCard
          icon={Route}
          title="Distanță economisită"
          value={`${stats.distanceSavedKm || 0} km`}
          hint={`${stats.distanceImprovementPercent || 0}% real`}
        />

        <StatCard
          icon={PiggyBank}
          title="Economisit cu LogiOpt"
          value={ron(monthlySaved)}
          hint={monthly.length ? `ultimele ${monthly.length} luni` : 'fără rapoarte lunare'}
        />

        <StatCard
          icon={TrendingUp}
          title="Profit estimat"
          value={ron(monthlyProfit)}
          hint={monthly.length ? `ultimele ${monthly.length} luni` : 'fără rapoarte lunare'}
        />
      </div>

      <div className="layout">
        <div className="card map-card">
          <div className="card-title">
            <h2>Hartă rețea națională</h2>
            <span>{modeLabel}</span>
          </div>

          <div className="mode-tabs">
            <button
              className={routeMode === 'initial' ? 'selected' : ''}
              onClick={() => changeMode('initial')}
            >
              Inițial
            </button>

            <button
              className={routeMode === 'solved' ? 'selected' : ''}
              onClick={() => changeMode('solved')}
            >
              Real
            </button>

            <button
              className={routeMode === 'hypothetical' ? 'selected' : ''}
              onClick={() => changeMode('hypothetical')}
            >
              Ipotetic
            </button>
          </div>

          <div className="city-filter">
            <span className="city-filter-label">
              <Building2 size={15} />
              Oraș
            </span>
            <button
              className={cityFilter === 'ALL' ? 'selected' : ''}
              onClick={() => changeCity('ALL')}
            >
              Toată țara
            </button>
            {cityOptions.map((hub) => (
              <button
                key={hub.id}
                className={cityFilter === hub.id ? 'selected' : ''}
                onClick={() => changeCity(hub.id)}
              >
                {hub.city}
              </button>
            ))}
            {hasLineHaul && (
              <button
                className={cityFilter === 'LINEHAUL' ? 'selected' : ''}
                onClick={() => changeCity('LINEHAUL')}
              >
                Curse interurbane
              </button>
            )}
          </div>

          <div className="live-solver-panel">
            <div>
              <span className="eyebrow">OptaPlanner</span>
              <strong>
                {liveOptimization?.status || 'IDLE'} · iterația{' '}
                {liveOptimization?.iteration || 0}
              </strong>
              <small>
                {liveOptimization?.bestDistanceKm || 0} km ·{' '}
                {liveOptimization?.bestDurationMinutes || 0} min ·{' '}
                {liveOptimization?.bestCostRon || 0} RON
              </small>
            </div>

            <div className="live-solver-actions">
              {/* Se optimizează scenariul deschis pe hartă. Pe tabul „Inițial" (care nu e o soluție, ci punctul de
                  comparație) butonul rezolvă planul real. */}
              <button className="primary" onClick={() => onOptimize(solveHypothetical)}>
                <Sparkles size={16} />
                Optimizează {solveHypothetical ? 'ipoteticul' : 'realul'}
              </button>
              <button onClick={() => onLiveAction('start', solveHypothetical)}>
                <PlayCircle size={16} />
                Live
              </button>
              <button onClick={() => onLiveAction('pause')}>
                <PauseCircle size={16} />
                Pauză
              </button>
              <button onClick={() => onLiveAction('stop')}>
                <Square size={16} />
                Stop
              </button>
            </div>
          </div>

          {hypotheticalMissing && (
            <div className="scenario-hint">
              <Gauge size={18} />
              <span>
                Scenariul ipotetic nu a fost rulat încă. Apasă <b>Optimizează ipoteticul</b> — pornește din soluția
                reală și caută un plan mai ieftin cu o flotă nelimitată, deci nu poate ieși mai slab decât realul.
              </span>
            </div>
          )}

          <RoutesMap
            scenario={scenario}
            routes={filteredRoutes}
            selectedRoute={selectedRoute}
            onSelectRoute={setSelectedRoute}
          />

          <VehicleSelector
            routes={filteredRoutes}
            selectedRoute={selectedRoute}
            onSelect={setSelectedRoute}
          />

          {selectedRoute === 'ALL' && (
            <div className="route-details">
              <p>
                {routeMode === 'initial'
                  ? 'Plan inițial fără optimizare, folosit ca punct de comparație.'
                  : routeMode === 'hypothetical'
                    ? 'Flotă nelimitată, dar cu aceleași costuri: o dubă în plus tot înseamnă un salariu și o amortizare. Pornește din soluția reală, deci nu poate ieși mai slabă — arată cât s-ar mai putea câștiga dacă flota deținută nu ar fi o constrângere.'
                    : 'Varianta reală folosește dubele și camioanele existente în sistem, minimizând costul zilnic în RON.'}{' '}
                {lineHaulCount} rute de linehaul (linie întreruptă) transferă
                coletele consolidat între hub-uri.
              </p>
            </div>
          )}

          {selected && (
            <div className="route-details">
              <h3>
                Traseul {selected.vehicleCode}
                <span className="route-timing">
                  {selected.durationMinutes} min total = {selected.drivingMinutes} min condus +{' '}
                  {selected.serviceMinutes} min operare + {selected.breakMinutes} min pauză ·{' '}
                  {ron(selected.costRon)}
                </span>
              </h3>

              <ol>
                {(selected.stops || []).map((stop, index) =>
                  stop.stopType === 'HUB' ? (
                    <li key={`hub-${index}`} className="reload-stop">
                      Hub {stop.city} • {stop.address}
                    </li>
                  ) : (
                    <li key={`${stop.stopType}-${index}`} className={stop.stopType === 'PICKUP' ? 'pickup-stop' : ''}>
                      #{stop.sequence} {stop.stopType === 'PICKUP' ? '⤴ Ridicare' : '⤵ Livrare'} ·{' '}
                      {stop.name} - {stop.address} • {stop.city} • {stop.timeWindow}{' '}
                      <b>{stop.priority}</b>
                    </li>
                  )
                )}
              </ol>
            </div>
          )}
        </div>

        <div className="card chart-card">
          <div className="card-title">
            <h2>Inițial vs real vs ipotetic</h2>
            <span>Impactul optimizării</span>
          </div>

          <ResponsiveContainer width="100%" height={330}>
            <BarChart
              data={chartData}
              margin={{
                top: 10,
                right: 20,
                left: 0,
                bottom: 8,
              }}
              barGap={8}
              barCategoryGap="28%"
            >
              <CartesianGrid
                strokeDasharray="3 3"
                stroke={chart.grid}
                vertical={false}
              />

              <XAxis
                dataKey="name"
                stroke={chart.axis}
                tickLine={false}
                axisLine={{ stroke: chart.axisLine }}
              />

              <YAxis
                stroke={chart.axis}
                tickLine={false}
                axisLine={{ stroke: chart.axisLine }}
              />

              <Tooltip
                contentStyle={{
                  background: chart.tooltipBg,
                  border: chart.tooltipBorder,
                  borderRadius: 12,
                  color: chart.tooltipText,
                }}
              />

              <Legend />

              <Bar
                dataKey="km"
                name="Distanță km"
                fill="#38bdf8"
                radius={[8, 8, 0, 0]}
                maxBarSize={45}
              />

              <Bar
                dataKey="cost"
                name="Cost RON"
                fill="#22c55e"
                radius={[8, 8, 0, 0]}
                maxBarSize={45}
              />

              <Bar
                dataKey="fuel"
                name="Combustibil L"
                fill="#f97316"
                radius={[8, 8, 0, 0]}
                maxBarSize={45}
              />

              <Bar
                dataKey="timp"
                name="Timp mediu livrare min"
                fill="#a78bfa"
                radius={[8, 8, 0, 0]}
                maxBarSize={45}
              />
            </BarChart>
          </ResponsiveContainer>

          <div className="mini-stats">
            <span>
              <Gauge size={16} />
              {routes.length
                ? Math.round(
                    routes.reduce((sum, route) => sum + route.durationMinutes, 0) /
                      routes.length
                  )
                : 0}{' '}
              min mediu/rută
            </span>

            <span>
              <Wallet size={16} />
              {totalCost.toFixed(2)} RON curent
            </span>
          </div>
        </div>
      </div>
    </>
  );
}
