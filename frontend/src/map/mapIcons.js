import L from 'leaflet';

function createDivIcon(className, html) {
  return L.divIcon({
    className,
    html,
    iconSize: [38, 38],
    iconAnchor: [19, 19],
    popupAnchor: [0, -18],
  });
}

export function orderIcon(label, color, active) {
  return createDivIcon(
    `order-marker ${active ? 'active-order-marker' : ''}`,
    `<span style="border-color:${color};background:${color}">${label}</span>`
  );
}

export function depotMarker(label) {
  return createDivIcon('depot-marker', `<span>${label}</span>`);
}
