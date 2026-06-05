import { Eye, Fuel, Gauge, Package, Route, Truck, Wallet } from 'lucide-react';
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
import { CULORI_RUTE } from '../config/constants';
import RoutesMap from '../map/RoutesMap';

export default function Dashboard({
  scenario,
  selectedRoute,
  setSelectedRoute,
  stats,
  chartData,
  routeMode,
  setRouteMode,
}) {
  const routes =
    routeMode === 'hypothetical'
      ? scenario?.hypotheticalRoutes || []
      : scenario?.routes || [];

  const selected = routes.find((route) => route.vehicleCode === selectedRoute);
  const totalCost = routes.reduce((sum, route) => sum + route.costRon, 0);

  return (
    <>
      <div className="grid stats-grid stats-grid-five">
        <StatCard
          icon={Package}
          title="Comenzi"
          value={scenario?.orders?.length || 0}
          hint="livrări de planificat"
        />

        <StatCard
          icon={Truck}
          title="Camioane"
          value={scenario?.vehicles?.length || 0}
          hint="flotă disponibilă"
        />

        <StatCard
          icon={Route}
          title="Distanță economisită"
          value={`${stats.distanceSavedKm || 0} km`}
          hint={`${stats.distanceImprovementPercent || 0}% real`}
        />

        <StatCard
          icon={Fuel}
          title="Combustibil economisit"
          value={`${stats.fuelSavedLiters || 0} L`}
          hint="motorină estimată"
        />

        <StatCard
          icon={Gauge}
          title="Varianta ipotetică"
          value={`${stats.hypotheticalDistanceKm || 0} km`}
          hint={`${stats.hypotheticalAverageDeliveryMinutes || 0} min mediu/livrare`}
        />
      </div>

      <div className="layout">
        <div className="card map-card">
          <div className="card-title">
            <h2>Hartă rute pe șosea</h2>
            <span>
              {routeMode === 'hypothetical'
                ? 'variantă ipotetică'
                : 'variantă reală'}
            </span>
          </div>

          <div className="mode-tabs">
            <button
              className={routeMode === 'solved' ? 'selected' : ''}
              onClick={() => {
                setRouteMode('solved');
                setSelectedRoute('ALL');
              }}
            >
              Real
            </button>

            <button
              className={routeMode === 'hypothetical' ? 'selected' : ''}
              onClick={() => {
                setRouteMode('hypothetical');
                setSelectedRoute('ALL');
              }}
            >
              Ipotetic
            </button>
          </div>

          <RoutesMap
            scenario={scenario}
            routes={routes}
            selectedRoute={selectedRoute}
            onSelectRoute={setSelectedRoute}
          />

          <div className="route-tabs">
            <button
              onClick={() => setSelectedRoute('ALL')}
              className={selectedRoute === 'ALL' ? 'selected' : ''}
            >
              <Eye size={16} />
              TOATE CAMIOANELE
            </button>

            {routes.map((route, index) => (
              <button
                key={route.vehicleCode}
                onClick={() => setSelectedRoute(route.vehicleCode)}
                className={selectedRoute === route.vehicleCode ? 'selected' : ''}
              >
                <span
                  className="route-dot"
                  style={{
                    background: CULORI_RUTE[index % CULORI_RUTE.length],
                  }}
                />
                {route.vehicleCode}
              </button>
            ))}
          </div>

          {selectedRoute === 'ALL' && (
            <div className="route-details">
              <h3>Toate camioanele afișate</h3>
              <p>
                Folosește butoanele de mai sus ca să filtrezi harta pe un
                singur camion. Fiecare marker are ordinea locală a camionului:
                1, 2, 3...
              </p>
            </div>
          )}

          {selected && (
            <div className="route-details">
              <h3>
                {selected.vehicleCode} - {selected.driverName}
              </h3>

              <p>
                {
                  selected.stops.filter((stop) => stop.stopType === 'DELIVERY')
                    .length
                }{' '}
                livrări •{' '}
                {
                  selected.stops.filter((stop) => stop.stopType === 'DEPOT_LOAD')
                    .length
                }{' '}
                încărcări intermediare • {selected.distanceKm} km •{' '}
                {selected.durationMinutes} minute total ={' '}
                {selected.drivingMinutes} min condus +{' '}
                {selected.serviceMinutes} min livrare/încărcare +{' '}
                {selected.breakMinutes} min pauză • {selected.loadKg} kg /{' '}
                {selected.capacityKg} kg
              </p>

              <ol>
                {selected.stops.map((stop, index) =>
                  stop.stopType === 'DEPOT_LOAD' ? (
                    <li key={`reload-${index}`} className="reload-stop">
                      Încărcare la {stop.customerName} • {stop.requiredProduct}
                    </li>
                  ) : (
                    <li key={stop.orderId}>
                      #{stop.sequence} {stop.customerName} - {stop.address} •{' '}
                      {stop.requiredProduct} • {stop.timeWindow}{' '}
                      <b>{stop.priority}</b>
                    </li>
                  )
                )}
              </ol>

              <p className="micro-copy">
                În varianta reală camionul pornește din depozitul lui inițial,
                dar solverul penalizează plecarea fără produs. În varianta
                ipotetică se pot folosi camioane din orice depozit, deci ruta
                pleacă direct din depozitul care are produsul.
              </p>
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
                stroke="rgba(148,163,184,.16)"
                vertical={false}
              />

              <XAxis
                dataKey="name"
                stroke="#94a3b8"
                tickLine={false}
                axisLine={{
                  stroke: '#334155',
                }}
              />

              <YAxis
                stroke="#94a3b8"
                tickLine={false}
                axisLine={{
                  stroke: '#334155',
                }}
              />

              <Tooltip
                contentStyle={{
                  background: '#0f172a',
                  border: '1px solid #334155',
                  borderRadius: 12,
                  color: '#e5e7eb',
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
