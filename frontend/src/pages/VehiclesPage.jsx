import { useState } from 'react';
import { Trash2, Truck } from 'lucide-react';

import CrudHeader from '../components/CrudHeader';
import { CULORI_RUTE } from '../config/constants';

export default function VehiclesPage({ scenario, callApi, setSelectedRoute }) {
  const [driverId, setDriverId] = useState('');
  const [depotId, setDepotId] = useState('');
  const [kind, setKind] = useState('VAN');

  // Nu se codează dur DRV-01 / HUB-B: dacă șoferul acela este șters, select-ul rămâne gol dar tot îl trimite,
  // iar vehiculul se creează cu „Șofer neasignat".
  const drivers = scenario?.drivers || [];
  const depots = scenario?.depots || [];
  const selectedDriver = drivers.some((d) => d.id === driverId) ? driverId : drivers[0]?.id || '';
  const selectedDepot = depots.some((h) => h.id === depotId) ? depotId : depots[0]?.id || '';

  // Un vehicul are culoare și este selectabil pe hartă doar dacă a primit o rută în planul curent. Camioanele
  // de linehaul (TIR-xx) NU apar niciodată ca rute (rutele lor se numesc CURSA-xx/SPITA-xx), iar dubele nefolosite
  // nu au rută deloc: un click pe ele trimitea harta către un cod inexistent și o lăsa complet goală.
  const routes = scenario?.routes || [];
  const routeIndexByCode = new Map(routes.map((route, index) => [route.vehicleCode, index]));

  return (
    <div className="card">
      <CrudHeader
        title="Flotă"
        hint="Dube regionale (ridicări + livrări în jurul hub-ului) și camioane de linehaul (transfer între hub-uri)."
        onAdd={() =>
          callApi('/vehicles', 'POST', {
            code: '',
            driverId: selectedDriver,
            depotId: selectedDepot,
            kind,
            driverName: '',
            capacityKg: 0,
            consumptionLPer100Km: 0,
            costRonPerKm: 0,
          })
        }
      />

      <div className="crud-form">
        <select value={kind} onChange={(e) => setKind(e.target.value)}>
          <option value="VAN">Dubă regională</option>
          <option value="LINEHAUL">Camion linehaul</option>
        </select>

        <select value={selectedDriver} onChange={(e) => setDriverId(e.target.value)}>
          {drivers.map((driver) => (
            <option key={driver.id} value={driver.id}>
              {driver.name}
            </option>
          ))}
        </select>

        <select value={selectedDepot} onChange={(e) => setDepotId(e.target.value)}>
          {depots.map((hub) => (
            <option key={hub.id} value={hub.id}>
              {hub.name}
            </option>
          ))}
        </select>
      </div>

      <div className="grid cards-grid">
        {scenario?.vehicles?.map((vehicle) => {
          const routeIndex = routeIndexByCode.get(vehicle.code);
          const hasRoute = routeIndex !== undefined;

          return (
          <div
            className={`vehicle-card inner-card ${hasRoute ? '' : 'no-route'}`}
            key={vehicle.code}
            onClick={() => hasRoute && setSelectedRoute(vehicle.code)}
          >
            <div className="vehicle-head">
              <h2>
                <Truck size={16} /> {vehicle.code}
              </h2>

              <span
                className="route-dot big"
                style={{
                  background: !hasRoute
                    ? '#475569'
                    : vehicle.kind === 'LINEHAUL'
                      ? '#a78bfa'
                      : CULORI_RUTE[routeIndex % CULORI_RUTE.length],
                }}
              />
            </div>

            <span className={`kind-badge ${vehicle.kind === 'LINEHAUL' ? 'linehaul' : 'regional'}`}>
              {vehicle.kind === 'LINEHAUL' ? 'LINEHAUL' : 'DUBĂ'}
            </span>

            <p>
              {vehicle.driverName} • hub {vehicle.depotId}
            </p>

            <p className="vehicle-route-note">
              {hasRoute
                ? 'Click pentru a-l urmări pe hartă'
                : vehicle.kind === 'LINEHAUL'
                  ? 'Cursele interurbane apar pe hartă ca CURSA-xx'
                  : 'Fără rută în planul curent'}
            </p>

            <ul>
              <li>{vehicle.capacityKg} kg capacitate</li>
              <li>{vehicle.consumptionLPer100Km} L/100 km</li>
              <li>{vehicle.costRonPerKm} RON/km</li>
            </ul>

            <button
              className="danger full"
              onClick={(e) => {
                e.stopPropagation();
                callApi(`/vehicles/${vehicle.code}`, 'DELETE');
              }}
            >
              <Trash2 size={15} />
              Șterge
            </button>
          </div>
          );
        })}
      </div>
    </div>
  );
}
