import { useEffect, useMemo, useState } from 'react';
import { Boxes, Layers, Search, Truck } from 'lucide-react';

import { CULORI_RUTE } from '../config/constants';

const km = (value) => `${Number(value || 0).toFixed(1)} km`;
const kg = (value) => `${Math.round(Number(value || 0))} kg`;

// Aceeași culoare ca traseul de pe hartă (RoutesMap colorează după indexul din aceeași listă),
// ca punctul din fișă să trimită fără ambiguitate la linia desenată.
export function routeColor(route, index) {
  return route.kind === 'LINEHAUL' ? '#a78bfa' : CULORI_RUTE[index % CULORI_RUTE.length];
}

function stopCounts(route) {
  const stops = route.stops || [];
  return {
    total: stops.length,
    pickups: stops.filter((s) => s.stopType === 'PICKUP').length,
    deliveries: stops.filter((s) => s.stopType === 'DELIVERY').length,
  };
}

// Doar vârful de încărcătură se compară cu capacitatea (vezi RouteDto.peakLoadKg): loadKg este greutatea
// totală manipulată într-o zi, iar duba nu cară niciodată tot ce ridică și livrează în același timp.
function peakLoad(route) {
  return Number(route.peakLoadKg ?? route.loadKg ?? 0);
}
function loadPercent(route) {
  if (!route.capacityKg) return 0;
  return (peakLoad(route) / Number(route.capacityKg)) * 100;
}

// Tonurile sunt prefixate cu vs-: în foaia de stil există deja un `.danger` global (butoanele de ștergere),
// care altfel ar transforma bara de încărcare într-un buton inline-flex.
export function vehicleStatus(route) {
  const load = loadPercent(route);
  if (load > 100) return { label: 'Supraîncărcat', tone: 'vs-danger' };
  if (route.drivingMinutes > route.legalMaxDriveMinutes) return { label: 'Peste program', tone: 'vs-danger' };
  if (load >= 90) return { label: 'Aproape plin', tone: 'vs-warn' };
  return { label: 'Optim', tone: 'vs-ok' };
}

function LoadBar({ route }) {
  const percent = loadPercent(route);
  const tone = percent > 100 ? 'vs-danger' : percent >= 90 ? 'vs-warn' : 'vs-ok';
  return (
    <div className={`vs-bar ${tone}`}>
      <i style={{ width: `${Math.min(100, percent)}%` }} />
    </div>
  );
}

function Tile({ label, value, detail, children }) {
  return (
    <div className="vs-tile">
      <span>{label}</span>
      {value && <b>{value}</b>}
      {detail && <small>{detail}</small>}
      {children}
    </div>
  );
}

// Fișa vehiculului selectat. Când nu e ales niciunul, același spațiu ține un rezumat al întregii flote
// afișate, ca înălțimea panoului să nu sară la fiecare selecție.
function SelectedCard({ route, index, routes }) {
  if (!route) {
    const totals = routes.reduce(
      (acc, r) => {
        const c = stopCounts(r);
        return {
          distance: acc.distance + Number(r.distanceKm || 0),
          stops: acc.stops + c.total,
          pickups: acc.pickups + c.pickups,
          deliveries: acc.deliveries + c.deliveries,
          load: acc.load + Number(r.loadKg || 0),
        };
      },
      { distance: 0, stops: 0, pickups: 0, deliveries: 0, load: 0 }
    );
    const flagged = routes.filter((r) => vehicleStatus(r).tone !== 'vs-ok').length;

    return (
      <div className="vs-card all">
        <div className="vs-card-head">
          <span className="vs-dot multi" />
          <div>
            <b>TOATE VEHICULELE</b>
            <span>{routes.length} rute afișate pe hartă</span>
          </div>
          <span className="vs-kind">FLOTĂ</span>
        </div>

        <div className="vs-tiles">
          <Tile label="Distanță totală" value={km(totals.distance)} />
          <Tile
            label="Opriri"
            value={`${totals.stops} opriri`}
            detail={`${totals.pickups}↑ ridicări · ${totals.deliveries}↓ livrări`}
          />
          <Tile label="Colete gestionate" value={kg(totals.load)} />
          <Tile label="Status">
            <div className="vs-status-slot">
              <span className={`vs-badge ${flagged ? 'vs-warn' : 'vs-ok'}`}>
                {flagged ? `${flagged} de revizuit` : 'Toate optime'}
              </span>
            </div>
          </Tile>
        </div>
      </div>
    );
  }

  const counts = stopCounts(route);
  const status = vehicleStatus(route);

  return (
    <div className="vs-card">
      <div className="vs-card-head">
        <span className="vs-dot" style={{ background: routeColor(route, index) }} />
        <div>
          <b>{route.vehicleCode}</b>
          <span>{route.driverName}</span>
        </div>
        <span className="vs-kind">{route.kind === 'LINEHAUL' ? 'CAMION' : 'DUBĂ'}</span>
      </div>

      <div className="vs-tiles">
        <Tile label="Distanță" value={km(route.distanceKm)} />
        <Tile
          label="Opriri"
          value={`${counts.total} opriri`}
          detail={`${counts.pickups}↑ ridicări · ${counts.deliveries}↓ livrări`}
        />
        <Tile
          label="Încărcare maximă"
          value={`${kg(peakLoad(route))} / ${kg(route.capacityKg)}`}
          detail={`${kg(route.loadKg)} gestionate în total`}
        >
          <LoadBar route={route} />
        </Tile>
        <Tile label="Status">
          <div className="vs-status-slot">
            <span className={`vs-badge ${status.tone}`}>{status.label}</span>
          </div>
        </Tile>
      </div>
    </div>
  );
}

export default function VehicleSelector({ routes, selectedRoute, onSelect }) {
  const [query, setQuery] = useState('');
  const [kind, setKind] = useState('ALL');

  const vans = routes.filter((r) => r.kind !== 'LINEHAUL').length;
  const trucks = routes.filter((r) => r.kind === 'LINEHAUL').length;

  // Filtrul pe tip și căutarea se resetează când se schimbă traseele (alt oraș, altă rulare). Altfel, cu chip-ul
  // „Curse" activ și un filtru pe oraș care nu are curse, lista rămânea goală și dădea vina pe căutare.
  const routesKey = `${routes.length}:${routes[0]?.vehicleCode || ''}:${vans}:${trucks}`;
  useEffect(() => {
    setKind('ALL');
    setQuery('');
  }, [routesKey]);

  // Indexul din lista completă dă culoarea; filtrele nu au voie să o schimbe, altfel dot-ul
  // din listă n-ar mai corespunde cu traseul de pe hartă.
  const visible = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return routes
      .map((route, index) => ({ route, index }))
      .filter(({ route }) => (kind === 'ALL' ? true : kind === 'LINEHAUL' ? route.kind === 'LINEHAUL' : route.kind !== 'LINEHAUL'))
      .filter(({ route }) =>
        !needle ||
        route.vehicleCode.toLowerCase().includes(needle) ||
        (route.driverName || '').toLowerCase().includes(needle)
      );
  }, [routes, query, kind]);

  const selectedIndex = routes.findIndex((r) => r.vehicleCode === selectedRoute);
  const selected = selectedIndex >= 0 ? routes[selectedIndex] : null;

  // „Curse", nu „Camioane": fiecare rută de linehaul este o cursă. În planul neoptimizat un camion face mai
  // multe spițe dus-întors, deci numărul de curse (14) nu este numărul de camioane deținute (6).
  const chips = [
    ['ALL', 'Toate', routes.length, Layers],
    ['VAN', 'Dube', vans, Boxes],
    ['LINEHAUL', 'Curse', trucks, Truck],
  ];

  return (
    <div className="vehicle-selector">
      <SelectedCard route={selected} index={selectedIndex} routes={routes} />

      <label className="vs-search">
        <Search size={17} />
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Caută după cod sau șofer..."
        />
      </label>

      <div className="vs-chips">
        {chips.map(([value, label, count, Icon]) => (
          <button
            key={value}
            className={kind === value ? 'selected' : ''}
            onClick={() => setKind(value)}
            disabled={count === 0}
          >
            <Icon size={14} />
            {label} {count}
          </button>
        ))}
      </div>

      <div className="vs-list">
        <button
          className={`vs-row all-row ${selectedRoute === 'ALL' ? 'selected' : ''}`}
          onClick={() => onSelect('ALL')}
        >
          <span className="vs-dot small multi" />
          <div className="vs-row-main">
            <b>Toate vehiculele</b>
            <span>afișează toate rutele pe hartă</span>
          </div>
        </button>

        {visible.map(({ route, index }) => {
          const counts = stopCounts(route);
          const status = vehicleStatus(route);
          return (
            <button
              key={route.vehicleCode}
              className={`vs-row ${selectedRoute === route.vehicleCode ? 'selected' : ''}`}
              onClick={() => onSelect(route.vehicleCode)}
            >
              <span className="vs-dot small" style={{ background: routeColor(route, index) }} />

              <div className="vs-row-main">
                <b>
                  {route.vehicleCode}
                  <em>{route.driverName}</em>
                </b>
                <span>
                  {km(route.distanceKm)} · {counts.total} opriri
                </span>
              </div>

              <div className="vs-row-side">
                <span className={`vs-badge ${status.tone}`}>{status.label}</span>
                <LoadBar route={route} />
              </div>
            </button>
          );
        })}

        {visible.length === 0 && (
          <p className="vs-empty">
            {query
              ? `Niciun vehicul nu se potrivește cu „${query}”.`
              : 'Niciun vehicul de acest tip în planul curent.'}
          </p>
        )}
      </div>
    </div>
  );
}
