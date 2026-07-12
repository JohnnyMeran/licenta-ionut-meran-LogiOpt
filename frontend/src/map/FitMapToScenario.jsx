import { useEffect, useMemo } from 'react';
import { useMap } from 'react-leaflet';

// Harta se încadrează pe traseele VIZIBILE, nu pe tot scenariul. Încadrarea pe toate hub-urile și toate coletele
// din țară făcea ca filtrul pe oraș și selectarea unui vehicul să nu schimbe niciodată imaginea: zoom-ul rămânea
// mereu pe toată România. Doar dacă nu există niciun traseu vizibil se cade înapoi pe rețeaua întreagă.
function routeCoords(routes, geometries) {
  return (routes || []).flatMap((route) => {
    const geometry = geometries?.[route.vehicleCode];
    if (geometry?.length) return geometry;

    const depot = route.depot ? [[route.depot.latitude, route.depot.longitude]] : [];
    const stops = (route.stops || []).map((stop) => [stop.latitude, stop.longitude]);
    return [...depot, ...stops];
  });
}

export default function FitMapToScenario({ scenario, geometries, routes }) {
  const map = useMap();

  const coords = useMemo(() => {
    if (!scenario) return [];

    const visible = routeCoords(routes, geometries);
    if (visible.length > 1) return visible;

    return [
      ...(scenario.depots || []).map((depot) => [depot.latitude, depot.longitude]),
      ...(scenario.shipments || []).flatMap((shipment) => [
        [shipment.pickupLat, shipment.pickupLon],
        [shipment.deliveryLat, shipment.deliveryLon],
      ]),
    ];
  }, [scenario, geometries, routes]);

  // Amprentă textuală: `coords` este un array nou la fiecare randare, iar un efect dependent de el ar reîncadra
  // harta continuu, anulând orice pan/zoom făcut de utilizator.
  const signature = coords.length ? `${coords.length}:${coords[0]}:${coords[coords.length - 1]}` : '';

  useEffect(() => {
    if (coords.length > 1) {
      map.fitBounds(coords, { padding: [30, 30] });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signature, map]);

  return null;
}
