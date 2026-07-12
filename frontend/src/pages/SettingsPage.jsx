import { useEffect, useState } from 'react';

const DEFAULTS = {
  realSolverSeconds: 20,
  hypotheticalSolverSeconds: 15,
  fuelPriceRonPerLiter: 7.45,
  driverDailySalaryRon: 300,
  vanPurchasePriceRon: 180000,
  truckPurchasePriceRon: 420000,
  vehicleUsefulLifeYears: 8,
  residualValuePercent: 20,
  workingDaysPerYear: 260,
  fleetReservePercent: 15,
};

const NUMERIC_FIELDS = Object.keys(DEFAULTS);

// Amortizare liniară: (preț − valoare reziduală) / (ani de viață × zile lucrătoare pe an).
function dailyAmortization(price, form) {
  const years = Math.max(1, Number(form.vehicleUsefulLifeYears) || 1);
  const days = Math.max(1, Number(form.workingDaysPerYear) || 1);
  const residual = (Number(price) || 0) * (Number(form.residualValuePercent) || 0) / 100;
  return Math.max(0, (Number(price) || 0) - residual) / (years * days);
}

const ron = (value) => `${value.toFixed(2)} RON`;

export default function SettingsPage({ scenario, callApi }) {
  const saved = scenario?.settings;
  const [form, setForm] = useState({ ...DEFAULTS, ...(saved || {}) });

  // Un singur dep serializat: lista de câmpuri e lungă, iar un array de dependențe construit din ea
  // ar fi ușor de desincronizat la fiecare setare nouă adăugată.
  const savedKey = JSON.stringify(saved || {});
  useEffect(() => {
    setForm({ ...DEFAULTS, ...(saved || {}) });
  }, [savedKey]);

  const set = (key) => (event) => setForm({ ...form, [key]: event.target.value });

  const save = () => {
    const payload = {};
    NUMERIC_FIELDS.forEach((key) => {
      payload[key] = +form[key];
    });
    callApi('/settings', 'PUT', payload);
  };

  const vanDaily = dailyAmortization(form.vanPurchasePriceRon, form);
  const truckDaily = dailyAmortization(form.truckPurchasePriceRon, form);
  const ownedVans = scenario?.vehicles?.filter((v) => v.kind === 'VAN').length || 0;
  const ownedTrucks = scenario?.vehicles?.filter((v) => v.kind === 'LINEHAUL').length || 0;
  const fleetDaily = ownedVans * vanDaily + ownedTrucks * truckDaily;

  return (
    <div className="settings-page">
      <div className="card">
        <div className="card-title">
          <div>
            <h2>Setări simulare</h2>
            <span>valorile se trimit în backend și afectează următoarea optimizare</span>
          </div>

          <button className="small-primary" onClick={save}>
            Salvează setările
          </button>
        </div>

        <div className="settings-grid">
          <label>
            Limită timp solver real, secunde
            <input type="number" min="5" max="300" value={form.realSolverSeconds} onChange={set('realSolverSeconds')} />
          </label>

          <label>
            Limită timp solver ipotetic, secunde
            <input type="number" min="5" max="300" value={form.hypotheticalSolverSeconds} onChange={set('hypotheticalSolverSeconds')} />
          </label>

          <label>
            Preț combustibil, RON/L
            <input type="number" min="1" max="30" step="0.01" value={form.fuelPriceRonPerLiter} onChange={set('fuelPriceRonPerLiter')} />
          </label>

          <label>
            Cost șofer / zi, RON
            <input type="number" min="0" max="2000" step="10" value={form.driverDailySalaryRon} onChange={set('driverDailySalaryRon')} />
          </label>
        </div>

        <p className="micro-copy settings-note">
          Sursa hărții rămâne intern fixă: OpenStreetMap + OSRM. Regulile legale sunt constrângeri ale
          modelului, nu setări modificabile din interfață.
        </p>
      </div>

      <div className="card">
        <div className="card-title">
          <div>
            <h2>Amortizarea flotei</h2>
            <span>prețul de achiziție, durata de viață și rezerva de flotă alimentează rapoartele financiare</span>
          </div>
        </div>

        <div className="settings-grid">
          <label>
            Preț achiziție dubă, RON
            <input type="number" min="20000" max="1000000" step="1000" value={form.vanPurchasePriceRon} onChange={set('vanPurchasePriceRon')} />
          </label>

          <label>
            Preț achiziție camion, RON
            <input type="number" min="50000" max="3000000" step="1000" value={form.truckPurchasePriceRon} onChange={set('truckPurchasePriceRon')} />
          </label>

          <label>
            Durată de viață, ani
            <input type="number" min="1" max="25" value={form.vehicleUsefulLifeYears} onChange={set('vehicleUsefulLifeYears')} />
          </label>

          <label>
            Valoare reziduală, %
            <input type="number" min="0" max="80" step="1" value={form.residualValuePercent} onChange={set('residualValuePercent')} />
          </label>

          <label>
            Zile lucrătoare / an
            <input type="number" min="100" max="365" value={form.workingDaysPerYear} onChange={set('workingDaysPerYear')} />
          </label>

          <label>
            Rezervă flotă, %
            <input type="number" min="0" max="100" step="1" value={form.fleetReservePercent} onChange={set('fleetReservePercent')} />
          </label>
        </div>

        <div className="amort-preview">
          <div>
            <span>Amortizare dubă / zi</span>
            <b>{ron(vanDaily)}</b>
          </div>
          <div>
            <span>Amortizare camion / zi</span>
            <b>{ron(truckDaily)}</b>
          </div>
          <div>
            <span>Amortizare flotă / zi ({ownedVans} dube + {ownedTrucks} camioane)</span>
            <b>{ron(fleetDaily)}</b>
          </div>
        </div>

        <p className="micro-copy settings-note">
          Amortizarea liniară = (preț achiziție − valoare reziduală) / (ani × zile lucrătoare). Se plătește
          pentru fiecare vehicul deținut, indiferent dacă iese sau nu pe traseu — motiv pentru care apare
          separat în Rapoarte, iar recomandarea de flotă o folosește ca argument principal.
        </p>
      </div>
    </div>
  );
}
