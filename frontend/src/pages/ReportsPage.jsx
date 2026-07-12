import {
  AlertTriangle,
  ArrowDownCircle,
  ArrowUpCircle,
  Boxes,
  CheckCircle2,
  Clock3,
  Coins,
  Fuel,
  Lightbulb,
  PackageCheck,
  PiggyBank,
  Route,
  TrendingUp,
  Truck,
  Users,
  Wallet,
  Wrench,
} from 'lucide-react';
import { chartTheme } from '../config/theme';
import {
  Area,
  Bar,
  CartesianGrid,
  ComposedChart,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

const safe = (value) => Number(value || 0);
const round = (value, digits = 2) => Number(safe(value).toFixed(digits));
const percent = (value) => `${round(value)}%`;
const km = (value) => `${round(value)} km`;
const ron = (value) => `${round(value)} RON`;
const ron0 = (value) => `${Math.round(safe(value)).toLocaleString('ro-RO')} RON`;
const liters = (value) => `${round(value)} L`;
const minutes = (value) => `${Math.round(safe(value))} min`;
const signed = (value) => (value > 0 ? `+${value}` : `${value}`);

function routeDeliveries(route) {
  return (route?.stops || []).filter((stop) => stop.stopType === 'DELIVERY');
}
function routePickups(route) {
  return (route?.stops || []).filter((stop) => stop.stopType === 'PICKUP');
}
// Gradul de încărcare se raportează la vârful de încărcătură (peakLoadKg), nu la greutatea totală manipulată
// într-o zi (loadKg): duba livrează colete și ridică altele, deci nu cară niciodată tot ce atinge într-o zi.
// Valoarea NU se plafonează la 100: o depășire reală de capacitate trebuie să se vadă în raport, nu să fie
// ascunsă sub o bară verde plină.
function loadPercent(route) {
  if (!route?.capacityKg) return 0;
  const peak = safe(route.peakLoadKg ?? route.loadKg);
  return round((peak / safe(route.capacityKg)) * 100);
}
function loadTone(percent) {
  if (percent > 100) return 'vs-danger';
  if (percent >= 90) return 'vs-warn';
  return 'vs-ok';
}

function MetricCard({ icon: Icon, label, value, detail }) {
  return (
    <div className="report-metric">
      <div className="icon compact">
        <Icon size={22} />
      </div>
      <div>
        <span>{label}</span>
        <b>{value}</b>
        {detail && <small>{detail}</small>}
      </div>
    </div>
  );
}

function ComparisonRow({ label, initial, real, hypothetical, saved, percentValue }) {
  return (
    <tr>
      <th>{label}</th>
      <td>{initial}</td>
      <td>{real}</td>
      <td>{hypothetical}</td>
      <td>
        <b>{saved}</b>
        <span>{percentValue}</span>
      </td>
    </tr>
  );
}

// Chip de tip "−3 dube" / "+1 camion" / "0 camioane", colorat după sensul deciziei.
function DeltaChip({ icon: Icon, delta, singular, plural }) {
  const value = safe(delta);
  const tone = value < 0 ? 'cut' : value > 0 ? 'buy' : 'keep';
  const noun = Math.abs(value) === 1 ? singular : plural;
  return (
    <div className={`delta-chip ${tone}`}>
      <Icon size={18} />
      <b>{signed(value)}</b>
      <span>{noun}</span>
    </div>
  );
}

const ACTION_META = {
  REDUCE: { icon: ArrowDownCircle, label: 'Redu flota', tone: 'cut' },
  EXTINDE: { icon: ArrowUpCircle, label: 'Majorează flota', tone: 'buy' },
  REECHILIBREAZĂ: { icon: Wrench, label: 'Reechilibrează flota', tone: 'buy' },
  MENȚINE: { icon: CheckCircle2, label: 'Menține flota', tone: 'keep' },
};

export default function ReportsPage({ scenario, stats, history = [], theme }) {
  const chart = chartTheme(theme);
  const allRoutes = scenario?.routes || [];
  const regionalRoutes = allRoutes.filter(
    (r) => r.kind === 'REGIONAL' && routeDeliveries(r).length + routePickups(r).length > 0
  );
  const lineHaulRoutes = allRoutes.filter((r) => r.kind === 'LINEHAUL');
  // Câte curse de camion cere planul neoptimizat, față de câte camioane deține de fapt compania.
  const baselineTruckTrips = (scenario?.initialRoutes || []).filter((r) => r.kind === 'LINEHAUL').length;
  const ownedTrucks = (scenario?.vehicles || []).filter((v) => v.kind === 'LINEHAUL').length;
  const monthly = scenario?.monthlyReports || [];

  const monthlyChart = monthly.map((m) => ({
    name: m.label,
    Venit: safe(m.revenueRon),
    'Cost baseline': safe(m.baselineCostRon),
    'Cost LogiOpt': safe(m.optimizedCostRon),
    Amortizare: safe(m.amortizationRon),
    Profit: safe(m.profitRon),
  }));

  const totals = monthly.reduce(
    (acc, m) => ({
      revenue: acc.revenue + safe(m.revenueRon),
      saved: acc.saved + safe(m.savedByLogiOptRon),
      profit: acc.profit + safe(m.profitRon),
      profitBefore: acc.profitBefore + safe(m.profitBeforeAmortizationRon),
      amortization: acc.amortization + safe(m.amortizationRon),
      optimized: acc.optimized + safe(m.optimizedCostRon),
      shipments: acc.shipments + safe(m.shipments),
    }),
    { revenue: 0, saved: 0, profit: 0, profitBefore: 0, amortization: 0, optimized: 0, shipments: 0 }
  );

  const monthsCount = monthly.length || 1;
  const annualSaved = (totals.saved / monthsCount) * 12;
  const annualAmortization = (totals.amortization / monthsCount) * 12;

  const costScenarios = scenario?.costAnalysis?.scenarios || [];
  const rec = scenario?.costAnalysis?.recommendation;
  const costChart = costScenarios.map((s) => ({
    name: s.name,
    Combustibil: safe(s.breakdown.fuelRon),
    Șoferi: safe(s.breakdown.driverRon),
    Amortizare: safe(s.breakdown.amortizationRon),
    'Amortizare nefolosite': safe(s.breakdown.idleAmortizationRon),
    Service: safe(s.breakdown.serviceRon),
    Operațional: safe(s.breakdown.operationalRon),
  }));
  const costRows = [
    ['Combustibil', 'fuelRon'],
    ['Șoferi (salarii)', 'driverRon'],
    ['Amortizare vehicule rulate', 'amortizationRon'],
    ['Amortizare vehicule nefolosite', 'idleAmortizationRon'],
    ['Service / mentenanță', 'serviceRon'],
    ['Operațional (taxe drum)', 'operationalRon'],
  ];
  const realScenario = costScenarios.find((s) => s.name === 'Real')?.breakdown;
  const initialScenario = costScenarios.find((s) => s.name === 'Inițial')?.breakdown;
  const realTotal = safe(realScenario?.totalRon);
  const initialTotal = safe(initialScenario?.totalRon);

  const chartData = [
    { name: 'Inițial', Distanță: safe(stats.initialDistanceKm), Cost: safe(stats.initialCostRon), Timp: safe(stats.initialDurationMinutes) },
    { name: 'Real', Distanță: safe(stats.optimizedDistanceKm), Cost: safe(stats.optimizedCostRon), Timp: safe(stats.optimizedDurationMinutes) },
    { name: 'Ipotetic', Distanță: safe(stats.hypotheticalDistanceKm), Cost: safe(stats.hypotheticalCostRon), Timp: safe(stats.hypotheticalDurationMinutes) },
  ];

  const routeRows = regionalRoutes.map((route) => ({
    code: route.vehicleCode,
    driver: route.driverName,
    hub: route.depot?.city || '-',
    pickups: routePickups(route).length,
    deliveries: routeDeliveries(route).length,
    load: loadPercent(route),
    distance: route.distanceKm,
    duration: route.durationMinutes,
    cost: route.costRon,
  }));

  const historySummary = history.reduce(
    (sum, entry) => ({
      runs: sum.runs + 1,
      orders: sum.orders + safe(entry.orderCount),
      costSaved: sum.costSaved + safe(entry.statistics?.costSavedRon),
    }),
    { runs: 0, orders: 0, costSaved: 0 }
  );

  const action = ACTION_META[rec?.action] || ACTION_META.MENȚINE;
  const ActionIcon = action.icon;

  // Amortizarea zilnică se poate afișa și fără recomandare (înainte de optimizare): depinde doar de setări.
  const cfg = scenario?.settings;
  const dailyAmortization = (price) => {
    const years = Math.max(1, safe(cfg?.vehicleUsefulLifeYears) || 1);
    const days = Math.max(1, safe(cfg?.workingDaysPerYear) || 1);
    const residual = safe(price) * safe(cfg?.residualValuePercent) / 100;
    return Math.max(0, safe(price) - residual) / (years * days);
  };
  const vanDaily = safe(rec?.vanDailyAmortizationRon) || dailyAmortization(cfg?.vanPurchasePriceRon);
  const truckDaily = safe(rec?.truckDailyAmortizationRon) || dailyAmortization(cfg?.truckPurchasePriceRon);

  return (
    <div className="reports-page">
      <div className="report-hero card money-hero">
        <div>
          <span className="eyebrow">Impact financiar LogiOpt</span>
          <h2>Câți bani am făcut datorită LogiOpt</h2>
          <p>
            Optimizarea rutelor cu OptaPlanner + OSRM reduce costul operațional. Amortizarea flotei se scade
            separat: ea nu depinde de rute, ci de câte vehicule deții — de aceea profitul real apare abia după ea.
          </p>
        </div>
        <div className="report-score money">
          <span>Economisit în ultimele {monthsCount} luni</span>
          <b>{ron0(totals.saved)}</b>
          <small>≈ {ron0(annualSaved)} pe an · profit după amortizare {ron0(totals.profit)}</small>
        </div>
      </div>

      <section className="report-metrics-grid">
        <MetricCard icon={Coins} label={`Venit total (${monthsCount} luni)`} value={ron0(totals.revenue)} detail={`${Math.round(totals.shipments).toLocaleString('ro-RO')} colete livrate`} />
        <MetricCard icon={PiggyBank} label="Economisit cu LogiOpt" value={ron0(totals.saved)} detail={`${ron0(annualSaved)} estimat anual`} />
        <MetricCard icon={Wrench} label="Amortizare flotă" value={ron0(totals.amortization)} detail={`${ron0(annualAmortization)} pe an, indiferent de rute`} />
        <MetricCard icon={TrendingUp} label="Profit după amortizare" value={ron0(totals.profit)} detail={`${ron0(totals.profitBefore)} înainte de amortizare`} />
        <MetricCard icon={Wallet} label="Economie rularea curentă" value={ron(stats.moneySavedByLogiOptRon)} detail={`${percent(stats.distanceImprovementPercent)} distanță mai mică`} />
      </section>

      <section className="history-panel card">
        <div className="card-title">
          <div>
            <h2>Evoluție lunară (demo estimativ)</h2>
            <span>venit, cost fără optimizare, cost cu LogiOpt, amortizarea flotei și profitul rămas</span>
          </div>
        </div>

        <ResponsiveContainer width="100%" height={320}>
          <ComposedChart data={monthlyChart} margin={{ top: 10, right: 16, left: 0, bottom: 4 }}>
            <CartesianGrid stroke={chart.grid} vertical={false} />
            <XAxis dataKey="name" stroke={chart.axis} tickLine={false} />
            <YAxis stroke={chart.axis} tickLine={false} />
            <Tooltip
              contentStyle={{ background: chart.tooltipBg, border: chart.tooltipBorder, borderRadius: 12, color: chart.tooltipText }}
              formatter={(value) => `${Math.round(value).toLocaleString('ro-RO')} RON`}
            />
            <Legend />
            <Bar dataKey="Venit" fill="#38bdf8" radius={[6, 6, 0, 0]} maxBarSize={30} />
            <Bar dataKey="Cost baseline" fill="#64748b" radius={[6, 6, 0, 0]} maxBarSize={30} />
            <Bar dataKey="Cost LogiOpt" fill="#22c55e" radius={[6, 6, 0, 0]} maxBarSize={30} />
            <Bar dataKey="Amortizare" fill="#a78bfa" radius={[6, 6, 0, 0]} maxBarSize={30} />
            {/* O SINGURĂ serie pentru profit. Înainte erau două suprapuse pe aceeași valoare (o Area pentru banda
                colorată și o Line pentru traseu), iar fiecare își adăuga propria intrare — de aceea „Profit"
                apărea de două ori în tooltip și în legendă. Area desenează și banda, și linia, și punctele. */}
            <Area
              dataKey="Profit"
              type="monotone"
              fill="rgba(250, 204, 21, 0.18)"
              stroke="#facc15"
              strokeWidth={2}
              dot={{ r: 3 }}
            />
          </ComposedChart>
        </ResponsiveContainer>

        <table className="history-table">
          <thead>
            <tr>
              <th>Luna</th>
              <th>Colete</th>
              <th>Venit</th>
              <th>Cost fără LogiOpt</th>
              <th>Cost cu LogiOpt</th>
              <th>Economisit</th>
              <th>Amortizare flotă</th>
              <th>Profit după amortizare</th>
            </tr>
          </thead>
          <tbody>
            {monthly.map((m) => (
              <tr key={`${m.label}-${m.year}`}>
                <td>{m.label} {m.year}</td>
                <td>{m.shipments.toLocaleString('ro-RO')}</td>
                <td>{ron0(m.revenueRon)}</td>
                <td>{ron0(m.baselineCostRon)}</td>
                <td>{ron0(m.optimizedCostRon)}</td>
                <td><b className="pos">{ron0(m.savedByLogiOptRon)}</b></td>
                <td className="amort-cell">− {ron0(m.amortizationRon)}</td>
                <td><b className={safe(m.profitRon) >= 0 ? 'pos' : 'neg'}>{ron0(m.profitRon)}</b></td>
              </tr>
            ))}
            {monthly.length === 0 && (
              <tr>
                <td colSpan="8">Nu există rapoarte lunare.</td>
              </tr>
            )}
          </tbody>
        </table>
      </section>

      <section className="cost-analysis card">
        <div className="card-title">
          <div>
            <h2>Cât ar costa fiecare scenariu</h2>
            <span>cost zilnic complet: combustibil, șoferi, service, taxe și amortizarea flotei deținute</span>
          </div>
          <div className="cost-headline">
            <span>Economie Inițial → Real</span>
            <b>{ron0(initialTotal - realTotal)}</b>
          </div>
        </div>

        <div className="cost-analysis-grid">
          <ResponsiveContainer width="100%" height={300}>
            <ComposedChart data={costChart} margin={{ top: 10, right: 12, left: 0, bottom: 4 }}>
              <CartesianGrid stroke={chart.grid} vertical={false} />
              <XAxis dataKey="name" stroke={chart.axis} tickLine={false} />
              <YAxis stroke={chart.axis} tickLine={false} />
              <Tooltip
                contentStyle={{ background: chart.tooltipBg, border: chart.tooltipBorder, borderRadius: 12, color: chart.tooltipText }}
                formatter={(value) => `${Math.round(value).toLocaleString('ro-RO')} RON`}
              />
              <Legend />
              <Bar dataKey="Combustibil" stackId="c" fill="#f97316" maxBarSize={70} />
              <Bar dataKey="Șoferi" stackId="c" fill="#38bdf8" maxBarSize={70} />
              <Bar dataKey="Amortizare" stackId="c" fill="#a78bfa" maxBarSize={70} />
              <Bar dataKey="Amortizare nefolosite" stackId="c" fill="#7c3aed" maxBarSize={70} />
              <Bar dataKey="Service" stackId="c" fill="#f43f5e" maxBarSize={70} />
              <Bar dataKey="Operațional" stackId="c" fill="#22c55e" radius={[6, 6, 0, 0]} maxBarSize={70} />
            </ComposedChart>
          </ResponsiveContainer>

          <table className="cost-table">
            <thead>
              <tr>
                <th>Componentă cost</th>
                {costScenarios.map((s) => (
                  <th key={s.name}>{s.name}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {costRows.map(([label, key]) => (
                <tr key={key} className={key === 'idleAmortizationRon' ? 'cost-idle' : undefined}>
                  <th>{label}</th>
                  {costScenarios.map((s) => (
                    <td key={s.name}>{ron0(s.breakdown[key])}</td>
                  ))}
                </tr>
              ))}
              <tr className="cost-total">
                <th>Cost rulare / zi</th>
                {costScenarios.map((s) => (
                  <td key={s.name}><b>{ron0(s.breakdown.totalRon)}</b></td>
                ))}
              </tr>
              <tr className="cost-total ownership">
                <th>Cost real de proprietate / zi</th>
                {costScenarios.map((s) => (
                  <td key={s.name}><b>{ron0(s.breakdown.totalWithIdleRon)}</b></td>
                ))}
              </tr>
              <tr>
                <th>Vehicule folosite</th>
                {costScenarios.map((s) => (
                  <td key={s.name}>{s.breakdown.vansUsed} dube · {s.breakdown.trucksUsed} camioane</td>
                ))}
              </tr>
              <tr>
                <th>Vehicule nefolosite</th>
                {costScenarios.map((s) => (
                  <td key={s.name}>{s.breakdown.vansIdle} dube · {s.breakdown.trucksIdle} camioane</td>
                ))}
              </tr>
            </tbody>
          </table>
        </div>

        <p className="cost-note">
          <AlertTriangle size={16} />
          Optimizarea rutelor scade combustibilul, salariile și service-ul, dar amortizarea vehiculelor
          rămase în curte nu dispare: un vehicul deținut se depreciază și dacă nu iese pe traseu. Acesta este
          costul pe care îl atacă recomandarea de mai jos.
        </p>
      </section>

      {!rec && (
        <section className="fleet-reco card fleet-empty">
          <div className="card-title">
            <div>
              <h2 className="fleet-title">
                <Lightbulb size={22} />
                Recomandarea LogiOpt pentru flotă
              </h2>
              <span>cu câte vehicule ar trebui redusă sau majorată flota și de ce</span>
            </div>
          </div>
          <p>
            Recomandarea se calculează doar pe un plan optimizat: în planul neoptimizat fiecare spiță trimite
            un camion dus-întors, așa că dimensionarea flotei pe baza lui ar cere de câteva ori mai multe
            camioane decât în realitate. Apasă <b>Optimizează</b> în Panou, apoi revino aici.
          </p>
        </section>
      )}

      {rec && (
        <section className="fleet-reco card">
          <div className="card-title">
            <div>
              <h2 className="fleet-title">
                <Lightbulb size={22} />
                Recomandarea LogiOpt pentru flotă
              </h2>
              <span>cu câte vehicule ar trebui redusă sau majorată flota și de ce</span>
            </div>
          </div>

          <div className={`fleet-verdict ${action.tone}`}>
            <div className="fleet-verdict-head">
              <ActionIcon size={26} />
              <div>
                <span>{action.label}</span>
                <b>{rec.headline}</b>
              </div>
            </div>
            <div className="fleet-deltas">
              <DeltaChip icon={Boxes} delta={rec.vanDelta} singular="dubă" plural="dube" />
              <DeltaChip icon={Truck} delta={rec.truckDelta} singular="camion" plural="camioane" />
              <DeltaChip icon={Users} delta={rec.driverDelta} singular="șofer" plural="șoferi" />
            </div>
          </div>

          <div className="fleet-money">
            <div>
              <span>Amortizare irosită / zi</span>
              <b className="neg">{ron0(rec.idleAmortizationPerDayRon)}</b>
              <small>vehicule deținute, dar nefolosite</small>
            </div>
            <div>
              <span>Economie anuală dacă reduci</span>
              <b className="pos">{ron0(rec.annualSavingRon)}</b>
              <small>amortizare tăiată din costuri</small>
            </div>
            <div>
              <span>Cash din vânzarea surplusului</span>
              <b className="pos">{ron0(rec.resaleValueRon)}</b>
              <small>la valoarea reziduală configurată</small>
            </div>
            <div>
              <span>Investiție dacă majorezi</span>
              <b>{ron0(rec.investmentRon)}</b>
              <small>{ron0(rec.annualExtraAmortizationRon)} / an amortizare în plus</small>
            </div>
          </div>

          <div className="fleet-reco-cols">
            <div className="fleet-col">
              <span className="fleet-col-label">Flotă actuală</span>
              <div className="fleet-metric"><Boxes size={16} /><b>{rec.currentVans}</b> dube</div>
              <div className="fleet-metric"><Truck size={16} /><b>{rec.currentTrucks}</b> camioane</div>
              <div className="fleet-metric"><Users size={16} /><b>{rec.currentDrivers}</b> șoferi</div>
            </div>
            <div className="fleet-col used">
              <span className="fleet-col-label">Folosit (zi optimizată)</span>
              <div className="fleet-metric"><Boxes size={16} /><b>{rec.usedVans}</b> dube</div>
              <div className="fleet-metric"><Truck size={16} /><b>{rec.usedTrucks}</b> camioane</div>
              <div className="fleet-metric"><Users size={16} /><b>{rec.usedVans + rec.usedTrucks}</b> șoferi</div>
            </div>
            <div className="fleet-col required">
              <span className="fleet-col-label">Minim fizic necesar</span>
              <div className="fleet-metric"><Boxes size={16} /><b>{rec.requiredVans}</b> dube</div>
              <div className="fleet-metric"><Truck size={16} /><b>{rec.requiredTrucks}</b> camioane</div>
              <div className="fleet-metric muted">capacitate + zi legală</div>
            </div>
            <div className="fleet-col reco">
              <span className="fleet-col-label">Recomandat (+{round(rec.reservePercent, 0)}% rezervă)</span>
              <div className="fleet-metric"><Boxes size={16} /><b>{rec.recommendedVans}</b> dube</div>
              <div className="fleet-metric"><Truck size={16} /><b>{rec.recommendedTrucks}</b> camioane</div>
              <div className="fleet-metric"><Users size={16} /><b>{rec.recommendedDrivers}</b> șoferi</div>
            </div>
          </div>

          <div className="fleet-why">
            <h3>De ce recomandă LogiOpt asta</h3>
            <ol>
              {(rec.reasons || []).map((reason, index) => (
                <li key={index}>{reason}</li>
              ))}
            </ol>
          </div>

          <p className="fleet-note">{rec.note}</p>

          <table className="fleet-hub-table">
            <thead>
              <tr>
                <th>Hub</th>
                <th>Deținute</th>
                <th>Folosite</th>
                <th>Minim necesar</th>
                <th>Recomandat</th>
                <th>Decizie</th>
                <th>Încărcare</th>
                <th>Motiv</th>
              </tr>
            </thead>
            <tbody>
              {(rec.perHub || []).map((h) => (
                <tr key={h.city}>
                  <td><b>{h.city}</b></td>
                  <td>{h.vansOwned}</td>
                  <td>{h.vansUsed}</td>
                  <td>{h.vansRequired}</td>
                  <td><b className="pos">{h.vansRecommended}</b></td>
                  <td>
                    <span className={`hub-delta ${h.delta < 0 ? 'cut' : h.delta > 0 ? 'buy' : 'keep'}`}>
                      {signed(h.delta)}
                    </span>
                  </td>
                  <td>{percent(h.utilizationPercent)}</td>
                  <td className="hub-reason">{h.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      <section className="report-layout">
        <div className="card report-chart-card">
          <div className="card-title">
            <div>
              <h2>Comparație scenarii (rularea curentă)</h2>
              <span>Distanță, cost și timp total</span>
            </div>
          </div>
          <ResponsiveContainer width="100%" height={310}>
            <ComposedChart data={chartData}>
              <CartesianGrid stroke={chart.grid} />
              <XAxis dataKey="name" stroke={chart.axis} />
              <YAxis stroke={chart.axis} />
              <Tooltip contentStyle={{ background: chart.tooltipBg, border: chart.tooltipBorder, borderRadius: 12, color: chart.tooltipText }} />
              <Legend />
              <Bar dataKey="Distanță" fill="#60a5fa" radius={[6, 6, 0, 0]} />
              <Bar dataKey="Cost" fill="#34d399" radius={[6, 6, 0, 0]} />
              <Bar dataKey="Timp" fill="#fbbf24" radius={[6, 6, 0, 0]} />
            </ComposedChart>
          </ResponsiveContainer>
        </div>

        <div className="card report-card">
          <div className="card-title">
            <div>
              <h2>Indicatori principali</h2>
              <span>inițial / real / ipotetic</span>
            </div>
          </div>

          <p className="cost-note">
            <AlertTriangle size={16} />
            Ipoteticul folosește o flotă nelimitată, dar cu aceleași costuri, și pornește din soluția reală: este
            limita de cost, nu poate ieși mai slab decât realul. Dacă diferența e mică, asta e chiar concluzia —
            flota deținută nu mai este constrângerea care te ține pe loc. Zero peste tot = scenariul nu a fost rulat.
          </p>

          <table className="comparison-table">
            <tbody>
              <ComparisonRow label="Distanță" initial={km(stats.initialDistanceKm)} real={km(stats.optimizedDistanceKm)} hypothetical={km(stats.hypotheticalDistanceKm)} saved={km(stats.distanceSavedKm)} percentValue={percent(stats.distanceImprovementPercent)} />
              <ComparisonRow label="Timp total" initial={minutes(stats.initialDurationMinutes)} real={minutes(stats.optimizedDurationMinutes)} hypothetical={minutes(stats.hypotheticalDurationMinutes)} saved={minutes(stats.durationSavedMinutes)} percentValue={percent(stats.durationImprovementPercent)} />
              <ComparisonRow label="Timp mediu livrare" initial={minutes(stats.initialAverageDeliveryMinutes)} real={minutes(stats.optimizedAverageDeliveryMinutes)} hypothetical={minutes(stats.hypotheticalAverageDeliveryMinutes)} saved={minutes(stats.averageDeliverySavedMinutes)} percentValue={percent(stats.averageDeliveryImprovementPercent)} />
              <ComparisonRow label="Cost operațional" initial={ron(stats.initialCostRon)} real={ron(stats.optimizedCostRon)} hypothetical={ron(stats.hypotheticalCostRon)} saved={ron(stats.costSavedRon)} percentValue="economie reală" />
            </tbody>
          </table>
        </div>
      </section>

      <section className="report-layout compact-layout">
        <div className="card table-card">
          <div className="card-title">
            <div>
              <h2>Performanță dube regionale</h2>
              <span>ridicări + livrări după optimizare</span>
            </div>
          </div>
          <table className="route-performance-table">
            <thead>
              <tr>
                <th>Vehicul</th>
                <th>Hub</th>
                <th>Operațiuni</th>
                <th>Încărcare</th>
                <th>Distanță</th>
                <th>Timp</th>
                <th>Cost</th>
              </tr>
            </thead>
            <tbody>
              {routeRows.map((route) => (
                <tr key={route.code}>
                  <td>
                    <b>{route.code}</b>
                    <span>{route.driver}</span>
                  </td>
                  <td>{route.hub}</td>
                  <td>
                    {route.pickups} ridicări · {route.deliveries} livrări
                  </td>
                  <td>
                    <div className="load-cell">
                      <span>{route.load}%</span>
                      <div className={`vs-bar ${loadTone(route.load)}`}>
                        <i style={{ width: `${Math.min(100, route.load)}%` }} />
                      </div>
                    </div>
                  </td>
                  <td>{km(route.distance)}</td>
                  <td>{minutes(route.duration)}</td>
                  <td>{ron(route.cost)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="card report-insights">
          <div className="card-title">
            <div>
              <h2>Interpretare</h2>
              <span>ce înseamnă rezultatele</span>
            </div>
          </div>

          <div className="insight-row">
            <PackageCheck size={20} />
            <div>
              <b>Rețea națională</b>
              <span>{scenario?.shipments?.length || 0} colete pe {scenario?.depots?.length || 0} hub-uri, {regionalRoutes.length} dube și {lineHaulRoutes.length} transferuri linehaul.</span>
            </div>
          </div>
          <div className="insight-row">
            <Route size={20} />
            <div>
              <b>Linehaul între orașe</b>
              <span>{ron(lineHaulRoutes.reduce((s, r) => s + safe(r.costRon), 0))} cost consolidat pe {km(lineHaulRoutes.reduce((s, r) => s + safe(r.distanceKm), 0))}.</span>
            </div>
          </div>
          <div className="insight-row">
            <AlertTriangle size={20} />
            <div>
              <b>Cum se citește economia</b>
              <span>
                Planul neoptimizat trimite câte un camion dus-întors pe fiecare spiță: {baselineTruckTrips} curse
                pe zi, față de {lineHaulRoutes.length} după consolidare. Cu {ownedTrucks} camioane deținute,
                baseline-ul ar cere subcontractare — o parte din economie vine tocmai din eliminarea acestor curse,
                nu doar din scurtarea traseelor.
              </span>
            </div>
          </div>
          <div className="insight-row">
            <Wrench size={20} />
            <div>
              <b>Amortizare zilnică</b>
              <span>{ron(vanDaily)} per dubă și {ron(truckDaily)} per camion, calculată liniar din prețul de achiziție.</span>
            </div>
          </div>
          <div className="insight-row">
            <Clock3 size={20} />
            <div>
              <b>Timp mediu livrare</b>
              <span>{minutes(stats.optimizedAverageDeliveryMinutes)} real față de {minutes(stats.initialAverageDeliveryMinutes)} inițial.</span>
            </div>
          </div>
          <div className="insight-row">
            <Fuel size={20} />
            <div>
              <b>Combustibil economisit</b>
              <span>{liters(stats.fuelSavedLiters)} mai puțin față de planul neoptimizat.</span>
            </div>
          </div>

          <div className="notice">
            <AlertTriangle size={18} />
            Model: colet → hub origine → linehaul → hub destinație → livrare. Reguli
            legale de condus (9h/zi, pauză 45 min după 4h30, repaus 11h), capacitate
            vehicul și ferestre orare sunt constrângeri ale solverului OptaPlanner.
            Istoric local: {historySummary.runs} rulări, {ron(historySummary.costSaved)} economisit.
          </div>
        </div>
      </section>
    </div>
  );
}
