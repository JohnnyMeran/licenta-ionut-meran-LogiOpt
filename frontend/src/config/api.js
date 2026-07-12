// Cale relativă: în producție nginx (containerul de frontend, apoi cel de pe domeniu) proxează /api/ către
// backend, iar în dev proxy-ul din vite.config.js o trimite la http://localhost:8090. O adresă absolută de tip
// http://localhost:8090 ar merge doar dacă site-ul e deschis chiar de pe mașina care rulează containerele.
export const API = import.meta.env.VITE_API_URL || '/api';
