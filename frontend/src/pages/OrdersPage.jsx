import { useState } from 'react';
import { Trash2 } from 'lucide-react';

import CrudHeader from '../components/CrudHeader';
import { PRIORITATI, PRODUSE } from '../config/constants';
import { geocodeAddressInBucharest } from '../utils/geocode';
import { timeToMinute } from '../utils/time';

export default function OrdersPage({ scenario, callApi }) {
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
          {PRIORITATI.map((priority) => (
            <option key={priority}>{priority}</option>
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
          {PRODUSE.map((product) => (
            <option key={product}>{product}</option>
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
          {scenario?.orders?.map((order) => (
            <tr key={order.id}>
              <td>#{order.id}</td>
              <td>{order.customerName}</td>
              <td>{order.address}</td>
              <td>
                {Number(order.latitude).toFixed(4)},{' '}
                {Number(order.longitude).toFixed(4)}
              </td>
              <td>{order.timeWindow}</td>
              <td>{order.weightKg} kg</td>
              <td>
                <span className="tag">{order.priority}</span>
              </td>
              <td>{order.requiredProduct}</td>
              <td>
                <button
                  className="danger"
                  onClick={() => callApi(`/orders/${order.id}`, 'DELETE')}
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
