package ro.licenta.logistics.service;

import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import org.optaplanner.core.config.localsearch.LocalSearchPhaseConfig;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.termination.TerminationConfig;
import org.springframework.stereotype.Service;
import ro.licenta.logistics.dto.*;
import ro.licenta.logistics.solver.*;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OptimizationService {
    private static final double FIXED_OPERATIONAL_COST_RON_PER_KM = 0.60;
    // Distanța haversine subestimează drumul real; factorul aproximează kilometrajul pe șosea (OSRM îl afișează exact
    // pe hartă). Definit o singură dată, în scorul solverului: dacă solverul și raportarea ar folosi factori diferiți,
    // solverul ar valida orele legale pe alt drum decât cel pe care îl afișăm.
    private static final double ROAD_FACTOR = VehicleRoutingEasyScoreCalculator.ROAD_FACTOR;
    private static final double VAN_SPEED_KMH = 28.0;            // ultima milă, urban
    private static final double LINEHAUL_SPEED_KMH = 65.0;       // interurban
    // Cursele interurbane se fac cu echipaj dublu: un tur-retur București–Timișoara cere ~15h de condus, peste
    // maximul legal al unui singur șofer (9h). Cei doi șoferi se schimbă la volan, deci limita de condus a cursei
    // este dublă — dar și costul cu salariile este dublu. Anterior codul spunea „echipaj", însă aplica limita de
    // 13h a unui singur om și plătea un singur salariu: cursele ieșeau ilegale și subevaluate ca preț.
    private static final int LINEHAUL_CREW_SIZE = 2;
    private static final int LINEHAUL_MAX_DRIVE_MINUTES = LINEHAUL_CREW_SIZE * DemoScenarioService.LEGAL_DAILY_DRIVE_MINUTES; // 18h
    private static final int LINEHAUL_MAX_SHIFT_MINUTES = 20 * 60; // fereastra de lucru a echipajului (condus + transfer + pauze)
    private static final int LINEHAUL_TRANSFER_MINUTES = 30;      // încărcare/descărcare la fiecare hub
    private static final int LINEHAUL_SOLVER_SECONDS = 4;
    private static final String CENTRAL_HUB = "HUB-B";

    // Cost de service/mentenanță pe km. Amortizarea nu mai este o constantă: se calculează liniar din prețul
    // de achiziție și durata de viață configurate în Setări (vezi SettingsDto#vanDailyAmortizationRon).
    private static final double VAN_SERVICE_RON_PER_KM = 0.35;
    private static final double LINEHAUL_SERVICE_RON_PER_KM = 0.55;

    // Fereastra legală maximă de lucru într-o zi: 24h − 11h repaus zilnic obligatoriu.
    private static final int MAX_DAILY_SHIFT_MINUTES = 24 * 60 - DemoScenarioService.LEGAL_DAILY_REST_MINUTES;
    // Un vehicul nu poate fi condus 7 zile din 7: concedii, repaus săptămânal și rotația turelor cer șoferi în plus.
    private static final double DRIVER_ROTATION_FACTOR = 1.25;

    private final DemoScenarioService demo;

    public OptimizationService(DemoScenarioService demo) {
        this.demo = demo;
    }

    // Planurile rezolvate se păstrează între cereri, ca fiecare scenariu să poată fi optimizat SEPARAT: apeși
    // „Optimizează" pe tabul Real → se rezolvă doar realul, iar ipoteticul rămâne cel calculat anterior (sau gol).
    // Cache-ul este legat de versiunea datelor: orice modificare de colete/flotă/setări îl invalidează.
    private final Object planLock = new Object();
    private int planVersion = -1;
    private List<RouteDto> solvedRealRegional = List.of();
    private List<RouteDto> solvedHypoRegional = List.of();
    private List<RouteDto> solvedLineHaul = List.of();
    private VehicleRoutingSolution realSolution;   // punctul de plecare pentru scenariul ipotetic

    private void dropStalePlans(int version) {
        if (planVersion == version) return;
        planVersion = version;
        solvedRealRegional = List.of();
        solvedHypoRegional = List.of();
        solvedLineHaul = List.of();
        realSolution = null;
    }

    // Optimizează UN singur scenariu, cel selectat în interfață. Înainte, orice apăsare pe „Optimizează" rezolva
    // și realul, și ipoteticul (20s + 15s + linehaul), indiferent ce tab era deschis.
    public ScenarioDto optimize(boolean hypothetical) {
        var hubs = demo.depots();
        var shipments = demo.shipments();
        var settings = demo.settings();

        synchronized (planLock) {
            dropStalePlans(demo.version());
            if (solvedLineHaul.isEmpty()) {
                solvedLineHaul = lineHaulOptimizedRoutes(hubs, shipments, demo.lineHaulVehicles(), settings);
            }
            if (hypothetical) {
                solvedHypoRegional = solveHypothetical(hubs, shipments, settings);
            } else {
                VehicleRoutingSolution problem = vehicleRoutingProblem(hubs, shipments, demo.vans(), settings);
                VehicleRoutingSolution solved = buildVehicleRoutingSolver(settings.realSolverSeconds(), settings).solve(problem);
                realSolution = solved;
                solvedRealRegional = routesFromSolution(solved, hubs, settings);
            }
            return buildScenario();
        }
    }

    // Scenariul ipotetic: aceleași costuri ca în realitate (o dubă în plus tot înseamnă un salariu și o amortizare),
    // dar flota nu mai este limitată la cea deținută. Pornește din soluția reală, deci nu poate ieși mai prost decât
    // ea — poate doar să găsească un plan mai ieftin folosind vehicule pe care compania nu le are.
    private List<RouteDto> solveHypothetical(List<DepotDto> hubs, List<ShipmentDto> shipments, SettingsDto settings) {
        List<VehicleDto> fleet = createUnlimitedHypotheticalFleet(hubs, shipments);
        VehicleRoutingSolution problem = vehicleRoutingProblem(hubs, shipments, fleet, settings);
        if (realSolution != null) seedFromRealSolution(problem);
        VehicleRoutingSolution solved = buildVehicleRoutingSolver(settings.hypotheticalSolverSeconds(), settings).solve(problem);

        List<RouteDto> renamed = new ArrayList<>();
        int index = 1;
        for (RouteDto route : routesFromSolution(solved, hubs, settings))
            renamed.add(renameHypotheticalRoute(route, index++));
        return renamed;
    }

    // Copiază lanțurile din soluția reală în problema ipotetică (dubele reale fac parte din flota ipotetică, cu
    // aceleași coduri). Solverul pornește astfel dintr-o soluție deja bună și o poate doar îmbunătăți.
    private void seedFromRealSolution(VehicleRoutingSolution problem) {
        Map<String, TruckAnchor> trucksByCode = new HashMap<>();
        for (TruckAnchor truck : problem.getTruckList()) trucksByCode.put(truck.getCode(), truck);
        Map<Long, DeliveryVisit> visitsById = new HashMap<>();
        for (DeliveryVisit visit : problem.getVisitList()) visitsById.put(visit.getId(), visit);

        for (TruckAnchor sourceTruck : realSolution.getTruckList()) {
            TruckAnchor target = trucksByCode.get(sourceTruck.getCode());
            if (target == null) continue;
            Standstill previous = target;
            for (DeliveryVisit sourceVisit : VehicleRoutingEasyScoreCalculator.extractChain(sourceTruck, realSolution.getVisitList())) {
                DeliveryVisit visit = visitsById.get(sourceVisit.getId());
                if (visit == null) continue;
                visit.setPreviousStandstill(previous);
                visit.setTruck(target);
                previous = visit;
            }
        }
    }

    private ScenarioDto buildScenario() {
        var hubs = demo.depots();
        var shipments = demo.shipments();
        var settings = demo.settings();

        var initialRoutes = concat(initialRegionalRoutes(hubs, shipments, demo.vans(), settings),
                lineHaulBaselineRoutes(hubs, shipments, settings));

        boolean realSolved = !solvedRealRegional.isEmpty();
        var routes = realSolved ? concat(solvedRealRegional, solvedLineHaul) : initialRoutes;
        // Dacă ipoteticul nu a fost rulat încă, NU se inventează nimic: lista rămâne goală, iar interfața cere
        // explicit o optimizare pe tabul Ipotetic.
        var hypotheticalRoutes = solvedHypoRegional.isEmpty() ? List.<RouteDto>of() : concat(solvedHypoRegional, solvedLineHaul);

        return new ScenarioDto(hubs, demo.depot(), shipments, demo.drivers(), demo.vehicles(),
                initialRoutes, routes, hypotheticalRoutes,
                statistics(initialRoutes, routes, hypotheticalRoutes, shipments),
                demo.monthlyReports(), costAnalysis(initialRoutes, routes, hypotheticalRoutes, hubs, settings, realSolved), settings);
    }

    // Încărcarea demo și operațiile CRUD nu rulează solverul. Planurile deja optimizate (dacă datele nu s-au
    // schimbat între timp) sunt păstrate, ca schimbarea unui tab să nu însemne o nouă rulare de 20 de secunde.
    public ScenarioDto scenario(boolean optimized) {
        synchronized (planLock) {
            dropStalePlans(demo.version());
            return buildScenario();
        }
    }

    // ---------- Regional VRP (dube: ridicări + livrări în jurul fiecărui hub) ----------

    // Costul variabil al unei dube pe kilometru: combustibil + taxe de drum + service.
    private double vanCostPerKm(SettingsDto settings) {
        return DemoScenarioService.VAN_CONSUMPTION / 100.0 * settings.fuelPriceRonPerLiter()
                + FIXED_OPERATIONAL_COST_RON_PER_KM + VAN_SERVICE_RON_PER_KM;
    }

    private double truckCostPerKm(SettingsDto settings) {
        return DemoScenarioService.LINEHAUL_CONSUMPTION / 100.0 * settings.fuelPriceRonPerLiter()
                + FIXED_OPERATIONAL_COST_RON_PER_KM + LINEHAUL_SERVICE_RON_PER_KM;
    }

    // Vehiculele costă bani în ORICE scenariu, inclusiv în cel ipotetic: o dubă în plus înseamnă un salariu și o
    // amortizare, indiferent dacă e deținută sau imaginată. Doar așa costul celor două planuri este comparabil.
    public VehicleRoutingSolution vehicleRoutingProblem(List<DepotDto> hubs, List<ShipmentDto> shipments, List<VehicleDto> vans,
                                                        SettingsDto settings) {
        Map<String, DepotDto> hubById = hubs.stream().collect(Collectors.toMap(DepotDto::id, d -> d));
        double costPerKm = vanCostPerKm(settings);
        double fixedCost = settings.driverDailySalaryRon() + settings.vanDailyAmortizationRon();
        List<TruckAnchor> trucks = vans.stream().map(v -> {
            DepotDto d = hubById.get(v.depotId());
            return new TruckAnchor(v.code(), v.driverName(), v.depotId(), d.name(), new HashSet<>(d.coverage()),
                    v.capacityKg(), v.consumptionLPer100Km(), costPerKm, fixedCost,
                    DemoScenarioService.LEGAL_DAILY_DRIVE_MINUTES, DemoScenarioService.LEGAL_BREAK_AFTER_MINUTES, DemoScenarioService.LEGAL_BREAK_DURATION_MINUTES,
                    d.latitude(), d.longitude(), VAN_SPEED_KMH);
        }).toList();
        List<DeliveryVisit> visits = buildVisits(shipments);
        return new VehicleRoutingSolution(trucks, visits);
    }

    // Fiecare colet generează două operațiuni pentru solver: o ridicare (la hub-ul de origine) și o livrare (la hub-ul destinație).
    private List<DeliveryVisit> buildVisits(List<ShipmentDto> shipments) {
        List<DeliveryVisit> visits = new ArrayList<>();
        for (ShipmentDto s : shipments) {
            int pickupService = Math.max(5, s.serviceMinutes() - 2);
            visits.add(new DeliveryVisit(s.id() * 2, s.id(), "Ridicare: " + s.senderName(), s.pickupAddress(), s.pickupCity(),
                    s.pickupLat(), s.pickupLon(), s.weightKg(), s.timeWindow(), s.windowStartMinute(), s.windowEndMinute(),
                    pickupService, s.priority(), "PICKUP", s.originHubId()));
            visits.add(new DeliveryVisit(s.id() * 2 + 1, s.id(), "Livrare: " + s.recipientName(), s.deliveryAddress(), s.deliveryCity(),
                    s.deliveryLat(), s.deliveryLon(), s.weightKg(), s.timeWindow(), s.windowStartMinute(), s.windowEndMinute(),
                    s.serviceMinutes(), s.priority(), "DELIVERY", s.destHubId()));
        }
        return visits;
    }

    public List<RouteDto> routesFromSolution(VehicleRoutingSolution solved, List<DepotDto> hubs, SettingsDto settings) {
        Map<String, DepotDto> hubById = hubs.stream().collect(Collectors.toMap(DepotDto::id, d -> d));
        List<RouteDto> result = new ArrayList<>();
        for (TruckAnchor truck : solved.getTruckList()) {
            List<DeliveryVisit> chain = VehicleRoutingEasyScoreCalculator.extractChain(truck, solved.getVisitList());
            DepotDto hub = hubById.get(truck.getDepotId());
            result.add(regionalRoute(truck.getCode(), truck.getDriverName(), hub, truck.getCapacityKg(), truck.getConsumptionLPer100Km(), chain, settings));
        }
        return result.stream().filter(r -> !r.stops().isEmpty()).toList();
    }

    private List<RouteDto> initialRegionalRoutes(List<DepotDto> hubs, List<ShipmentDto> shipments, List<VehicleDto> vans, SettingsDto settings) {
        Map<String, DepotDto> hubById = hubs.stream().collect(Collectors.toMap(DepotDto::id, d -> d));
        List<DeliveryVisit> visits = buildVisits(shipments);
        Map<String, List<VehicleDto>> vansByHub = vans.stream().collect(Collectors.groupingBy(VehicleDto::depotId, LinkedHashMap::new, Collectors.toList()));
        Map<VehicleDto, List<DeliveryVisit>> buckets = new LinkedHashMap<>();
        vans.forEach(v -> buckets.put(v, new ArrayList<>()));
        Map<String, Integer> cursor = new HashMap<>();
        for (DeliveryVisit visit : visits) {
            List<VehicleDto> regional = vansByHub.get(visit.getHubId());
            if (regional == null || regional.isEmpty()) {
                // Hub fără dube proprii: coletul NU se aruncă. Solverul îl va livra oricum (fiecare vizită trebuie
                // atribuită unei dube), așa că un baseline care îl ignoră ar compara două planuri cu volume de
                // muncă diferite — și ar face optimizarea să pară mai scumpă decât planul inițial.
                regional = nearestVans(vansByHub, hubById, visit.getHubId());
                if (regional.isEmpty()) continue;   // nu există nicio dubă nicăieri
            }
            int idx = cursor.getOrDefault(visit.getHubId(), 0);
            buckets.get(regional.get(idx % regional.size())).add(visit);
            cursor.put(visit.getHubId(), idx + 1);
        }
        List<RouteDto> result = new ArrayList<>();
        for (var entry : buckets.entrySet()) {
            VehicleDto v = entry.getKey();
            DepotDto hub = hubById.get(v.depotId());
            result.add(regionalRoute(v.code(), v.driverName(), hub, v.capacityKg(), v.consumptionLPer100Km(), entry.getValue(), settings));
        }
        return result.stream().filter(r -> !r.stops().isEmpty()).toList();
    }

    // Dubele celui mai apropiat hub care chiar are dube.
    private List<VehicleDto> nearestVans(Map<String, List<VehicleDto>> vansByHub, Map<String, DepotDto> hubById, String hubId) {
        DepotDto origin = hubById.get(hubId);
        if (origin == null) return List.of();
        return vansByHub.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty() && hubById.containsKey(e.getKey()))
                .min(Comparator.comparingDouble(e -> {
                    DepotDto other = hubById.get(e.getKey());
                    return VehicleRoutingEasyScoreCalculator.haversine(origin.latitude(), origin.longitude(), other.latitude(), other.longitude());
                }))
                .map(Map.Entry::getValue)
                .orElse(List.of());
    }

    // Flota ipotetică le include pe dubele reale (cu aceleași coduri, ca soluția reală să poată fi folosită drept
    // punct de plecare) și adaugă peste ele vehicule suplimentare, pe care compania nu le deține.
    public List<VehicleDto> createUnlimitedHypotheticalFleet(List<DepotDto> hubs, List<ShipmentDto> shipments) {
        List<VehicleDto> fleet = new ArrayList<>(demo.vans());
        int code = 1;
        for (DepotDto hub : hubs) {
            long visitsAtHub = shipments.stream().filter(s -> s.originHubId().equals(hub.id())).count()
                    + shipments.stream().filter(s -> s.destHubId().equals(hub.id())).count();
            // Flotă virtuală mai generoasă decât cea reală, dar mărginită (nu una per vizită) ca solverul să rămână rapid.
            int vansHere = Math.max(2, (int) Math.ceil(visitsAtHub / 5.0));
            for (int i = 0; i < vansHere; i++) {
                fleet.add(new VehicleDto("IPO-DUBA-" + String.format("%03d", code), "DRV-IPOTETIC", "Șofer ipotetic", hub.id(),
                        "VAN", DemoScenarioService.VAN_CAPACITY_KG, DemoScenarioService.VAN_CONSUMPTION, DemoScenarioService.VAN_COST_RON_PER_KM));
                code++;
            }
        }
        return fleet;
    }

    private RouteDto regionalRoute(String code, String driverName, DepotDto hub, double capacityKg, double consumption, List<DeliveryVisit> chain, SettingsDto settings) {
        List<RouteStopDto> stops = new ArrayList<>();
        int sequence = 1;
        for (DeliveryVisit v : chain) {
            stops.add(new RouteStopDto(v.getShipmentId(), sequence++, v.getName(), v.getAddress(), v.getCity(),
                    v.getLatitude(), v.getLongitude(), v.getPriority(), v.getTimeWindow(), v.getTaskType()));
        }
        double distance = routeDistanceWithStops(hub, stops) * ROAD_FACTOR;
        int driving = VehicleRoutingEasyScoreCalculator.minutesFor(distance, VAN_SPEED_KMH);
        int service = chain.stream().mapToInt(DeliveryVisit::getServiceMinutes).sum();
        int breaks = legalBreakMinutes(driving, DemoScenarioService.LEGAL_BREAK_AFTER_MINUTES, DemoScenarioService.LEGAL_BREAK_DURATION_MINUTES);
        int total = driving + service + breaks;
        double fuel = distance * consumption / 100.0;
        double driverCost = stops.isEmpty() ? 0 : settings.driverDailySalaryRon();
        double amortization = stops.isEmpty() ? 0 : settings.vanDailyAmortizationRon();
        double serviceCost = distance * VAN_SERVICE_RON_PER_KM;
        double cost = fuel * settings.fuelPriceRonPerLiter() + distance * FIXED_OPERATIONAL_COST_RON_PER_KM + driverCost + amortization + serviceCost;
        double load = chain.stream().mapToDouble(DeliveryVisit::getWeightKg).sum();
        double peak = VehicleRoutingEasyScoreCalculator.peakLoad(chain);
        return new RouteDto(code, driverName, hub, "REGIONAL", round(load), round(peak), capacityKg, round(distance), driving, service, breaks, total,
                DemoScenarioService.LEGAL_DAILY_DRIVE_MINUTES, DemoScenarioService.LEGAL_DAILY_REST_MINUTES, round(fuel), round(cost), stops);
    }

    // ---------- Linehaul baseline: spițe directe prin hub-and-spoke (neoptimizat, tur-retur per cursă) ----------

    private List<RouteDto> lineHaulBaselineRoutes(List<DepotDto> hubs, List<ShipmentDto> shipments, SettingsDto settings) {
        Map<String, DepotDto> hubById = hubs.stream().collect(Collectors.toMap(DepotDto::id, d -> d));
        Map<String, double[]> lanes = new LinkedHashMap<>(); // "A>B" -> [greutate, nr colete]
        for (ShipmentDto s : shipments) {
            String o = s.originHubId(), d = s.destHubId();
            if (o.equals(d)) continue;
            if (o.equals(CENTRAL_HUB) || d.equals(CENTRAL_HUB)) {
                accumulateLane(lanes, o, d, s.weightKg());
            } else {
                accumulateLane(lanes, o, CENTRAL_HUB, s.weightKg());
                accumulateLane(lanes, CENTRAL_HUB, d, s.weightKg());
            }
        }
        List<RouteDto> routes = new ArrayList<>();
        int trip = 1;
        for (var entry : lanes.entrySet()) {
            String[] lane = entry.getKey().split(">");
            DepotDto origin = hubById.get(lane[0]);
            DepotDto dest = hubById.get(lane[1]);
            double totalWeight = entry.getValue()[0];
            int parcels = (int) entry.getValue()[1];
            int tripsNeeded = Math.max(1, (int) Math.ceil(totalWeight / DemoScenarioService.LINEHAUL_CAPACITY_KG));
            double remaining = totalWeight;
            for (int t = 0; t < tripsNeeded; t++) {
                double load = Math.min(remaining, DemoScenarioService.LINEHAUL_CAPACITY_KG);
                remaining -= load;
                String code = "SPITA-" + String.format("%02d", trip++);
                routes.add(baselineLineHaulRoute(code, origin, dest, load, parcels, settings));
            }
        }
        return routes;
    }

    private RouteDto baselineLineHaulRoute(String code, DepotDto origin, DepotDto dest, double load, int parcels, SettingsDto settings) {
        RouteStopDto destStop = new RouteStopDto(null, 1, dest.name(), "Hub " + dest.city() + " · " + parcels + " colete", dest.city(),
                dest.latitude(), dest.longitude(), "LINEHAUL", "transfer", "HUB");
        List<RouteStopDto> stops = List.of(destStop);
        // Spița neoptimizată face tur-retur (camion care se întoarce gol).
        double distance = VehicleRoutingEasyScoreCalculator.haversine(origin.latitude(), origin.longitude(), dest.latitude(), dest.longitude()) * ROAD_FACTOR * 2;
        int driving = VehicleRoutingEasyScoreCalculator.minutesFor(distance, LINEHAUL_SPEED_KMH);
        int breaks = legalBreakMinutes(driving, DemoScenarioService.LEGAL_BREAK_AFTER_MINUTES, DemoScenarioService.LEGAL_BREAK_DURATION_MINUTES);
        int total = driving + LINEHAUL_TRANSFER_MINUTES + breaks;
        double fuel = distance * DemoScenarioService.LINEHAUL_CONSUMPTION / 100.0;
        double cost = fuel * settings.fuelPriceRonPerLiter() + distance * FIXED_OPERATIONAL_COST_RON_PER_KM
                + LINEHAUL_CREW_SIZE * settings.driverDailySalaryRon()
                + settings.truckDailyAmortizationRon() + distance * LINEHAUL_SERVICE_RON_PER_KM;
        // La linehaul marfa e consolidată: camionul chiar cară toată încărcătura simultan, deci vârful = totalul.
        return new RouteDto(code, "Echipaj spiță", origin, "LINEHAUL", round(load), round(load), DemoScenarioService.LINEHAUL_CAPACITY_KG, round(distance), driving, LINEHAUL_TRANSFER_MINUTES, breaks, total,
                LINEHAUL_MAX_DRIVE_MINUTES, DemoScenarioService.LEGAL_DAILY_REST_MINUTES, round(fuel), round(cost), stops);
    }

    // ---------- Linehaul optimizat: milk-run între hub-uri, rezolvat tot cu OptaPlanner ----------

    private List<RouteDto> lineHaulOptimizedRoutes(List<DepotDto> hubs, List<ShipmentDto> shipments, List<VehicleDto> lineHaulVehicles, SettingsDto settings) {
        Map<String, DepotDto> hubById = hubs.stream().collect(Collectors.toMap(DepotDto::id, d -> d));
        DepotDto central = hubById.get(CENTRAL_HUB);
        // Greutatea consolidată transferată prin fiecare hub (non-central), pe ambele sensuri.
        Map<String, Double> freight = new LinkedHashMap<>();
        for (ShipmentDto s : shipments) {
            if (s.originHubId().equals(s.destHubId())) continue;
            if (!s.originHubId().equals(CENTRAL_HUB)) freight.merge(s.originHubId(), s.weightKg(), Double::sum);
            if (!s.destHubId().equals(CENTRAL_HUB)) freight.merge(s.destHubId(), s.weightKg(), Double::sum);
        }
        if (freight.isEmpty()) return List.of();

        // Marfa unui hub se împarte în transferuri care încap într-un camion. Un singur transfer indivizibil cu
        // toată marfa hub-ului ar deveni imposibil de executat de îndată ce volumul depășește capacitatea unui
        // camion (12 t) — solverul n-ar avea cum să îl fractioneze, iar planul ar fi fizic irealizabil.
        List<DeliveryVisit> visits = new ArrayList<>();
        long id = 1;
        for (var e : freight.entrySet()) {
            DepotDto hub = hubById.get(e.getKey());
            double remaining = e.getValue();
            int trips = Math.max(1, (int) Math.ceil(remaining / DemoScenarioService.LINEHAUL_CAPACITY_KG));
            for (int t = 0; t < trips; t++) {
                double chunk = Math.min(remaining, DemoScenarioService.LINEHAUL_CAPACITY_KG);
                remaining -= chunk;
                String name = trips == 1 ? "Transfer " + hub.city() : "Transfer " + hub.city() + " (" + (t + 1) + "/" + trips + ")";
                visits.add(new DeliveryVisit(id++, null, name, hub.name(), hub.city(),
                        hub.latitude(), hub.longitude(), chunk, "00:00-23:59", 0, 1439, LINEHAUL_TRANSFER_MINUTES, "NORMAL", "LINEHAUL", CENTRAL_HUB));
            }
        }
        // Pool de camioane linehaul ancorate la hub-ul central; solverul alege câte folosește.
        List<TruckAnchor> trucks = new ArrayList<>();
        int poolSize = Math.max(visits.size(), Math.max(1, lineHaulVehicles.size()));
        double truckCostPerKm = truckCostPerKm(settings);
        double truckFixedCost = LINEHAUL_CREW_SIZE * settings.driverDailySalaryRon() + settings.truckDailyAmortizationRon();
        for (int i = 0; i < poolSize; i++) {
            String driverName = lineHaulVehicles.isEmpty() ? "Echipaj linehaul" : lineHaulVehicles.get(i % lineHaulVehicles.size()).driverName();
            trucks.add(new TruckAnchor("LH-" + i, driverName, CENTRAL_HUB, central.name(), new HashSet<>(),
                    DemoScenarioService.LINEHAUL_CAPACITY_KG, DemoScenarioService.LINEHAUL_CONSUMPTION, truckCostPerKm, truckFixedCost,
                    LINEHAUL_MAX_DRIVE_MINUTES, DemoScenarioService.LEGAL_BREAK_AFTER_MINUTES, DemoScenarioService.LEGAL_BREAK_DURATION_MINUTES,
                    central.latitude(), central.longitude(), LINEHAUL_SPEED_KMH));
        }

        VehicleRoutingSolution problem = new VehicleRoutingSolution(trucks, visits);
        Solver<VehicleRoutingSolution> solver = buildVehicleRoutingSolver(LINEHAUL_SOLVER_SECONDS, settings);
        VehicleRoutingSolution solved = solver.solve(problem);

        List<RouteDto> routes = new ArrayList<>();
        int trip = 1;
        for (TruckAnchor truck : solved.getTruckList()) {
            List<DeliveryVisit> chain = VehicleRoutingEasyScoreCalculator.extractChain(truck, solved.getVisitList());
            if (chain.isEmpty()) continue;
            routes.add(milkRunRoute("CURSA-" + String.format("%02d", trip++), truck.getDriverName(), central, chain, settings));
        }
        return routes;
    }

    private RouteDto milkRunRoute(String code, String driverName, DepotDto central, List<DeliveryVisit> chain, SettingsDto settings) {
        List<RouteStopDto> stops = new ArrayList<>();
        int sequence = 1;
        for (DeliveryVisit v : chain) {
            stops.add(new RouteStopDto(null, sequence++, v.getName(), "Hub " + v.getCity(), v.getCity(),
                    v.getLatitude(), v.getLongitude(), "LINEHAUL", "transfer consolidat", "HUB"));
        }
        double distance = routeDistanceWithStops(central, stops) * ROAD_FACTOR; // tur-retur central -> hub-uri -> central
        int driving = VehicleRoutingEasyScoreCalculator.minutesFor(distance, LINEHAUL_SPEED_KMH);
        int service = chain.size() * LINEHAUL_TRANSFER_MINUTES;
        int breaks = legalBreakMinutes(driving, DemoScenarioService.LEGAL_BREAK_AFTER_MINUTES, DemoScenarioService.LEGAL_BREAK_DURATION_MINUTES);
        int total = driving + service + breaks;
        double fuel = distance * DemoScenarioService.LINEHAUL_CONSUMPTION / 100.0;
        double load = chain.stream().mapToDouble(DeliveryVisit::getWeightKg).sum();
        double cost = fuel * settings.fuelPriceRonPerLiter() + distance * FIXED_OPERATIONAL_COST_RON_PER_KM
                + LINEHAUL_CREW_SIZE * settings.driverDailySalaryRon()
                + settings.truckDailyAmortizationRon() + distance * LINEHAUL_SERVICE_RON_PER_KM;
        return new RouteDto(code, driverName, central, "LINEHAUL", round(load), round(load), DemoScenarioService.LINEHAUL_CAPACITY_KG, round(distance), driving, service, breaks, total,
                LINEHAUL_MAX_DRIVE_MINUTES, DemoScenarioService.LEGAL_DAILY_REST_MINUTES, round(fuel), round(cost), stops);
    }

    private void accumulateLane(Map<String, double[]> lanes, String a, String b, double weight) {
        double[] arr = lanes.computeIfAbsent(a + ">" + b, k -> new double[2]);
        arr[0] += weight;
        arr[1] += 1;
    }

    // ---------- Live optimization helpers ----------

    // Costurile nu mai sunt injectate într-un câmp static al funcției de scor: fiecare vehicul din problemă își
    // poartă propriile costuri (vezi TruckAnchor). Altfel, o salvare de setări în timpul unei optimizări live
    // schimba funcția de scor sub solverul aflat în plină execuție.
    public Solver<VehicleRoutingSolution> buildVehicleRoutingSolver(int seconds, SettingsDto settings) {
        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(VehicleRoutingSolution.class)
                .withEntityClasses(DeliveryVisit.class)
                .withEasyScoreCalculatorClass(VehicleRoutingEasyScoreCalculator.class)
                .withPhases(new ConstructionHeuristicPhaseConfig(), new LocalSearchPhaseConfig())
                .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(Math.max(1, seconds))));
        SolverFactory<VehicleRoutingSolution> solverFactory = SolverFactory.create(solverConfig);
        return solverFactory.buildSolver();
    }

    public ScenarioDto scenarioFromLiveRoutes(List<RouteDto> liveRegionalRoutes, boolean hypotheticalMode) {
        var hubs = demo.depots();
        var shipments = demo.shipments();
        var vans = demo.vans();
        var drivers = demo.drivers();
        var vehicles = demo.vehicles();
        var settings = demo.settings();

        var lineHaul = lineHaulBaselineRoutes(hubs, shipments, settings);
        var initialRegional = initialRegionalRoutes(hubs, shipments, vans, settings);

        var initialRoutes = concat(initialRegional, lineHaul);
        // Rutele live populează exact scenariul care se optimizează; celălalt rămâne gol, nu se umple cu planul
        // inițial deghizat în rezultat.
        var routes = hypotheticalMode ? initialRoutes : concat(liveRegionalRoutes, lineHaul);
        var hypotheticalRoutes = hypotheticalMode ? concat(liveRegionalRoutes, lineHaul) : List.<RouteDto>of();

        // Linehaul-ul rămâne cel baseline în modul live (un solve de 4s la fiecare poll ar bloca interfața),
        // deci planul nu e complet optimizat: recomandarea de flotă se calculează doar după /optimize.
        return new ScenarioDto(hubs, demo.depot(), shipments, drivers, vehicles,
                initialRoutes, routes, hypotheticalRoutes,
                statistics(initialRoutes, routes, hypotheticalRoutes, shipments),
                demo.monthlyReports(), costAnalysis(initialRoutes, routes, hypotheticalRoutes, hubs, settings, false), settings);
    }

    // ---------- Statistics ----------

    private StatisticsDto statistics(List<RouteDto> initial, List<RouteDto> optimized, List<RouteDto> hypothetical, List<ShipmentDto> shipments) {
        // Un scenariu ipotetic nerulat rămâne pe zero peste tot; altfel o listă goală ar fi raportată drept
        // „0 km, 0 RON", adică o îmbunătățire de 100%.
        boolean hasHypothetical = !hypothetical.isEmpty();
        double d1 = sumDistance(initial), d2 = sumDistance(optimized), d3 = hasHypothetical ? sumDistance(hypothetical) : 0;
        int t1 = sumDuration(initial), t2 = sumDuration(optimized), t3 = hasHypothetical ? sumDuration(hypothetical) : 0;
        double a1 = averageDeliveryMinutes(initial), a2 = averageDeliveryMinutes(optimized), a3 = hasHypothetical ? averageDeliveryMinutes(hypothetical) : 0;
        double f1 = sumFuel(initial), f2 = sumFuel(optimized), f3 = hasHypothetical ? sumFuel(hypothetical) : 0;
        double c1 = sumCost(initial), c2 = sumCost(optimized), c3 = hasHypothetical ? sumCost(hypothetical) : 0;
        if (!hasHypothetical) {
            return new StatisticsDto(
                    round(d1), round(d2), 0, round(d1 - d2), 0, pct(d1, d2), 0,
                    t1, t2, 0, t1 - t2, 0, pct(t1, t2), 0,
                    round(a1), round(a2), 0, round(a1 - a2), 0, pct(a1, a2), 0,
                    round(f1), round(f2), 0, round(f1 - f2), 0,
                    round(c1), round(c2), 0, round(c1 - c2), 0,
                    round(shipments.stream().mapToDouble(ShipmentDto::tariffRon).sum()),
                    round(shipments.stream().mapToDouble(ShipmentDto::tariffRon).sum() - c1),
                    round(shipments.stream().mapToDouble(ShipmentDto::tariffRon).sum() - c2),
                    round(c1 - c2));
        }
        double revenue = shipments.stream().mapToDouble(ShipmentDto::tariffRon).sum();
        double baselineProfit = revenue - c1;
        double optimizedProfit = revenue - c2;
        double moneySaved = c1 - c2;
        return new StatisticsDto(
                round(d1), round(d2), round(d3), round(d1 - d2), round(d1 - d3), pct(d1, d2), pct(d1, d3),
                t1, t2, t3, t1 - t2, t1 - t3, pct(t1, t2), pct(t1, t3),
                round(a1), round(a2), round(a3), round(a1 - a2), round(a1 - a3), pct(a1, a2), pct(a1, a3),
                round(f1), round(f2), round(f3), round(f1 - f2), round(f1 - f3),
                round(c1), round(c2), round(c3), round(c1 - c2), round(c1 - c3),
                round(revenue), round(baselineProfit), round(optimizedProfit), round(moneySaved)
        );
    }

    private double averageDeliveryMinutes(List<RouteDto> routes) {
        double deliveryTimeSum = 0;
        int deliveries = 0;
        for (RouteDto route : routes) {
            if (!"REGIONAL".equals(route.kind())) continue;
            double lat = route.depot().latitude(), lon = route.depot().longitude();
            for (RouteStopDto stop : route.stops()) {
                double leg = VehicleRoutingEasyScoreCalculator.haversine(lat, lon, stop.latitude(), stop.longitude()) * ROAD_FACTOR;
                int legMinutes = VehicleRoutingEasyScoreCalculator.minutesFor(leg, VAN_SPEED_KMH);
                if ("DELIVERY".equals(stop.stopType())) {
                    deliveryTimeSum += legMinutes + serviceForStop(stop);
                    deliveries++;
                }
                lat = stop.latitude();
                lon = stop.longitude();
            }
        }
        return deliveries == 0 ? 0 : deliveryTimeSum / deliveries;
    }

    private int serviceForStop(RouteStopDto stop) {
        if (stop.shipmentId() == null) return 10;
        return demo.shipments().stream().filter(s -> Objects.equals(s.id(), stop.shipmentId())).mapToInt(ShipmentDto::serviceMinutes).findFirst().orElse(10);
    }

    // ---------- Cost analysis + recomandare flotă ----------

    // planOptimized: recomandarea de flotă are sens doar pe un plan rezolvat de solver. Planul neoptimizat trimite
    // câte un camion dus-întors pe fiecare spiță (14 curse în loc de 4 milk-run-uri), iar dimensionarea flotei pe
    // baza lui ar cere absurd de multe camioane. Fără optimizare, secțiunea lipsește, în loc să dea un sfat greșit.
    private CostAnalysisDto costAnalysis(List<RouteDto> initial, List<RouteDto> real, List<RouteDto> hypothetical,
                                         List<DepotDto> hubs, SettingsDto settings, boolean planOptimized) {
        List<CostScenarioDto> scenarios = List.of(
                new CostScenarioDto("Inițial", costBreakdown(initial, settings)),
                new CostScenarioDto("Real", costBreakdown(real, settings)),
                new CostScenarioDto("Ipotetic", costBreakdown(hypothetical, settings))
        );
        return new CostAnalysisDto(scenarios, planOptimized ? fleetRecommendation(real, hubs, settings) : null);
    }

    private CostBreakdownDto costBreakdown(List<RouteDto> routes, SettingsDto settings) {
        double fuel = 0, driver = 0, amort = 0, service = 0, op = 0;
        int vans = 0, trucks = 0;
        for (RouteDto r : routes) {
            if (r.stops().isEmpty()) continue;
            boolean lh = "LINEHAUL".equals(r.kind());
            fuel += r.fuelLiters() * settings.fuelPriceRonPerLiter();
            op += r.distanceKm() * FIXED_OPERATIONAL_COST_RON_PER_KM;
            driver += (lh ? LINEHAUL_CREW_SIZE : 1) * settings.driverDailySalaryRon();  // cursa interurbană merge cu echipaj dublu
            amort += lh ? settings.truckDailyAmortizationRon() : settings.vanDailyAmortizationRon();
            service += r.distanceKm() * (lh ? LINEHAUL_SERVICE_RON_PER_KM : VAN_SERVICE_RON_PER_KM);
            if (lh) trucks++;
            else vans++;
        }
        // Vehiculele deținute, dar rămase în curte, se depreciază în continuare. Optimizarea rutelor nu atinge
        // acest cost — el scade doar dacă flota este redimensionată (vezi fleetRecommendation).
        int idleVans = Math.max(0, demo.vans().size() - vans);
        int idleTrucks = Math.max(0, demo.lineHaulVehicles().size() - trucks);
        double idleAmort = idleVans * settings.vanDailyAmortizationRon() + idleTrucks * settings.truckDailyAmortizationRon();
        double total = fuel + driver + amort + service + op;
        return new CostBreakdownDto(round(fuel), round(driver), round(amort), round(idleAmort), round(service), round(op),
                round(total), round(total + idleAmort), vans, trucks, idleVans, idleTrucks);
    }

    // Dimensionarea flotei pornește de la ce a folosit efectiv solverul în ziua optimizată și verifică două
    // constrângeri fizice per hub: capacitatea de încărcare și fereastra legală de lucru. Minimul rezultat
    // primește o rezervă (service, defecțiuni, vârfuri), iar diferența față de flota deținută este recomandarea.
    private FleetRecommendationDto fleetRecommendation(List<RouteDto> real, List<DepotDto> hubs, SettingsDto settings) {
        double reserve = 1 + settings.fleetReservePercent() / 100.0;
        Map<String, Long> ownedVansByHub = demo.vans().stream()
                .collect(Collectors.groupingBy(VehicleDto::depotId, Collectors.counting()));

        // Etapa 1: minimul fizic per hub, fără rezervă.
        List<HubPlan> plans = new ArrayList<>();
        int usedVans = 0, requiredVans = 0;
        double fleetLoad = 0, fleetCapacity = 0;

        for (DepotDto hub : hubs) {
            List<RouteDto> hubRoutes = real.stream()
                    .filter(r -> "REGIONAL".equals(r.kind()) && r.depot() != null && hub.id().equals(r.depot().id()))
                    .filter(r -> !r.stops().isEmpty())
                    .toList();
            int owned = ownedVansByHub.getOrDefault(hub.id(), 0L).intValue();
            if (hubRoutes.isEmpty() && owned == 0) continue;

            int vansUsed = hubRoutes.size();
            // Vârful de încărcătură, nu totalul manipulat: altfel o dubă care ridică și livrează toată ziua
            // ar părea supraîncărcată deși nu cară niciodată mai mult decât capacitatea ei.
            double loadKg = hubRoutes.stream().mapToDouble(RouteDto::peakLoadKg).sum();
            int workMinutes = hubRoutes.stream().mapToInt(RouteDto::durationMinutes).sum();

            // Minimul fizic: nici capacitatea unei dube, nici ziua legală de lucru nu pot fi depășite.
            int byCapacity = (int) Math.ceil(loadKg / DemoScenarioService.VAN_CAPACITY_KG);
            int byWorkTime = (int) Math.ceil(workMinutes / (double) MAX_DAILY_SHIFT_MINUTES);
            int required = Math.max(vansUsed, Math.max(byCapacity, byWorkTime));

            double capacityKg = vansUsed * DemoScenarioService.VAN_CAPACITY_KG;
            double utilization = capacityKg == 0 ? 0 : (loadKg / capacityKg) * 100;
            fleetLoad += loadKg;
            fleetCapacity += owned * DemoScenarioService.VAN_CAPACITY_KG;

            plans.add(new HubPlan(hub.city(), owned, vansUsed, required, byCapacity, byWorkTime, loadKg, capacityKg, utilization));
            usedVans += vansUsed;
            requiredVans += required;
        }

        // Etapa 2: rezerva se aplică pe total, nu pe fiecare hub. Rotunjită în sus la nivel de hub, o rezervă
        // de 15% ar adăuga o dubă în plus în FIECARE hub (3 → ceil(3,45) = 4), adică ~50% flotă în plus.
        int recommendedVans = requiredVans == 0 ? 0 : (int) Math.ceil(requiredVans * reserve);
        List<HubFleetDto> perHub = distributeReserve(plans, requiredVans, recommendedVans);

        // Camioanele de linehaul sunt alese de solver dintr-un pool nelimitat, deci numărul folosit este chiar
        // necesarul de transfer între hub-uri — independent de câte camioane deține compania.
        List<RouteDto> truckRoutes = real.stream().filter(r -> "LINEHAUL".equals(r.kind()) && !r.stops().isEmpty()).toList();
        int usedTrucks = truckRoutes.size();
        double freightKg = truckRoutes.stream().mapToDouble(RouteDto::loadKg).sum();
        int trucksByCapacity = (int) Math.ceil(freightKg / DemoScenarioService.LINEHAUL_CAPACITY_KG);
        // Distanțele dintre hub-uri sunt mari, așa că limita care mușcă la camioane nu e tonajul, ci ziua legală:
        // un milk-run București → Cluj → Timișoara depășește 13h de condus. Fără această verificare, recomandarea
        // ar propune reducerea flotei tocmai când camioanele existente rulează deja peste program.
        int truckMinutes = truckRoutes.stream().mapToInt(RouteDto::durationMinutes).sum();
        int trucksByWorkTime = (int) Math.ceil(truckMinutes / (double) LINEHAUL_MAX_SHIFT_MINUTES);
        int requiredTrucks = Math.max(usedTrucks, Math.max(trucksByCapacity, trucksByWorkTime));
        int recommendedTrucks = requiredTrucks == 0 ? 0 : (int) Math.ceil(requiredTrucks * reserve);
        // O dubă cere un șofer, un camion cere un echipaj de doi. Peste asta, rotația pentru concedii și repaus.
        int seats = recommendedVans + recommendedTrucks * LINEHAUL_CREW_SIZE;
        int recommendedDrivers = (int) Math.ceil(seats * DRIVER_ROTATION_FACTOR);

        int currentVans = demo.vans().size();
        int currentTrucks = demo.lineHaulVehicles().size();
        int currentDrivers = demo.drivers().size();
        int vanDelta = recommendedVans - currentVans;
        int truckDelta = recommendedTrucks - currentTrucks;
        int driverDelta = recommendedDrivers - currentDrivers;

        double vanDaily = settings.vanDailyAmortizationRon();
        double truckDaily = settings.truckDailyAmortizationRon();
        int idleVans = Math.max(0, currentVans - usedVans);
        int idleTrucks = Math.max(0, currentTrucks - usedTrucks);
        double idlePerDay = idleVans * vanDaily + idleTrucks * truckDaily;

        int surplusVans = Math.max(0, -vanDelta), surplusTrucks = Math.max(0, -truckDelta);
        int shortageVans = Math.max(0, vanDelta), shortageTrucks = Math.max(0, truckDelta);
        double annualSaving = (surplusVans * vanDaily + surplusTrucks * truckDaily) * settings.workingDaysPerYear();
        double resaleValue = surplusVans * settings.vanResidualValueRon() + surplusTrucks * settings.truckResidualValueRon();
        double investment = shortageVans * settings.vanPurchasePriceRon() + shortageTrucks * settings.truckPurchasePriceRon();
        double annualExtraAmortization = (shortageVans * vanDaily + shortageTrucks * truckDaily) * settings.workingDaysPerYear();
        double fleetUtilization = fleetCapacity == 0 ? 0 : (fleetLoad / fleetCapacity) * 100;

        String action = action(vanDelta, truckDelta);
        String headline = headline(action, vanDelta, truckDelta, annualSaving, investment);
        List<String> reasons = reasons(action, settings, usedVans, currentVans, usedTrucks, currentTrucks,
                requiredVans, requiredTrucks, recommendedVans, recommendedTrucks, vanDelta, truckDelta, driverDelta,
                idleVans, idleTrucks, idlePerDay, annualSaving, resaleValue, investment, annualExtraAmortization,
                fleetUtilization, freightKg, perHub);

        String note = String.format(
                "Ziua optimizată cere minimum %d dube și %d camioane. Cu rezerva de %.0f%% pentru service, defecțiuni și vârfuri sezoniere, flota țintă este de %d dube și %d camioane, deservită de ~%d șoferi. Deții %d dube, %d camioane și %d șoferi.",
                requiredVans, requiredTrucks, settings.fleetReservePercent(), recommendedVans, recommendedTrucks,
                recommendedDrivers, currentVans, currentTrucks, currentDrivers);

        return new FleetRecommendationDto(currentVans, currentTrucks, currentDrivers, usedVans, usedTrucks,
                requiredVans, requiredTrucks, recommendedVans, recommendedTrucks, recommendedDrivers,
                vanDelta, truckDelta, driverDelta, action, headline, settings.fleetReservePercent(),
                round(vanDaily), round(truckDaily), round(idlePerDay), round(annualSaving), round(resaleValue),
                round(investment), round(annualExtraAmortization), round(fleetUtilization), reasons, perHub, note);
    }

    // Datele brute per hub, înainte de repartizarea rezervei.
    private record HubPlan(String city, int owned, int used, int required, int byCapacity, int byWorkTime,
                           double loadKg, double capacityKg, double utilization) {
    }

    // Repartizează rezerva de flotă pe hub-uri prin metoda celui mai mare rest, astfel încât suma coloanei
    // "recomandat" din tabel să fie exact numărul recomandat pe total — altfel raportul s-ar contrazice singur.
    private List<HubFleetDto> distributeReserve(List<HubPlan> plans, int requiredTotal, int recommendedTotal) {
        int[] recommended = new int[plans.size()];
        for (int i = 0; i < plans.size(); i++) recommended[i] = plans.get(i).required();

        int spare = recommendedTotal - requiredTotal;
        // Rezerva merge întâi la hub-urile cele mai încărcate: acolo o defecțiune blochează cele mai multe colete.
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < plans.size(); i++) order.add(i);
        order.sort(Comparator.comparingDouble((Integer i) -> plans.get(i).utilization()).reversed());
        for (int k = 0; k < spare && !order.isEmpty(); k++) recommended[order.get(k % order.size())]++;

        List<HubFleetDto> result = new ArrayList<>();
        for (int i = 0; i < plans.size(); i++) {
            HubPlan p = plans.get(i);
            int delta = recommended[i] - p.owned();
            result.add(new HubFleetDto(p.city(), p.owned(), p.used(), p.required(), recommended[i], delta,
                    round(p.loadKg()), round(p.capacityKg()), round(p.utilization()),
                    hubReason(delta, p.used(), p.owned(), p.byCapacity(), p.byWorkTime(), p.required())));
        }
        return result;
    }

    private String hubReason(int delta, int vansUsed, int owned, int byCapacity, int byWorkTime, int required) {
        if (owned == 0) return "Hub fără dube proprii; necesar " + required;
        if (byCapacity > vansUsed) return "Dubele ies supraîncărcate: minim " + required;
        if (byWorkTime > vansUsed) return "Ziua de lucru depășește limita legală: minim " + required;
        if (delta > 0) return "Fără rezervă de avarie: mai trebuie " + delta;
        if (delta < 0) return (-delta) + " dube în surplus, se amortizează degeaba";
        return "Dimensionat corect";
    }

    private String action(int vanDelta, int truckDelta) {
        if (vanDelta == 0 && truckDelta == 0) return "MENȚINE";
        if (vanDelta <= 0 && truckDelta <= 0) return "REDUCE";
        if (vanDelta >= 0 && truckDelta >= 0) return "EXTINDE";
        return "REECHILIBREAZĂ";
    }

    private String headline(String action, int vanDelta, int truckDelta, double annualSaving, double investment) {
        String vans = vehiclePhrase(vanDelta, "dubă", "dube");
        String trucks = vehiclePhrase(truckDelta, "camion", "camioane");
        return switch (action) {
            case "MENȚINE" -> "Păstrează flota actuală — este deja dimensionată corect.";
            case "REDUCE" -> String.format("Redu flota: %s%s. Economisești %s RON pe an din amortizare.",
                    vans, truckDelta == 0 ? "" : " și " + trucks, money(annualSaving));
            case "EXTINDE" -> String.format("Majorează flota: %s%s. Investiție de %s RON.",
                    vans, truckDelta == 0 ? "" : " și " + trucks, money(investment));
            default -> String.format("Reechilibrează flota: %s, %s.", vans, trucks);
        };
    }

    // "−3 dube" / "+2 camioane" / "0 camioane" într-o formă citibilă în raport.
    private String vehiclePhrase(int delta, String singular, String plural) {
        int n = Math.abs(delta);
        String noun = n == 1 ? singular : plural;
        if (delta < 0) return "renunță la " + n + " " + noun;
        if (delta > 0) return "cumpără încă " + n + " " + noun;
        return "menține " + noun;
    }

    private List<String> reasons(String action, SettingsDto settings, int usedVans, int currentVans, int usedTrucks,
                                 int currentTrucks, int requiredVans, int requiredTrucks, int recommendedVans,
                                 int recommendedTrucks, int vanDelta, int truckDelta, int driverDelta, int idleVans,
                                 int idleTrucks, double idlePerDay, double annualSaving, double resaleValue,
                                 double investment, double annualExtraAmortization, double fleetUtilization,
                                 double freightKg, List<HubFleetDto> perHub) {
        List<String> reasons = new ArrayList<>();

        reasons.add(String.format(
                "Ruta optimizată a avut nevoie de %d din cele %d dube deținute și de %d din cele %d camioane. Solverul plătește un cost fix pentru fiecare vehicul scos pe traseu, deci nu folosește niciun vehicul în plus față de cât e nevoie.",
                usedVans, currentVans, usedTrucks, currentTrucks));

        if (usedVans >= currentVans && currentVans > 0) {
            reasons.add("Toate dubele deținute au ieșit pe traseu: flota este la saturație. Nu există rezervă pentru o defecțiune sau o zi de vârf — o singură dubă imobilizată ar lăsa colete nelivrate.");
        }

        if (idleVans > 0 || idleTrucks > 0) {
            reasons.add(String.format(
                    "%d dube și %d camioane au rămas în curte, dar s-au depreciat cu %s RON în ziua respectivă (%s RON pe an). Amortizarea curge indiferent dacă vehiculul rulează sau nu — optimizarea rutelor nu o poate reduce, doar redimensionarea flotei.",
                    idleVans, idleTrucks, money(idlePerDay), money(idlePerDay * settings.workingDaysPerYear())));
        }

        reasons.add(String.format(
                "Gradul de încărcare al dubelor deținute este de %.0f%%: coletele cântăresc %s kg, iar capacitatea totală a flotei este de %s kg.",
                fleetUtilization, money(perHub.stream().mapToDouble(HubFleetDto::loadKg).sum()),
                money(currentVans * DemoScenarioService.VAN_CAPACITY_KG)));

        reasons.add(String.format(
                "Minimul fizic este de %d dube și %d camioane (limitat de capacitatea de %.0f kg a unei dube, de %.0f tone a unui camion și de ziua legală de lucru de %d ore). Peste el se adaugă o rezervă de %.0f%% pentru service, defecțiuni și vârfuri sezoniere, rezultând %d dube și %d camioane.",
                requiredVans, requiredTrucks, DemoScenarioService.VAN_CAPACITY_KG,
                DemoScenarioService.LINEHAUL_CAPACITY_KG / 1000, MAX_DAILY_SHIFT_MINUTES / 60,
                settings.fleetReservePercent(), recommendedVans, recommendedTrucks));

        if (freightKg > 0) {
            reasons.add(String.format(
                    "Linehaul-ul consolidează %s kg între hub-uri în %d curse pe zi. Aici limita nu este tonajul (un camion duce %.0f tone), ci distanța: milk-run-urile între orașe consumă ziua legală de condus, deci numărul de camioane este dictat de ore, nu de kilograme.",
                    money(freightKg), requiredTrucks, DemoScenarioService.LINEHAUL_CAPACITY_KG / 1000));
        }

        String surplusHubs = perHub.stream().filter(h -> h.delta() < 0)
                .map(h -> h.city() + " (−" + (-h.delta()) + ")").collect(Collectors.joining(", "));
        String shortageHubs = perHub.stream().filter(h -> h.delta() > 0)
                .map(h -> h.city() + " (+" + h.delta() + ")").collect(Collectors.joining(", "));
        if (!surplusHubs.isEmpty()) reasons.add("Hub-uri cu dube în surplus: " + surplusHubs + ".");
        if (!shortageHubs.isEmpty()) reasons.add("Hub-uri sub-dimensionate: " + shortageHubs + ".");

        if (annualSaving > 0) {
            reasons.add(String.format(
                    "Renunțarea la vehiculele în surplus taie %s RON pe an din amortizare și aduce ~%s RON din vânzarea lor la valoarea reziduală de %.0f%%.",
                    money(annualSaving), money(resaleValue), settings.residualValuePercent()));
        }
        if (investment > 0) {
            reasons.add(String.format(
                    "Vehiculele lipsă cer o investiție de %s RON, adică %s RON pe an în amortizare suplimentară — dar fără ele coletele nu încap sau șoferii depășesc programul legal.",
                    money(investment), money(annualExtraAmortization)));
        }
        if (driverDelta != 0) {
            reasons.add(String.format(
                    "Numărul de șoferi urmează flota: %d dube (un șofer fiecare) + %d camioane (echipaj de %d, cursele interurbane depășesc maximul legal al unui singur om) = %d posturi, × %.2f pentru rotație (concedii, repaus săptămânal) înseamnă %s șoferi față de câți ai acum.",
                    recommendedVans, recommendedTrucks, LINEHAUL_CREW_SIZE,
                    recommendedVans + recommendedTrucks * LINEHAUL_CREW_SIZE, DRIVER_ROTATION_FACTOR,
                    driverDelta > 0 ? "cu " + driverDelta + " mai mulți" : "cu " + (-driverDelta) + " mai puțini"));
        }
        if ("MENȚINE".equals(action)) {
            reasons.add("Flota deținută coincide cu cea recomandată: nu există nici surplus care se amortizează degeaba, nici lipsă care să blocheze livrările.");
        }
        return reasons;
    }

    private String money(double value) {
        return String.format("%,.0f", value).replace(',', '.');
    }

    // ---------- Helpers ----------

    private List<RouteDto> concat(List<RouteDto> a, List<RouteDto> b) {
        List<RouteDto> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private RouteDto renameHypotheticalRoute(RouteDto route, int index) {
        String suffix = String.format("%02d", index);
        return new RouteDto("IPOTETIC-" + suffix, "Șofer ipotetic " + index, route.depot(), route.kind(), route.loadKg(), route.peakLoadKg(), route.capacityKg(), route.distanceKm(), route.drivingMinutes(), route.serviceMinutes(), route.breakMinutes(), route.durationMinutes(), route.legalMaxDriveMinutes(), route.dailyRestMinutes(), route.fuelLiters(), route.costRon(), route.stops());
    }

    private int legalBreakMinutes(int driving, int after, int duration) {
        return driving <= after ? 0 : ((driving - 1) / after) * duration;
    }

    private double routeDistanceWithStops(DepotDto startHub, List<RouteStopDto> stops) {
        double total = 0, lat = startHub.latitude(), lon = startHub.longitude();
        for (RouteStopDto stop : stops) {
            total += VehicleRoutingEasyScoreCalculator.haversine(lat, lon, stop.latitude(), stop.longitude());
            lat = stop.latitude();
            lon = stop.longitude();
        }
        total += VehicleRoutingEasyScoreCalculator.haversine(lat, lon, startHub.latitude(), startHub.longitude());
        return total;
    }

    private double sumDistance(List<RouteDto> r) {
        return r.stream().mapToDouble(RouteDto::distanceKm).sum();
    }

    private int sumDuration(List<RouteDto> r) {
        return r.stream().mapToInt(RouteDto::durationMinutes).sum();
    }

    private double sumFuel(List<RouteDto> r) {
        return r.stream().mapToDouble(RouteDto::fuelLiters).sum();
    }

    private double sumCost(List<RouteDto> r) {
        return r.stream().mapToDouble(RouteDto::costRon).sum();
    }

    private double pct(double initial, double current) {
        return round(initial == 0 ? 0 : ((initial - current) / initial) * 100);
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
