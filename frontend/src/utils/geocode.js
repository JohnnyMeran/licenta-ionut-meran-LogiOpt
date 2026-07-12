// Geocodare pe toată România (nu mai e restrânsă la București).
export async function geocodeAddress(address, city = '') {
  const query = [address, city, 'România'].filter(Boolean).join(', ');

  const url = `https://nominatim.openstreetmap.org/search?format=json&limit=1&addressdetails=1&countrycodes=ro&q=${encodeURIComponent(
    query
  )}`;

  const res = await fetch(url, {
    headers: {
      Accept: 'application/json',
    },
  });

  if (!res.ok) {
    throw new Error('Serviciul de geocodare nu răspunde');
  }

  const results = await res.json();
  const first = results?.[0];

  if (!first) {
    throw new Error(`Adresa nu a fost găsită${city ? ` în ${city}` : ''}`);
  }

  return {
    latitude: Number(first.lat),
    longitude: Number(first.lon),
    label: first.display_name,
  };
}
