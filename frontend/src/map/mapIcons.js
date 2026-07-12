import L from 'leaflet';

import { CULORI_STOP } from '../config/constants';

function createDivIcon(className, html, size = 34) {
  return L.divIcon({
    className,
    html,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
    popupAnchor: [0, -size / 2 + 2],
  });
}

// Marker pentru o oprire de curier: ridicare (chihlimbar) sau livrare (verde).
export function stopIcon(label, type, active) {
  const color = CULORI_STOP[type] || '#38bdf8';
  const shape = type === 'PICKUP' ? 'stop-pickup' : 'stop-delivery';
  return createDivIcon(
    `order-marker ${shape} ${active ? 'active-order-marker' : ''}`,
    `<span style="border-color:${color};background:${color}">${label}</span>`
  );
}

export function hubMarker(label) {
  return createDivIcon('depot-marker hub-marker', `<span>${label}</span>`, 40);
}
