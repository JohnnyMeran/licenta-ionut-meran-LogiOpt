import { useEffect, useState } from 'react';
import { Route } from 'lucide-react';
import { MapContainer, Marker, Polyline, Popup, TileLayer } from 'react-leaflet';

import { CULORI_RUTE } from '../config/constants';
import EmptyHint from '../components/EmptyHint';
import FitMapToScenario from './FitMapToScenario';
import { depotMarker, orderIcon } from './mapIcons';
import { fallbackCoordinates, fetchRoadGeometry } from './routing';

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

  useEffect(() => {
    let cancelled = false;

    async function loadRoads() {
      if (!routes?.length) return;

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
        setRoutingStatus(
          'Traseele sunt afișate pe drumuri reale prin OpenStreetMap/OSRM. Dacă OSRM cade, aplicația folosește fallback local.'
        );
      }
    }

    loadRoads();

    return () => {
      cancelled = true;
    };
  }, [routes]);

  if (!scenario) {
    return <EmptyHint text="Se încarcă harta logistică..." />;
  }

  const visibleRoutes =
    selectedRoute === 'ALL'
      ? routes
      : routes.filter((route) => route.vehicleCode === selectedRoute);

  const centerDepot = scenario.depot || scenario.depots?.[0];
  const center = [centerDepot.latitude, centerDepot.longitude];

  return (
    <div className="real-leaflet-map">
      <MapContainer
        center={center}
        zoom={11}
        scrollWheelZoom
        className="leaflet-shell"
      >
        <TileLayer
          attribution="&copy; OpenStreetMap contributors"
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />

        <FitMapToScenario scenario={scenario} geometries={geometries} />

        {visibleRoutes.map((route) => {
          const idx = routes.findIndex(
            (item) => item.vehicleCode === route.vehicleCode
          );

          const color = CULORI_RUTE[idx % CULORI_RUTE.length];
          const positions =
            geometries[route.vehicleCode] || fallbackCoordinates(route);

          return (
            <Polyline
              key={route.vehicleCode}
              positions={positions}
              pathOptions={{
                color,
                weight: 6,
                opacity: 0.9,
              }}
              eventHandlers={{
                click: () => onSelectRoute(route.vehicleCode),
              }}
            />
          );
        })}

        {scenario.depots.map((depot, index) => (
          <Marker
            key={depot.id}
            position={[depot.latitude, depot.longitude]}
            icon={depotMarker(`D${index + 1}`)}
          >
            <Popup>
              <b>{depot.name}</b>
              <br />
              Produse: {depot.products.join(', ')}
            </Popup>
          </Marker>
        ))}

        {scenario.orders.map((order) => {
          const belongsTo = routes.find((route) =>
            route.stops.some(
              (stop) =>
                stop.stopType === 'DELIVERY' &&
                Number(stop.orderId) === Number(order.id)
            )
          );

          if (
            !belongsTo ||
            (selectedRoute !== 'ALL' && belongsTo.vehicleCode !== selectedRoute)
          ) {
            return null;
          }

          const stop = belongsTo.stops.find(
            (item) =>
              item.stopType === 'DELIVERY' &&
              Number(item.orderId) === Number(order.id)
          );

          const idx = routes.findIndex(
            (route) => route.vehicleCode === belongsTo.vehicleCode
          );

          const color = CULORI_RUTE[idx % CULORI_RUTE.length];

          return (
            <Marker
              key={order.id}
              position={[order.latitude, order.longitude]}
              icon={orderIcon(
                stop?.sequence || '?',
                color,
                selectedRoute === belongsTo.vehicleCode
              )}
              eventHandlers={{
                click: () => onSelectRoute(belongsTo.vehicleCode),
              }}
            >
              <Popup>
                <b>
                  Livrarea {stop?.sequence || '?'} - Comanda #{order.id}
                </b>
                <br />
                {order.customerName}
                <br />
                {order.address}
                <br />
                Produs: {order.requiredProduct}
                <br />
                Interval: {order.timeWindow}
                <br />
                Camion: {belongsTo.vehicleCode}
              </Popup>
            </Marker>
          );
        })}

        {visibleRoutes.flatMap((route) =>
          route.stops
            .filter((stop) => stop.stopType === 'DEPOT_LOAD')
            .map((stop, index) => (
              <Marker
                key={`${route.vehicleCode}-reload-${index}`}
                position={[stop.latitude, stop.longitude]}
                icon={depotMarker('Î')}
              >
                <Popup>
                  <b>{stop.customerName}</b>
                  <br />
                  {stop.address}
                  <br />
                  Camion: {route.vehicleCode}
                  <br />
                  Produse disponibile: {stop.requiredProduct}
                </Popup>
              </Marker>
            ))
        )}
      </MapContainer>

      <div className="map-note">
        <Route size={16} />
        {routingStatus}
      </div>
    </div>
  );
}
