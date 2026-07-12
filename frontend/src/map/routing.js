// Toate rutele sunt tur-retur din nodul de plecare: dubele pleacă și se întorc la hub,
// iar milk-run-urile de linehaul pleacă din hub-ul central, ating mai multe hub-uri și revin.
function routePoints(route) {
  const stops = (route?.stops || []).filter(
    (stop) => Number.isFinite(stop?.latitude) && Number.isFinite(stop?.longitude)
  );
  if (!route?.depot) return stops;
  return [route.depot, ...stops, route.depot];
}

export function fallbackCoordinates(route) {
  return routePoints(route).map((point) => [point.latitude, point.longitude]);
}

export async function fetchRoadGeometry(route) {
  const points = routePoints(route);

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

// Geometrie pe șosea pentru o listă simplă de puncte [lat, lon] (folosită la urmărirea coletului).
export async function fetchPathGeometry(points) {
  if (!points || points.length < 2) return points || [];

  const coords = points.map(([lat, lon]) => `${lon},${lat}`).join(';');
  const url = `https://router.project-osrm.org/route/v1/driving/${coords}?overview=full&geometries=geojson`;

  const res = await fetch(url);
  if (!res.ok) throw new Error('OSRM indisponibil');

  const json = await res.json();
  return json.routes?.[0]?.geometry?.coordinates?.map(([lng, lat]) => [lat, lng]) || points;
}
