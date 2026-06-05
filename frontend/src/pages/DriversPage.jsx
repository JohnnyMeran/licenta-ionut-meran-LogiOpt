import { useState } from 'react';
import { Trash2, Users } from 'lucide-react';

import CrudHeader from '../components/CrudHeader';

export default function DriversPage({ scenario, callApi }) {
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
        {scenario?.drivers?.map((driver) => (
          <div className="driver-card inner-card" key={driver.id}>
            <div className="avatar">
              <Users size={22} />
            </div>

            <h2>{driver.name}</h2>
            <p>{driver.id}</p>

            <div className="driver-metrics">
              <span>
                Condus zilnic
                <br />
                <b>{driver.maxDailyDriveMinutes} min</b>
              </span>

              <span>
                Pauză după
                <br />
                <b>{driver.breakAfterMinutes} min</b>
              </span>

              <span>
                Repaus zilnic
                <br />
                <b>{driver.dailyRestMinutes} min</b>
              </span>
            </div>

            <button
              className="danger full"
              onClick={() => callApi(`/drivers/${driver.id}`, 'DELETE')}
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
