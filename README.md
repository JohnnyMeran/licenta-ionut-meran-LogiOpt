# Licență – Automatizarea proceselor logistice

Conținut ZIP:
- `document/LICENTA_Automatizare_Logistica.docx` – document Word actualizat.
- `frontend/` – React + Leaflet + Recharts.
- `backend/` – Java Spring Boot + OptaPlanner vehicle routing.

Modificări incluse în această versiune:
- varianta reală folosește flota existentă și depozitul inițial al camionului;
- varianta ipotetică poate porni camioane din orice depozit care are produsul cerut;
- timpul total este afișat ca: condus + livrare/încărcare + pauză;
- setările modificabile sunt doar: timp solver real, timp solver ipotetic și preț combustibil;
- prețul combustibilului și timpii solverului se trimit în backend și afectează următoarea optimizare;
- harta folosește OpenStreetMap + OSRM pentru trasee pe șosea.

Pornire backend:
```bash
cd backend
mvn spring-boot:run
```

Pornire frontend:
```bash
cd frontend
npm install
npm run dev
```

## Update: varianta ipotetică cu flotă virtuală nelimitată

Varianta ipotetică nu mai depinde de lista reală de camioane. Backend-ul generează automat camioane virtuale candidate pentru depozitele care au produsele cerute de comenzi. OptaPlanner poate folosi câte dintre aceste camioane dorește, iar camioanele nefolosite nu apar în hartă/statistici.

Concret, dacă în scenariul real există 4 camioane, dar soluția mai bună are nevoie de 5, varianta ipotetică poate afișa 5 rute ipotetice. Acest lucru permite comparația corectă între resursele reale și limita superioară obținută când resursele nu sunt constrângerea principală.

## Update final: timp solver 60s și cost salarial

- Timpul implicit pentru solverul real și cel ipotetic este acum 60 de secunde.
- În Setări se poate modifica separat timpul pentru solverul real și pentru solverul ipotetic.
- În Setări se poate modifica prețul combustibilului și costul zilnic al unui șofer.
- Costul salarial intră în costul total al rutei doar pentru camioanele folosite efectiv.
- Solverul real poate decide să nu folosească toate camioanele disponibile dacă economia de timp/combustibil nu justifică încă un șofer plătit.
- Solverul ipotetic poate genera resurse virtuale, dar este penalizat pentru fiecare camion folosit, ca să nu folosească 10-11 camioane doar pentru o economie mică de kilometri.

Valoarea implicită folosită pentru costul șoferului este 300 RON/zi, pornind de la o estimare de aproximativ 6.000 RON/lună împărțită la 20 de zile lucrătoare.

## Deploy Docker / Nginx

Pentru deploy pe server se folosește `docker-compose.prod.yml` din root. Containerele se numesc `logiopt-frontend` și `logiopt-backend`, iar porturile host sunt legate doar pe `127.0.0.1`:

- frontend: `127.0.0.1:3010 -> 80`
- backend: `127.0.0.1:8090 -> 8080`

Configul nginx pentru domeniul `licenta-meran-ionut.site` este în `deploy/nginx/licenta-meran-ionut.site.conf`.
