import { useEffect, useMemo, useState } from 'react';
import { Route } from 'lucide-react';
import { MapContainer, Marker, Polyline, Popup, TileLayer } from 'react-leaflet';

import { CULORI_RUTE } from '../config/constants';
import EmptyHint from '../components/EmptyHint';
import FitMapToScenario from './FitMapToScenario';
import { hubMarker, stopIcon } from './mapIcons';
import { fallbackCoordinates, fetchRoadGeometry } from './routing';

const ROUTING_DONE =
  'Trasee pe drumuri reale prin OpenStreetMap/OSRM. Linia întreruptă = linehaul între hub-uri. Dacă OSRM cade, se folosește fallback local.';

// Amprenta traseelor: coduri + numărul și ordinea opririlor. `routes` este un array nou la fiecare randare a
// Dashboard-ului (vine dintr-un .filter), deci un efect care depinde de el ar reporni tot lanțul de cereri OSRM
// la fiecare click. Amprenta se schimbă doar când traseele chiar s-au schimbat.
function routesSignature(routes) {
  return (routes || [])
    .map((r) => `${r.vehicleCode}:${(r.stops || []).map((s) => s.sequence).join('.')}`)
    .join('|');
}

export default function RoutesMap({
  scenario,
  selectedRoute,
  onSelectRoute,
  routes,
}) {
  const [geometries, setGeometries] = useState({});
  const [routingStatus, setRoutingStatus] = useState(
    'Se calculează traseele pe șosea...'
  );

  const signature = useMemo(() => routesSignature(routes), [routes]);

  useEffect(() => {
    let cancelled = false;

    async function loadRoads() {
      // Geometriile vechi se șterg imediat: sunt indexate după codul vehiculului, iar planul inițial și cel
      // optimizat folosesc aceleași coduri (DUBA-01...). Păstrate, harta ar desena traseul planului vechi
      // pentru rutele noi în tot intervalul cât durează recalcularea.
      setGeometries({});

      if (!routes?.length) {
        setRoutingStatus('Nu există trasee de afișat pentru filtrul curent.');
        return;
      }

      setRoutingStatus('Se calculează traseele pe șosea...');

      const result = {};

      for (const route of routes) {
        try {
          result[route.vehicleCode] = await fetchRoadGeometry(route);
        } catch {
          result[route.vehicleCode] = fallbackCoordinates(route);
        }
      }

      if (!cancelled) {
        setGeometries(result);
        setRoutingStatus(ROUTING_DONE);
      }
    }

    loadRoads();

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signature]);

  if (!scenario) {
    return <EmptyHint text="Se încarcă harta rețelei..." />;
  }

  const visibleRoutes =
    selectedRoute === 'ALL'
      ? routes
      : routes.filter((route) => route.vehicleCode === selectedRoute);

  return (
    <div className="real-leaflet-map">
      <MapContainer
        center={[45.9, 25.0]}
        zoom={7}
        scrollWheelZoom
        className="leaflet-shell"
      >
        <TileLayer
          attribution="&copy; OpenStreetMap contributors"
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />

        <FitMapToScenario
          scenario={scenario}
          geometries={geometries}
          routes={visibleRoutes}
        />

        {visibleRoutes.map((route) => {
          const idx = routes.findIndex(
            (item) => item.vehicleCode === route.vehicleCode
          );

          const color = CULORI_RUTE[idx % CULORI_RUTE.length];
          const positions =
            geometries[route.vehicleCode] || fallbackCoordinates(route);
          const isLineHaul = route.kind === 'LINEHAUL';

          return (
            <Polyline
              key={route.vehicleCode}
              positions={positions}
              pathOptions={{
                color: isLineHaul ? '#a78bfa' : color,
                weight: isLineHaul ? 4 : 6,
                opacity: 0.9,
                dashArray: isLineHaul ? '10 10' : undefined,
              }}
              eventHandlers={{
                click: () => onSelectRoute(route.vehicleCode),
              }}
            />
          );
        })}

        {(scenario.depots || []).map((hub, index) => (
          <Marker
            key={hub.id}
            position={[hub.latitude, hub.longitude]}
            icon={hubMarker(`H${index + 1}`)}
          >
            <Popup>
              <b>{hub.name}</b>
              <br />
              Oraș: {hub.city}
              <br />
              Acoperire: {(hub.coverage || []).join(', ')}
            </Popup>
          </Marker>
        ))}

        {visibleRoutes.flatMap((route) => {
          const idx = routes.findIndex(
            (item) => item.vehicleCode === route.vehicleCode
          );
          const active = selectedRoute === route.vehicleCode;

          return (route.stops || [])
            .filter((stop) => stop.stopType === 'PICKUP' || stop.stopType === 'DELIVERY')
            .map((stop) => (
              <Marker
                key={`${route.vehicleCode}-${stop.stopType}-${stop.sequence}`}
                position={[stop.latitude, stop.longitude]}
                icon={stopIcon(stop.sequence, stop.stopType, active)}
                eventHandlers={{
                  click: () => onSelectRoute(route.vehicleCode),
                }}
              >
                <Popup>
                  <b>
                    {stop.stopType === 'PICKUP' ? 'Ridicare' : 'Livrare'} #{stop.sequence}
                    {stop.shipmentId ? ` · colet ${stop.shipmentId}` : ''}
                  </b>
                  <br />
                  {stop.name}
                  <br />
                  {stop.address}
                  <br />
                  {stop.city} · {stop.timeWindow}
                  <br />
                  Vehicul: {route.vehicleCode}
                </Popup>
              </Marker>
            ));
        })}
      </MapContainer>

      <div className="map-note">
        <Route size={16} />
        {routingStatus}
      </div>
    </div>
  );
}
