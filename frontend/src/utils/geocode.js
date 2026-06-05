export async function geocodeAddressInBucharest(address) {
  const query = `${address}, București, România`;

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
    throw new Error('Adresa nu a fost găsită în București');
  }

  return {
    latitude: Number(first.lat),
    longitude: Number(first.lon),
    label: first.display_name,
  };
}
