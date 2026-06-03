# Backend Spring Boot + OptaPlanner

Endpoint-uri principale:
- `GET /api/scenario/demo` – scenariu curent.
- `POST /api/scenario/reset` – reset date demo.
- `POST /api/optimize` – rezolvare reală cu flota existentă.
- `POST /api/optimize/hypothetical` – rezolvare și comparație cu varianta ipotetică.
- `PUT /api/settings` – salvează `realSolverSeconds`, `hypotheticalSolverSeconds`, `fuelPriceRonPerLiter`.
- CRUD: `/api/orders`, `/api/drivers`, `/api/vehicles`.

Reguli model:
- capacitate camion standard 900 kg;
- plecare preferată din depozitul care are produsul;
- încărcare intermediară permisă în varianta reală când este nevoie;
- întoarcere la depozitul de start;
- 9h condus/zi, 45 min pauză după 4h30, repaus zilnic 11h;
- scorul include distanță, cost combustibil, timp total și ferestre de livrare.

## Ipotetic solve

Metoda ipotetică creează o flotă virtuală în `OptimizationService#createUnlimitedHypotheticalFleet`. Pentru fiecare depozit care are produsul necesar și pentru fiecare comandă compatibilă, se creează un camion candidat. Solverul decide câte camioane să folosească efectiv. Astfel, numărul de camioane ipotetice nu este limitat de `DemoScenarioService#vehicles()`.
