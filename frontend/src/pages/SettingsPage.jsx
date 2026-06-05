import { useEffect, useState } from 'react';

export default function SettingsPage({ scenario, callApi }) {
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
