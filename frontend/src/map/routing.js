export function fallbackCoordinates(route) {
  const coords = [[route.depot.latitude, route.depot.longitude]];

  route.stops.forEach((stop) => {
    coords.push([stop.latitude, stop.longitude]);
  });

  coords.push([route.depot.latitude, route.depot.longitude]);

  return coords;
}

export async function fetchRoadGeometry(route) {
  const points = [route.depot, ...route.stops, route.depot];

  const coords = points
    .map((point) => `${point.longitude},${point.latitude}`)
    .join(';');

  const url = `https://router.project-osrm.org/route/v1/driving/${coords}?overview=full&geometries=geojson`;

  const res = await fetch(url);

  if (!res.ok) {
    throw new Error('OSRM indisponibil');
  }

  const json = await res.json();

  return (
    json.routes?.[0]?.geometry?.coordinates?.map(([lng, lat]) => [lat, lng]) ||
    fallbackCoordinates(route)
  );
}
