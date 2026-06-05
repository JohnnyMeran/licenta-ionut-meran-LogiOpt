import { useState } from 'react';
import { Trash2 } from 'lucide-react';

import CrudHeader from '../components/CrudHeader';
import { CULORI_RUTE } from '../config/constants';

export default function VehiclesPage({ scenario, callApi, setSelectedRoute }) {
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
        <select value={driverId} onChange={(e) => setDriverId(e.target.value)}>
          {scenario?.drivers?.map((driver) => (
            <option key={driver.id} value={driver.id}>
              {driver.name}
            </option>
          ))}
        </select>

        <select value={depotId} onChange={(e) => setDepotId(e.target.value)}>
          {scenario?.depots?.map((depot) => (
            <option key={depot.id} value={depot.id}>
              {depot.name}
            </option>
          ))}
        </select>
      </div>

      <div className="grid cards-grid">
        {scenario?.vehicles?.map((vehicle, index) => (
          <div
            className="vehicle-card inner-card"
            key={vehicle.code}
            onClick={() => setSelectedRoute(vehicle.code)}
          >
            <div className="vehicle-head">
              <h2>{vehicle.code}</h2>

              <span
                className="route-dot big"
                style={{
                  background: CULORI_RUTE[index % CULORI_RUTE.length],
                }}
              />
            </div>

            <p>
              {vehicle.driverName} • depozit inițial {vehicle.depotId}
            </p>

            <ul>
              <li>{vehicle.capacityKg} kg capacitate standard</li>
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
        ))}
      </div>
    </div>
  );
}
