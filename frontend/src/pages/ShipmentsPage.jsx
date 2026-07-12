import { useState } from 'react';
import { ArrowRight, MapPin, Trash2 } from 'lucide-react';

import CrudHeader from '../components/CrudHeader';
import { ORASE, PRIORITATI } from '../config/constants';
import { geocodeAddress } from '../utils/geocode';
import { timeToMinute } from '../utils/time';

const emptyForm = {
  senderName: 'Expeditor nou',
  recipientName: 'Destinatar nou',
  pickupCity: 'București',
  pickupAddress: 'Calea Victoriei 100',
  pickupLat: 44.43,
  pickupLon: 26.1,
  deliveryCity: 'Cluj-Napoca',
  deliveryAddress: 'Str. Memorandumului 21',
  deliveryLat: 46.77,
  deliveryLon: 23.62,
  weightKg: 15,
  timeStart: '09:00',
  timeEnd: '14:00',
  serviceMinutes: 10,
  priority: 'NORMAL',
  tariffRon: 0,
};

export default function ShipmentsPage({ scenario, callApi }) {
  const [form, setForm] = useState(emptyForm);
  const [status, setStatus] = useState('');
  const [busy, setBusy] = useState(false);
  const [search, setSearch] = useState('');

  const allShipments = scenario?.shipments || [];
  const list = allShipments.filter((s) => {
    const q = search.trim().toLowerCase();
    if (!q) return true;
    return `${s.code} ${s.senderName} ${s.recipientName} ${s.pickupCity} ${s.deliveryCity}`
      .toLowerCase()
      .includes(q);
  });

  const set = (patch) => setForm((prev) => ({ ...prev, ...patch }));

  const geocodeBoth = async () => {
    setBusy(true);
    setStatus('Caut coordonatele pentru ridicare și livrare...');

    try {
      const pickup = await geocodeAddress(form.pickupAddress, form.pickupCity);
      const delivery = await geocodeAddress(form.deliveryAddress, form.deliveryCity);

      set({
        pickupLat: pickup.latitude,
        pickupLon: pickup.longitude,
        deliveryLat: delivery.latitude,
        deliveryLon: delivery.longitude,
      });

      setStatus(
        `Ridicare ${pickup.latitude.toFixed(4)}, ${pickup.longitude.toFixed(4)} · ` +
          `Livrare ${delivery.latitude.toFixed(4)}, ${delivery.longitude.toFixed(4)}`
      );

      return { pickup, delivery };
    } catch (error) {
      setStatus(error.message || 'Geocodarea a eșuat. Completează coordonatele manual.');
      return null;
    } finally {
      setBusy(false);
    }
  };

  const add = async () => {
    const found = await geocodeBoth();

    // Fără geocodare NU se trimite nimic. Formularul nu are câmpuri de coordonate, deci valorile de rezervă erau
    // cele din formularul gol (București → Cluj): un colet „Iași → Constanța" ajungea salvat cu poziții din alte
    // orașe, iar backend-ul îi atribuia hub-urile după coordonate. Datele ieșeau corupte fără niciun avertisment.
    if (!found) {
      setStatus(
        'Adresele nu au putut fi localizate, așa că nu am salvat coletul. Verifică adresa și orașul, apoi încearcă din nou.'
      );
      return;
    }

    const pickupLat = found.pickup.latitude;
    const pickupLon = found.pickup.longitude;
    const deliveryLat = found.delivery.latitude;
    const deliveryLon = found.delivery.longitude;

    const payload = {
      id: 0,
      senderName: form.senderName,
      recipientName: form.recipientName,
      pickupAddress: form.pickupAddress,
      pickupCity: form.pickupCity,
      pickupLat,
      pickupLon,
      originHubId: '',
      deliveryAddress: form.deliveryAddress,
      deliveryCity: form.deliveryCity,
      deliveryLat,
      deliveryLon,
      destHubId: '',
      weightKg: Number(form.weightKg),
      timeWindow: `${form.timeStart}-${form.timeEnd}`,
      windowStartMinute: timeToMinute(form.timeStart),
      windowEndMinute: timeToMinute(form.timeEnd),
      serviceMinutes: Number(form.serviceMinutes),
      priority: form.priority,
      tariffRon: Number(form.tariffRon) || 0,
    };

    callApi('/shipments', 'POST', payload);
  };

  return (
    <div className="card table-card">
      <CrudHeader
        title="Colete"
        hint="Ridicare de la expeditor → hub → livrare la destinatar. Hub-ul de origine/destinație se alege automat după oraș."
        onAdd={add}
      />

      <div className="crud-form shipment-form">
        <div className="shipment-leg">
          <span className="leg-title pickup">Ridicare</span>
          <input
            placeholder="Expeditor"
            value={form.senderName}
            onChange={(e) => set({ senderName: e.target.value })}
          />
          <select value={form.pickupCity} onChange={(e) => set({ pickupCity: e.target.value })}>
            {ORASE.map((city) => (
              <option key={city}>{city}</option>
            ))}
          </select>
          <input
            className="wide-field"
            placeholder="Adresă ridicare"
            value={form.pickupAddress}
            onChange={(e) => set({ pickupAddress: e.target.value })}
          />
        </div>

        <div className="shipment-leg">
          <span className="leg-title delivery">Livrare</span>
          <input
            placeholder="Destinatar"
            value={form.recipientName}
            onChange={(e) => set({ recipientName: e.target.value })}
          />
          <select value={form.deliveryCity} onChange={(e) => set({ deliveryCity: e.target.value })}>
            {ORASE.map((city) => (
              <option key={city}>{city}</option>
            ))}
          </select>
          <input
            className="wide-field"
            placeholder="Adresă livrare"
            value={form.deliveryAddress}
            onChange={(e) => set({ deliveryAddress: e.target.value })}
          />
        </div>

        <div className="shipment-meta">
          <label className="input-suffix">
            <input
              type="number"
              value={form.weightKg}
              onChange={(e) => set({ weightKg: +e.target.value })}
            />
            <span>KG</span>
          </label>
          <input type="time" value={form.timeStart} onChange={(e) => set({ timeStart: e.target.value })} />
          <input type="time" value={form.timeEnd} onChange={(e) => set({ timeEnd: e.target.value })} />
          <select value={form.priority} onChange={(e) => set({ priority: e.target.value })}>
            {PRIORITATI.map((priority) => (
              <option key={priority}>{priority}</option>
            ))}
          </select>
          <label className="input-suffix">
            <input
              type="number"
              placeholder="auto"
              value={form.tariffRon}
              onChange={(e) => set({ tariffRon: +e.target.value })}
            />
            <span>RON tarif</span>
          </label>
          <button type="button" className="small-primary inline-action" onClick={geocodeBoth} disabled={busy}>
            <MapPin size={15} />
            Verifică adresele
          </button>
        </div>
      </div>

      {status && <div className="geocode-status">{status}</div>}

      <div className="shipment-search">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Caută după cod colet, expeditor, destinatar sau oraș..."
        />
        <span>
          {list.length} / {allShipments.length} colete
          {list.length > 120 ? ' · afișate primele 120' : ''}
        </span>
      </div>

      <table>
        <thead>
          <tr>
            <th>Cod colet</th>
            <th>Expeditor → Destinatar</th>
            <th>Ridicare</th>
            <th>Livrare</th>
            <th>Greutate</th>
            <th>Interval</th>
            <th>Prioritate</th>
            <th>Tarif</th>
            <th></th>
          </tr>
        </thead>

        <tbody>
          {list.slice(0, 120).map((shipment) => (
            <tr key={shipment.id}>
              <td><span className="code-cell">{shipment.code}</span></td>
              <td>
                <b>{shipment.senderName}</b>
                <span className="route-arrow">
                  <ArrowRight size={13} /> {shipment.recipientName}
                </span>
              </td>
              <td>
                {shipment.pickupCity}
                <span className="hub-tag">{shipment.originHubId}</span>
              </td>
              <td>
                {shipment.deliveryCity}
                <span className="hub-tag">{shipment.destHubId}</span>
              </td>
              <td>{shipment.weightKg} kg</td>
              <td>{shipment.timeWindow}</td>
              <td>
                <span className="tag">{shipment.priority}</span>
              </td>
              <td>{Number(shipment.tariffRon).toFixed(2)} RON</td>
              <td>
                <button
                  className="danger"
                  onClick={() => callApi(`/shipments/${shipment.id}`, 'DELETE')}
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
