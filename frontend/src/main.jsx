import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';

import {
  AlertTriangle,
  BarChart3,
  ClipboardList,
  Eye,
  Fuel,
  Gauge,
  LayoutDashboard,
  Package,
  PlayCircle,
  Plus,
  RefreshCw,
  Route,
  Settings,
  Trash2,
  Truck,
  Users,
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

import {
  MapContainer,
  Marker,
  Polyline,
  Popup,
  TileLayer,
  useMap,
} from 'react-leaflet';

import L from 'leaflet';

import 'leaflet/dist/leaflet.css';
import './styles.css';

const API = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

const CULORI_RUTE = [
  '#38bdf8',
  '#22c55e',
  '#f97316',
  '#a78bfa',
  '#ef4444',
  '#eab308',
  '#14b8a6',
  '#f43f5e',
  '#84cc16',
];

const PRODUSE = [
  'alimente',
  'carti',
  'fashion',
  'electronice',
  'farmaceutice',
  'mobila',
];

const PRIORITATI = ['URGENT', 'NORMAL', 'SCĂZUTĂ'];

function createDivIcon(className, html) {
  return L.divIcon({
    className,
    html,
    iconSize: [38, 38],
    iconAnchor: [19, 19],
    popupAnchor: [0, -18],
  });
}

function orderIcon(label, color, active) {
  return createDivIcon(
    `order-marker ${active ? 'active-order-marker' : ''}`,
    `<span style="border-color:${color};background:${color}">${label}</span>`
  );
}

function depotMarker(label) {
  return createDivIcon('depot-marker', `<span>${label}</span>`);
}

function minuteToTime(min) {
  const h = Math.floor(min / 60).toString().padStart(2, '0');
  const m = (min % 60).toString().padStart(2, '0');

  return `${h}:${m}`;
}

function timeToMinute(t) {
  const [h, m] = (t || '09:00').split(':').map(Number);

  return h * 60 + m;
}

async function geocodeAddressInBucharest(address) {
  const query = `${address}, București, România`;

  const url = `https://nominatim.openstreetmap.org/search?format=json&limit=1&addressdetails=1&countrycodes=ro&q=${encodeURIComponent(
    query
  )}`;

  const res = await fetch(url, {
    headers: {
      Accept: 'application/json',
    },
  });

  if (!res.ok) {
    throw new Error('Serviciul de geocodare nu răspunde');
  }

  const results = await res.json();
  const first = results?.[0];

  if (!first) {
    throw new Error('Adresa nu a fost găsită în București');
  }

  return {
    latitude: Number(first.lat),
    longitude: Number(first.lon),
    label: first.display_name,
  };
}

function PotrivesteHarta({ scenario, geometries }) {
  const map = useMap();

  useEffect(() => {
    if (!scenario) return;

    const coords = [
      ...scenario.depots.map((d) => [d.latitude, d.longitude]),
      ...scenario.orders.map((o) => [o.latitude, o.longitude]),
      ...Object.values(geometries).flat(),
    ];

    if (coords.length > 1) {
      map.fitBounds(coords, {
        padding: [28, 28],
      });
    }
  }, [scenario, geometries, map]);

  return null;
}

function StatCard({ icon: Icon, title, value, hint }) {
  return (
    <div className="card stat">
      <div className="icon">
        <Icon size={22} />
      </div>

      <div>
        <p>{title}</p>
        <h2>{value}</h2>
        <span>{hint}</span>
      </div>
    </div>
  );
}

function EmptyHint({ text }) {
  return <div className="empty-hint">{text}</div>;
}

function fallbackCoordinates(route) {
  const coords = [[route.depot.latitude, route.depot.longitude]];

  route.stops.forEach((s) => {
    coords.push([s.latitude, s.longitude]);
  });

  coords.push([route.depot.latitude, route.depot.longitude]);

  return coords;
}

async function fetchRoadGeometry(route) {
  const points = [route.depot, ...route.stops, route.depot];

  const coords = points
    .map((p) => `${p.longitude},${p.latitude}`)
    .join(';');

  const url = `https://router.project-osrm.org/route/v1/driving/${coords}?overview=full&geometries=geojson`;

  const res = await fetch(url);

  if (!res.ok) {
    throw new Error('OSRM indisponibil');
  }

  const json = await res.json();

  return (
    json.routes?.[0]?.geometry?.coordinates?.map(([lng, lat]) => [lat, lng]) ||
    fallbackCoordinates(route)
  );
}

function HartaRute({ scenario, selectedRoute, onSelectRoute, routes }) {
  const [geometries, setGeometries] = useState({});
  const [routingStatus, setRoutingStatus] = useState(
    'Se calculează traseele pe șosea...'
  );

  useEffect(() => {
    let cancelled = false;

    async function loadRoads() {
      if (!routes?.length) return;

      setRoutingStatus('Se calculează traseele pe șosea...');

      const result = {};

      for (const route of routes) {
        try {
          result[route.vehicleCode] = await fetchRoadGeometry(route);
        } catch {
          result[route.vehicleCode] = fallbackCoordinates(route);
        }
      }

      if (!cancelled) {
        setGeometries(result);
        setRoutingStatus(
          'Traseele sunt afișate pe drumuri reale prin OpenStreetMap/OSRM. Dacă OSRM cade, aplicația folosește fallback local.'
        );
      }
    }

    loadRoads();

    return () => {
      cancelled = true;
    };
  }, [routes]);

  if (!scenario) {
    return <EmptyHint text="Se încarcă harta logistică..." />;
  }

  const visibleRoutes =
    selectedRoute === 'ALL'
      ? routes
      : routes.filter((r) => r.vehicleCode === selectedRoute);

  const centerDepot = scenario.depot || scenario.depots?.[0];
  const center = [centerDepot.latitude, centerDepot.longitude];

  return (
    <div className="real-leaflet-map">
      <MapContainer
        center={center}
        zoom={11}
        scrollWheelZoom
        className="leaflet-shell"
      >
        <TileLayer
          attribution="&copy; OpenStreetMap contributors"
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />

        <PotrivesteHarta scenario={scenario} geometries={geometries} />

        {visibleRoutes.map((route) => {
          const idx = routes.findIndex(
            (r) => r.vehicleCode === route.vehicleCode
          );

          const color = CULORI_RUTE[idx % CULORI_RUTE.length];

          const positions =
            geometries[route.vehicleCode] || fallbackCoordinates(route);

          return (
            <Polyline
              key={route.vehicleCode}
              positions={positions}
              pathOptions={{
                color,
                weight: 6,
                opacity: 0.9,
              }}
              eventHandlers={{
                click: () => onSelectRoute(route.vehicleCode),
              }}
            />
          );
        })}

        {scenario.depots.map((d, i) => (
          <Marker
            key={d.id}
            position={[d.latitude, d.longitude]}
            icon={depotMarker(`D${i + 1}`)}
          >
            <Popup>
              <b>{d.name}</b>
              <br />
              Produse: {d.products.join(', ')}
            </Popup>
          </Marker>
        ))}

        {scenario.orders.map((order) => {
          const belongsTo = routes.find((r) =>
            r.stops.some(
              (s) =>
                s.stopType === 'DELIVERY' &&
                Number(s.orderId) === Number(order.id)
            )
          );

          if (
            !belongsTo ||
            (selectedRoute !== 'ALL' &&
              belongsTo.vehicleCode !== selectedRoute)
          ) {
            return null;
          }

          const stop = belongsTo.stops.find(
            (s) =>
              s.stopType === 'DELIVERY' &&
              Number(s.orderId) === Number(order.id)
          );

          const idx = routes.findIndex(
            (r) => r.vehicleCode === belongsTo.vehicleCode
          );

          const color = CULORI_RUTE[idx % CULORI_RUTE.length];

          return (
            <Marker
              key={order.id}
              position={[order.latitude, order.longitude]}
              icon={orderIcon(
                stop?.sequence || '?',
                color,
                selectedRoute === belongsTo.vehicleCode
              )}
              eventHandlers={{
                click: () => onSelectRoute(belongsTo.vehicleCode),
              }}
            >
              <Popup>
                <b>
                  Livrarea {stop?.sequence || '?'} - Comanda #{order.id}
                </b>
                <br />
                {order.customerName}
                <br />
                {order.address}
                <br />
                Produs: {order.requiredProduct}
                <br />
                Interval: {order.timeWindow}
                <br />
                Camion: {belongsTo.vehicleCode}
              </Popup>
            </Marker>
          );
        })}

        {visibleRoutes.flatMap((route) =>
          route.stops
            .filter((s) => s.stopType === 'DEPOT_LOAD')
            .map((s, i) => (
              <Marker
                key={`${route.vehicleCode}-reload-${i}`}
                position={[s.latitude, s.longitude]}
                icon={depotMarker('Î')}
              >
                <Popup>
                  <b>{s.customerName}</b>
                  <br />
                  {s.address}
                  <br />
                  Camion: {route.vehicleCode}
                  <br />
                  Produse disponibile: {s.requiredProduct}
                </Popup>
              </Marker>
            ))
        )}
      </MapContainer>

      <div className="map-note">
        <Route size={16} />
        {routingStatus}
      </div>
    </div>
  );
}

function Dashboard({
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

  const selected = routes.find((r) => r.vehicleCode === selectedRoute);

  const totalCost = routes.reduce((sum, r) => sum + r.costRon, 0);

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

          <HartaRute
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

            {routes.map((r, idx) => (
              <button
                key={r.vehicleCode}
                onClick={() => setSelectedRoute(r.vehicleCode)}
                className={selectedRoute === r.vehicleCode ? 'selected' : ''}
              >
                <span
                  className="route-dot"
                  style={{
                    background: CULORI_RUTE[idx % CULORI_RUTE.length],
                  }}
                />
                {r.vehicleCode}
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
                  selected.stops.filter((s) => s.stopType === 'DELIVERY')
                    .length
                }{' '}
                livrări •{' '}
                {
                  selected.stops.filter((s) => s.stopType === 'DEPOT_LOAD')
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
                {selected.stops.map((s, i) =>
                  s.stopType === 'DEPOT_LOAD' ? (
                    <li key={`reload-${i}`} className="reload-stop">
                      Încărcare la {s.customerName} • {s.requiredProduct}
                    </li>
                  ) : (
                    <li key={s.orderId}>
                      #{s.sequence} {s.customerName} - {s.address} •{' '}
                      {s.requiredProduct} • {s.timeWindow}{' '}
                      <b>{s.priority}</b>
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
                    routes.reduce(
                      (sum, r) => sum + r.durationMinutes,
                      0
                    ) / routes.length
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

function CrudHeader({ title, hint, onAdd }) {
  return (
    <div className="card-title">
      <div>
        <h2>{title}</h2>
        <span>{hint}</span>
      </div>

      <button className="small-primary" onClick={onAdd}>
        <Plus size={16} />
        Adaugă
      </button>
    </div>
  );
}

function OrdersPage({ scenario, callApi }) {
  const [form, setForm] = useState({
    customerName: 'Client nou',
    address: 'București',
    latitude: 44.43,
    longitude: 26.1,
    weightKg: 50,
    timeStart: '09:00',
    timeEnd: '12:00',
    serviceMinutes: 10,
    priority: 'NORMAL',
    requiredProduct: 'alimente',
  });

  const [geoStatus, setGeoStatus] = useState('');
  const [geocoding, setGeocoding] = useState(false);
  const [manualCoords, setManualCoords] = useState(false);

  const geocode = async () => {
    setGeocoding(true);
    setGeoStatus('Caut adresa pe hartă...');

    try {
      const found = await geocodeAddressInBucharest(form.address);

      setForm((prev) => ({
        ...prev,
        latitude: found.latitude,
        longitude: found.longitude,
      }));

      setGeoStatus(
        `Adresă găsită: ${found.latitude.toFixed(5)}, ${found.longitude.toFixed(
          5
        )}`
      );

      return found;
    } catch (e) {
      setGeoStatus(
        e.message || 'Strada nu a fost găsită. Completează coordonatele manual.'
      );

      return null;
    } finally {
      setGeocoding(false);
    }
  };

  const add = async () => {
    const found = manualCoords
      ? {
          latitude: Number(form.latitude),
          longitude: Number(form.longitude),
        }
      : await geocode();

    if (!found) return;

    const payload = {
      ...form,
      id: 0,
      latitude: found.latitude,
      longitude: found.longitude,
      timeWindow: `${form.timeStart}-${form.timeEnd}`,
      windowStartMinute: timeToMinute(form.timeStart),
      windowEndMinute: timeToMinute(form.timeEnd),
    };

    callApi('/orders', 'POST', payload);
  };

  return (
    <div className="card table-card">
      <CrudHeader
        title="Comenzi"
        hint="CRUD comenzi + adresă geocodată automat sau coordonate manuale"
        onAdd={add}
      />

      <div className="crud-form orders-form">
        <input
          placeholder="Client"
          value={form.customerName}
          onChange={(e) =>
            setForm({
              ...form,
              customerName: e.target.value,
            })
          }
        />

        <input
          className="wide-field"
          placeholder="Adresă în București"
          value={form.address}
          onChange={(e) =>
            setForm({
              ...form,
              address: e.target.value,
            })
          }
        />

        <button
          type="button"
          className="small-primary inline-action"
          onClick={geocode}
          disabled={geocoding}
        >
          Caută coordonate
        </button>

        <label className="input-suffix">
          <input
            type="number"
            value={form.weightKg}
            onChange={(e) =>
              setForm({
                ...form,
                weightKg: +e.target.value,
              })
            }
          />
          <span>KG</span>
        </label>

        <input
          type="number"
          step="0.0001"
          value={form.latitude}
          title="Latitudine"
          onChange={(e) =>
            setForm({
              ...form,
              latitude: +e.target.value,
            })
          }
        />

        <input
          type="number"
          step="0.0001"
          value={form.longitude}
          title="Longitudine"
          onChange={(e) =>
            setForm({
              ...form,
              longitude: +e.target.value,
            })
          }
        />

        <label className="check-field">
          <input
            type="checkbox"
            checked={manualCoords}
            onChange={(e) => setManualCoords(e.target.checked)}
          />
          folosesc coordonatele manual
        </label>

        <input
          type="time"
          value={form.timeStart}
          onChange={(e) =>
            setForm({
              ...form,
              timeStart: e.target.value,
            })
          }
        />

        <input
          type="time"
          value={form.timeEnd}
          onChange={(e) =>
            setForm({
              ...form,
              timeEnd: e.target.value,
            })
          }
        />

        <select
          value={form.priority}
          onChange={(e) =>
            setForm({
              ...form,
              priority: e.target.value,
            })
          }
        >
          {PRIORITATI.map((p) => (
            <option key={p}>{p}</option>
          ))}
        </select>

        <select
          value={form.requiredProduct}
          onChange={(e) =>
            setForm({
              ...form,
              requiredProduct: e.target.value,
            })
          }
        >
          {PRODUSE.map((p) => (
            <option key={p}>{p}</option>
          ))}
        </select>
      </div>

      {geoStatus && <div className="geocode-status">{geoStatus}</div>}

      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Client</th>
            <th>Adresă</th>
            <th>Coordonate</th>
            <th>Interval</th>
            <th>Greutate</th>
            <th>Prioritate</th>
            <th>Produs</th>
            <th></th>
          </tr>
        </thead>

        <tbody>
          {scenario?.orders?.map((o) => (
            <tr key={o.id}>
              <td>#{o.id}</td>
              <td>{o.customerName}</td>
              <td>{o.address}</td>
              <td>
                {Number(o.latitude).toFixed(4)},{' '}
                {Number(o.longitude).toFixed(4)}
              </td>
              <td>{o.timeWindow}</td>
              <td>{o.weightKg} kg</td>
              <td>
                <span className="tag">{o.priority}</span>
              </td>
              <td>{o.requiredProduct}</td>
              <td>
                <button
                  className="danger"
                  onClick={() => callApi(`/orders/${o.id}`, 'DELETE')}
                >
                  <Trash2 size={15} />
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function DriversPage({ scenario, callApi }) {
  const [name, setName] = useState('Șofer nou');

  return (
    <div className="card">
      <CrudHeader
        title="Șoferi"
        hint="ore legale: 9h condus/zi, pauză 45 min după 4h30, repaus 11h"
        onAdd={() =>
          callApi('/drivers', 'POST', {
            id: '',
            name,
          })
        }
      />

      <div className="crud-form">
        <input value={name} onChange={(e) => setName(e.target.value)} />
      </div>

      <div className="grid cards-grid">
        {scenario?.drivers?.map((d) => (
          <div className="driver-card inner-card" key={d.id}>
            <div className="avatar">
              <Users size={22} />
            </div>

            <h2>{d.name}</h2>
            <p>{d.id}</p>

            <div className="driver-metrics">
              <span>
                Condus zilnic
                <br />
                <b>{d.maxDailyDriveMinutes} min</b>
              </span>

              <span>
                Pauză după
                <br />
                <b>{d.breakAfterMinutes} min</b>
              </span>

              <span>
                Repaus zilnic
                <br />
                <b>{d.dailyRestMinutes} min</b>
              </span>
            </div>

            <button
              className="danger full"
              onClick={() => callApi(`/drivers/${d.id}`, 'DELETE')}
            >
              <Trash2 size={15} />
              Șterge
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}

function VehiclesPage({ scenario, callApi, setSelectedRoute }) {
  const [driverId, setDriverId] = useState('DRV-01');
  const [depotId, setDepotId] = useState('DEP-C');

  return (
    <div className="card">
      <CrudHeader
        title="Camioane"
        hint="CRUD camioane; toate au aceeași capacitate standard, pot încărca și din alte depozite"
        onAdd={() =>
          callApi('/vehicles', 'POST', {
            code: '',
            driverId,
            depotId,
            driverName: '',
            capacityKg: 900,
            consumptionLPer100Km: 8,
            costRonPerKm: 2.25,
          })
        }
      />

      <div className="crud-form">
        <select
          value={driverId}
          onChange={(e) => setDriverId(e.target.value)}
        >
          {scenario?.drivers?.map((d) => (
            <option key={d.id} value={d.id}>
              {d.name}
            </option>
          ))}
        </select>

        <select value={depotId} onChange={(e) => setDepotId(e.target.value)}>
          {scenario?.depots?.map((d) => (
            <option key={d.id} value={d.id}>
              {d.name}
            </option>
          ))}
        </select>
      </div>

      <div className="grid cards-grid">
        {scenario?.vehicles?.map((v, idx) => (
          <div
            className="vehicle-card inner-card"
            key={v.code}
            onClick={() => setSelectedRoute(v.code)}
          >
            <div className="vehicle-head">
              <h2>{v.code}</h2>

              <span
                className="route-dot big"
                style={{
                  background: CULORI_RUTE[idx % CULORI_RUTE.length],
                }}
              />
            </div>

            <p>
              {v.driverName} • depozit inițial {v.depotId}
            </p>

            <ul>
              <li>{v.capacityKg} kg capacitate standard</li>
              <li>{v.consumptionLPer100Km} L/100 km</li>
              <li>{v.costRonPerKm} RON/km</li>
            </ul>

            <button
              className="danger full"
              onClick={(e) => {
                e.stopPropagation();
                callApi(`/vehicles/${v.code}`, 'DELETE');
              }}
            >
              <Trash2 size={15} />
              Șterge
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}

function ReportsPage({ stats }) {
  const rows = [
    ['Distanță inițială', `${stats.initialDistanceKm || 0} km`],
    ['Distanță reală', `${stats.optimizedDistanceKm || 0} km`],
    ['Distanță best ipotetic', `${stats.hypotheticalDistanceKm || 0} km`],
    ['Timp total inițial', `${stats.initialDurationMinutes || 0} min`],
    ['Timp total real', `${stats.optimizedDurationMinutes || 0} min`],
    ['Timp total best ipotetic', `${stats.hypotheticalDurationMinutes || 0} min`],
    [
      'Timp mediu livrare inițial',
      `${stats.initialAverageDeliveryMinutes || 0} min/comandă`,
    ],
    [
      'Timp mediu livrare real',
      `${stats.optimizedAverageDeliveryMinutes || 0} min/comandă`,
    ],
    [
      'Timp mediu livrare ipotetic',
      `${stats.hypotheticalAverageDeliveryMinutes || 0} min/comandă`,
    ],
    ['Combustibil economisit real', `${stats.fuelSavedLiters || 0} L`],
    ['Cost economisit real', `${stats.costSavedRon || 0} RON`],
    [
      'Cost economisit ipotetic',
      `${stats.hypotheticalCostSavedRon || 0} RON`,
    ],
  ];

  return (
    <div className="card report-card">
      <div className="card-title">
        <h2>Rapoarte</h2>
        <span>inițial / real / ipotetic</span>
      </div>

      {rows.map(([k, v]) => (
        <div className="report-row" key={k}>
          <span>{k}</span>
          <b>{v}</b>
        </div>
      ))}

      <div className="notice">
        <AlertTriangle size={18} />
        Reguli implementate: capacitate camion 900 kg, cost salarial pe camion
        folosit, compatibilitate produs-depozit, ferestre de livrare, 9h
        condus/zi, pauză 45 min după 4h30 și repaus zilnic de 11h.
      </div>
    </div>
  );
}

function SettingsPage({ scenario, callApi }) {
  const current = scenario?.settings || {
    realSolverSeconds: 60,
    hypotheticalSolverSeconds: 60,
    fuelPriceRonPerLiter: 7.45,
    driverDailySalaryRon: 300,
  };

  const [form, setForm] = useState(current);

  useEffect(() => {
    setForm(current);
  }, [
    current.realSolverSeconds,
    current.hypotheticalSolverSeconds,
    current.fuelPriceRonPerLiter,
    current.driverDailySalaryRon,
  ]);

  const save = () => {
    callApi('/settings', 'PUT', {
      realSolverSeconds: +form.realSolverSeconds,
      hypotheticalSolverSeconds: +form.hypotheticalSolverSeconds,
      fuelPriceRonPerLiter: +form.fuelPriceRonPerLiter,
      driverDailySalaryRon: +form.driverDailySalaryRon,
    });
  };

  return (
    <div className="card">
      <div className="card-title">
        <div>
          <h2>Setări simulare</h2>
          <span>
            valorile se trimit în backend și afectează următoarea optimizare
          </span>
        </div>

        <button className="small-primary" onClick={save}>
          Salvează setările
        </button>
      </div>

      <div className="settings-grid">
        <label>
          Limită timp solver real, secunde
          <input
            type="number"
            min="60"
            max="300"
            value={form.realSolverSeconds}
            onChange={(e) =>
              setForm({
                ...form,
                realSolverSeconds: e.target.value,
              })
            }
          />
        </label>

        <label>
          Limită timp solver ipotetic, secunde
          <input
            type="number"
            min="60"
            max="300"
            value={form.hypotheticalSolverSeconds}
            onChange={(e) =>
              setForm({
                ...form,
                hypotheticalSolverSeconds: e.target.value,
              })
            }
          />
        </label>

        <label>
          Preț combustibil, RON/L
          <input
            type="number"
            min="1"
            max="30"
            step="0.01"
            value={form.fuelPriceRonPerLiter}
            onChange={(e) =>
              setForm({
                ...form,
                fuelPriceRonPerLiter: e.target.value,
              })
            }
          />
        </label>

        <label>
          Cost șofer / zi, RON
          <input
            type="number"
            min="0"
            max="2000"
            step="10"
            value={form.driverDailySalaryRon}
            onChange={(e) =>
              setForm({
                ...form,
                driverDailySalaryRon: e.target.value,
              })
            }
          />
        </label>
      </div>

      <p className="micro-copy settings-note">
        Sursa hărții rămâne intern fixă: OpenStreetMap + OSRM. Regulile legale
        sunt constrângeri ale modelului, nu setări modificabile din interfață.
      </p>
    </div>
  );
}

function App() {
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

  const optimize = async (hyp = false) => {
    setLoading(true);
    setError('');

    try {
      const res = await fetch(
        `${API}${hyp ? '/optimize/hypothetical' : '/optimize'}`,
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
      setRouteMode(hyp ? 'hypothetical' : 'solved');
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

  const pages = [
    ['dashboard', LayoutDashboard, 'Dashboard'],
    ['orders', ClipboardList, 'Comenzi'],
    ['drivers', Users, 'Șoferi'],
    ['vehicles', Truck, 'Camioane'],
    ['reports', BarChart3, 'Rapoarte'],
    ['settings', Settings, 'Setări'],
  ];

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
          {pages.map(([key, Icon, label]) => (
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

createRoot(document.getElementById('root')).render(<App />);