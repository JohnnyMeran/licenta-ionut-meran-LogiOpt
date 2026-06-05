import { AlertTriangle } from 'lucide-react';

export default function ReportsPage({ stats }) {
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
    ['Cost economisit ipotetic', `${stats.hypotheticalCostSavedRon || 0} RON`],
  ];

  return (
    <div className="card report-card">
      <div className="card-title">
        <h2>Rapoarte</h2>
        <span>inițial / real / ipotetic</span>
      </div>

      {rows.map(([label, value]) => (
        <div className="report-row" key={label}>
          <span>{label}</span>
          <b>{value}</b>
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
