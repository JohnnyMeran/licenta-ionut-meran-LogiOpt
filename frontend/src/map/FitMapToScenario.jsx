import { useEffect } from 'react';
import { useMap } from 'react-leaflet';

export default function FitMapToScenario({ scenario, geometries }) {
  const map = useMap();

  useEffect(() => {
    if (!scenario) return;

    const coords = [
      ...scenario.depots.map((depot) => [depot.latitude, depot.longitude]),
      ...scenario.orders.map((order) => [order.latitude, order.longitude]),
      ...Object.values(geometries).flat(),
    ];

    if (coords.length > 1) {
      map.fitBounds(coords, {
        padding: [28, 28],
      });
    }
  }, [scenario, geometries, map]);

  return null;
}
