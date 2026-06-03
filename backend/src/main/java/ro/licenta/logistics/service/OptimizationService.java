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
    private final DemoScenarioService demo;
    public OptimizationService(DemoScenarioService demo) { this.demo = demo; }

    public ScenarioDto scenario(boolean optimized) {
        var depots = demo.depots();
        var depot = demo.depot();
        var orders = demo.orders();
        var drivers = demo.drivers();
        var vehicles = demo.vehicles();
        var settings = demo.settings();
        var initial = initialRoutes(depots, orders, vehicles, settings);
        var solved = optimized ? optaPlannerVehicleRouting(depots, orders, vehicles, settings.realSolverSeconds(), settings) : initial;
        var hypothetical = hypotheticalBestRoutes(depots, orders, settings);
        if (isWorseThan(hypothetical, solved)) {
            // Siguranță pentru grafic: varianta ipotetică reprezintă limita superioară teoretică.
            // Dacă solverul nu apucă să găsească ceva mai bun în timpul setat, afișăm cel puțin soluția reală ca fallback.
            hypothetical = cloneAsHypothetical(solved);
        }
        return new ScenarioDto(depots, depot, orders, drivers, vehicles, solved, hypothetical, statistics(initial, solved, hypothetical), settings);
    }

    public ScenarioDto scenarioWithHypothetical() { return scenario(true); }

    private List<RouteDto> cloneAsHypothetical(List<RouteDto> solved) {
        List<RouteDto> copy = new ArrayList<>(); int i = 1;
        for (RouteDto r : solved) {
            copy.add(new RouteDto("IPOTETIC-" + String.format("%02d", i++), "Șofer ipotetic", r.depot(), r.loadKg(), r.capacityKg(), r.distanceKm(), r.drivingMinutes(), r.serviceMinutes(), r.breakMinutes(), r.durationMinutes(), r.legalMaxDriveMinutes(), r.dailyRestMinutes(), r.fuelLiters(), r.costRon(), r.stops()));
        }
        return copy;
    }

    private boolean isWorseThan(List<RouteDto> candidate, List<RouteDto> baseline) {
        return sumDistance(candidate) > sumDistance(baseline)
                || sumDuration(candidate) > sumDuration(baseline)
                || sumCost(candidate) > sumCost(baseline);
    }

    private List<RouteDto> hypotheticalBestRoutes(List<DepotDto> depots, List<OrderDto> orders, SettingsDto settings) {
        // Varianta ipotetică NU folosește lista reală de camioane din DemoScenarioService.
        // Ea construiește o flotă virtuală: pentru fiecare depozit care are produsul și pentru fiecare comandă,
        // există un camion candidat. OptaPlanner poate alege câte dintre aceste camioane folosește efectiv.
        // Astfel, dacă 5 camioane sunt mai bune decât 4, solverul are voie să creeze și să folosească al 5-lea.
        List<VehicleDto> virtualFleet = createUnlimitedHypotheticalFleet(depots, orders);
        List<RouteDto> solved = optaPlannerVehicleRouting(depots, orders, virtualFleet, settings.hypotheticalSolverSeconds(), settings)
                .stream().filter(r -> !r.stops().isEmpty()).toList();
        List<RouteDto> renamed = new ArrayList<>();
        int index = 1;
        for (RouteDto route : solved) renamed.add(renameHypotheticalRoute(route, index++));
        return renamed;
    }

    private List<VehicleDto> createUnlimitedHypotheticalFleet(List<DepotDto> depots, List<OrderDto> orders) {
        List<VehicleDto> fleet = new ArrayList<>();
        int code = 1;
        for (DepotDto depot : depots) {
            long compatibleOrders = orders.stream()
                    .filter(o -> depot.products().contains(o.requiredProduct()))
                    .count();
            // Un camion virtual per comandă compatibilă este suficient pentru a permite inclusiv cazul „un camion per livrare”.
            // Dacă depozitul nu are produsul, nu primește camion virtual pentru acea comandă, deci ipoteticul pleacă direct
            // dintr-un depozit care chiar are marfa.
            for (int i = 0; i < compatibleOrders; i++) {
                fleet.add(new VehicleDto(
                        "IPOTETIC-CANDIDAT-" + String.format("%03d", code),
                        "DRV-IPOTETIC",
                        "Șofer ipotetic",
                        depot.id(),
                        DemoScenarioService.STANDARD_TRUCK_CAPACITY_KG,
                        DemoScenarioService.STANDARD_TRUCK_CONSUMPTION,
                        DemoScenarioService.STANDARD_TRUCK_COST_RON_PER_KM
                ));
                code++;
            }
        }
        return fleet;
    }

    private RouteDto renameHypotheticalRoute(RouteDto route, int index) {
        String suffix = String.format("%02d", index);
        return new RouteDto("IPOTETIC-" + suffix, "Șofer ipotetic " + index, route.depot(), route.loadKg(), route.capacityKg(), route.distanceKm(), route.drivingMinutes(), route.serviceMinutes(), route.breakMinutes(), route.durationMinutes(), route.legalMaxDriveMinutes(), route.dailyRestMinutes(), route.fuelLiters(), route.costRon(), route.stops());
    }

    private List<OrderDto> improveSequence(List<OrderDto> input, DepotDto depot, long deadlineNanos) {
        if (input.size() < 3 || System.nanoTime() > deadlineNanos) return input;
        List<OrderDto> best = new ArrayList<>(input);
        double bestDistance = routeDistanceOrders(depot, best);
        boolean improved = true;
        while (improved && System.nanoTime() < deadlineNanos) {
            improved = false;
            for (int i = 0; i < best.size() - 1 && System.nanoTime() < deadlineNanos; i++) {
                for (int j = i + 1; j < best.size() && System.nanoTime() < deadlineNanos; j++) {
                    List<OrderDto> candidate = new ArrayList<>(best);
                    Collections.reverse(candidate.subList(i, j + 1));
                    double d = routeDistanceOrders(depot, candidate);
                    if (d + 0.001 < bestDistance) {
                        best = candidate; bestDistance = d; improved = true;
                    }
                }
            }
        }
        return best;
    }

    private double routeDistanceOrders(DepotDto depot, List<OrderDto> orders) {
        double total = 0, lat = depot.latitude(), lon = depot.longitude();
        for (OrderDto o : orders) {
            total += VehicleRoutingEasyScoreCalculator.haversine(lat, lon, o.latitude(), o.longitude());
            lat = o.latitude(); lon = o.longitude();
        }
        total += VehicleRoutingEasyScoreCalculator.haversine(lat, lon, depot.latitude(), depot.longitude());
        return total;
    }

    private List<RouteDto> optaPlannerVehicleRouting(List<DepotDto> depots, List<OrderDto> orders, List<VehicleDto> vehicles, int seconds, SettingsDto settings) {
        VehicleRoutingEasyScoreCalculator.DRIVER_DAILY_SALARY_RON = settings.driverDailySalaryRon();
        Map<String, DepotDto> depotById = depots.stream().collect(Collectors.toMap(DepotDto::id, d -> d));
        List<TruckAnchor> trucks = vehicles.stream().map(v -> {
            DepotDto d = depotById.get(v.depotId());
            return new TruckAnchor(v.code(), v.driverName(), v.depotId(), d.name(), new HashSet<>(d.products()), v.capacityKg(), v.consumptionLPer100Km(), v.costRonPerKm(), DemoScenarioService.LEGAL_DAILY_DRIVE_MINUTES, DemoScenarioService.LEGAL_BREAK_AFTER_MINUTES, DemoScenarioService.LEGAL_BREAK_DURATION_MINUTES, d.latitude(), d.longitude());
        }).toList();
        List<DeliveryVisit> visits = orders.stream().map(o -> new DeliveryVisit(o.id(), o.customerName(), o.address(), o.latitude(), o.longitude(), o.weightKg(), o.timeWindow(), o.windowStartMinute(), o.windowEndMinute(), o.serviceMinutes(), o.priority(), o.requiredProduct())).toList();

        VehicleRoutingSolution problem = new VehicleRoutingSolution(trucks, visits);
        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(VehicleRoutingSolution.class)
                .withEntityClasses(DeliveryVisit.class)
                .withEasyScoreCalculatorClass(VehicleRoutingEasyScoreCalculator.class)
                .withPhases(new ConstructionHeuristicPhaseConfig(), new LocalSearchPhaseConfig())
                .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(Math.max(1, seconds))));

        SolverFactory<VehicleRoutingSolution> solverFactory = SolverFactory.create(solverConfig);
        Solver<VehicleRoutingSolution> solver = solverFactory.buildSolver();
        VehicleRoutingSolution solved = solver.solve(problem);
        return buildRoutesFromSolution(solved, depotById, depots, settings).stream().filter(r -> !r.stops().isEmpty()).toList();
    }

    private List<RouteDto> initialRoutes(List<DepotDto> depots, List<OrderDto> orders, List<VehicleDto> vehicles, SettingsDto settings) {
        Map<String, DepotDto> depotById = depots.stream().collect(Collectors.toMap(DepotDto::id, d -> d));
        Map<VehicleDto, List<OrderDto>> buckets = new LinkedHashMap<>(); vehicles.forEach(v -> buckets.put(v, new ArrayList<>()));
        int cursor = 0;
        for (OrderDto order : orders) {
            VehicleDto selected = vehicles.get(cursor % vehicles.size());
            buckets.get(selected).add(order); cursor++;
        }
        return buildRoutes(depotById, depots, buckets, settings).stream().filter(r -> !r.stops().isEmpty()).toList();
    }

    private List<RouteDto> buildRoutesFromSolution(VehicleRoutingSolution solved, Map<String, DepotDto> depotById, List<DepotDto> depots, SettingsDto settings) {
        List<RouteDto> result = new ArrayList<>();
        for (TruckAnchor truck : solved.getTruckList()) {
            List<DeliveryVisit> chain = VehicleRoutingEasyScoreCalculator.extractChain(truck, solved.getVisitList());
            DepotDto startDepot = depotById.get(truck.getDepotId());
            List<RouteStopDto> stops = toAugmentedStops(chain, startDepot, depots, truck.getCapacityKg());
            result.add(routeFromStops(truck.getCode(), truck.getDriverName(), startDepot, stops, chain.stream().mapToDouble(DeliveryVisit::getWeightKg).sum(), truck.getCapacityKg(), truck.getConsumptionLPer100Km(), settings));
        }
        return result;
    }

    private List<RouteDto> buildRoutes(Map<String, DepotDto> depotById, List<DepotDto> depots, Map<VehicleDto, List<OrderDto>> buckets, SettingsDto settings) {
        List<RouteDto> result = new ArrayList<>();
        for (var entry : buckets.entrySet()) {
            VehicleDto vehicle = entry.getKey(); DepotDto startDepot = depotById.get(vehicle.depotId()); List<OrderDto> orders = entry.getValue();
            List<RouteStopDto> stops = toAugmentedStopsDto(orders, startDepot, depots, vehicle.capacityKg());
            result.add(routeFromStops(vehicle.code(), vehicle.driverName(), startDepot, stops, currentLoad(orders), vehicle.capacityKg(), vehicle.consumptionLPer100Km(), settings));
        }
        return result;
    }

    private RouteDto routeFromOrders(String code, String driverName, DepotDto depot, List<OrderDto> orders, SettingsDto settings) {
        List<RouteStopDto> stops = new ArrayList<>(); int sequence = 1;
        for (OrderDto o : orders) stops.add(new RouteStopDto(o.id(), sequence++, o.customerName(), o.address(), o.latitude(), o.longitude(), o.priority(), o.requiredProduct(), o.timeWindow(), "DELIVERY", null));
        return routeFromStops(code, driverName, depot, stops, currentLoad(orders), DemoScenarioService.STANDARD_TRUCK_CAPACITY_KG, DemoScenarioService.STANDARD_TRUCK_CONSUMPTION, settings);
    }

    private RouteDto routeFromStops(String code, String driverName, DepotDto depot, List<RouteStopDto> stops, double loadKg, double capacityKg, double consumption, SettingsDto settings) {
        double distance = routeDistanceWithStops(depot, stops);
        int driving = VehicleRoutingEasyScoreCalculator.drivingMinutes(distance);
        int service = stops.stream().filter(s -> "DELIVERY".equals(s.stopType())).mapToInt(this::serviceForStop).sum() + reloadStops(stops) * 12;
        int breaks = legalBreakMinutes(driving, DemoScenarioService.LEGAL_BREAK_AFTER_MINUTES, DemoScenarioService.LEGAL_BREAK_DURATION_MINUTES);
        int total = driving + service + breaks;
        double fuel = distance * consumption / 100.0;
        double driverCost = stops.isEmpty() ? 0 : settings.driverDailySalaryRon();
        double cost = fuel * settings.fuelPriceRonPerLiter() + distance * FIXED_OPERATIONAL_COST_RON_PER_KM + driverCost;
        return new RouteDto(code, driverName, depot, round(loadKg), capacityKg, round(distance), driving, service, breaks, total, DemoScenarioService.LEGAL_DAILY_DRIVE_MINUTES, DemoScenarioService.LEGAL_DAILY_REST_MINUTES, round(fuel), round(cost), stops);
    }

    private int serviceForStop(RouteStopDto stop) {
        return demo.orders().stream().filter(o -> Objects.equals(o.id(), stop.orderId())).mapToInt(OrderDto::serviceMinutes).findFirst().orElse(10);
    }

    private List<RouteStopDto> toAugmentedStops(List<DeliveryVisit> chain, DepotDto startDepot, List<DepotDto> depots, double capacityKg) {
        List<RouteStopDto> stops = new ArrayList<>(); DepotDto currentDepot = startDepot; double loadedKg = 0; int deliverySeq = 1;
        for (DeliveryVisit o : chain) {
            boolean needsOtherProduct = !currentDepot.products().contains(o.getRequiredProduct());
            boolean wouldExceedCapacity = loadedKg + o.getWeightKg() > capacityKg;
            if (needsOtherProduct || wouldExceedCapacity) {
                DepotDto reload = nearestDepotWithProduct(currentDepot.latitude(), currentDepot.longitude(), depots, o.getRequiredProduct());
                stops.add(depotStop(reload, "Încărcare " + o.getRequiredProduct()));
                currentDepot = reload; loadedKg = 0;
            }
            stops.add(new RouteStopDto(o.getId(), deliverySeq++, o.getCustomerName(), o.getAddress(), o.getLatitude(), o.getLongitude(), o.getPriority(), o.getRequiredProduct(), o.getTimeWindow(), "DELIVERY", null));
            loadedKg += o.getWeightKg();
        }
        return stops;
    }

    private List<RouteStopDto> toAugmentedStopsDto(List<OrderDto> orders, DepotDto startDepot, List<DepotDto> depots, double capacityKg) {
        List<RouteStopDto> stops = new ArrayList<>(); DepotDto currentDepot = startDepot; double loadedKg = 0; int deliverySeq = 1;
        for (OrderDto o : orders) {
            boolean needsOtherProduct = !currentDepot.products().contains(o.requiredProduct());
            boolean wouldExceedCapacity = loadedKg + o.weightKg() > capacityKg;
            if (needsOtherProduct || wouldExceedCapacity) {
                DepotDto reload = nearestDepotWithProduct(currentDepot.latitude(), currentDepot.longitude(), depots, o.requiredProduct());
                stops.add(depotStop(reload, "Încărcare " + o.requiredProduct()));
                currentDepot = reload; loadedKg = 0;
            }
            stops.add(new RouteStopDto(o.id(), deliverySeq++, o.customerName(), o.address(), o.latitude(), o.longitude(), o.priority(), o.requiredProduct(), o.timeWindow(), "DELIVERY", null));
            loadedKg += o.weightKg();
        }
        return stops;
    }

    private RouteStopDto depotStop(DepotDto depot, String reason) { return new RouteStopDto(null, 0, depot.name(), reason, depot.latitude(), depot.longitude(), "ÎNCĂRCARE", String.join(", ", depot.products()), "stoc disponibil", "DEPOT_LOAD", depot.id()); }
    private int reloadStops(List<RouteStopDto> stops) { return (int) stops.stream().filter(s -> "DEPOT_LOAD".equals(s.stopType())).count(); }
    private DepotDto nearestDepotWithProduct(double lat, double lon, List<DepotDto> depots, String product) { return depots.stream().filter(d -> d.products().contains(product)).min(Comparator.comparingDouble(d -> VehicleRoutingEasyScoreCalculator.haversine(lat, lon, d.latitude(), d.longitude()))).orElse(depots.get(0)); }

    private int legalBreakMinutes(int driving, int after, int duration) { return driving <= after ? 0 : ((driving - 1) / after) * duration; }
    private double routeDistanceWithStops(DepotDto startDepot, List<RouteStopDto> stops) { double total = 0, lat = startDepot.latitude(), lon = startDepot.longitude(); for (RouteStopDto stop : stops) { total += VehicleRoutingEasyScoreCalculator.haversine(lat, lon, stop.latitude(), stop.longitude()); lat = stop.latitude(); lon = stop.longitude(); } total += VehicleRoutingEasyScoreCalculator.haversine(lat, lon, startDepot.latitude(), startDepot.longitude()); return total; }

    private StatisticsDto statistics(List<RouteDto> initial, List<RouteDto> optimized, List<RouteDto> hypothetical) {
        double d1 = sumDistance(initial), d2 = sumDistance(optimized), d3 = sumDistance(hypothetical);
        int t1 = sumDuration(initial), t2 = sumDuration(optimized), t3 = sumDuration(hypothetical);
        double a1 = averageDeliveryMinutes(initial), a2 = averageDeliveryMinutes(optimized), a3 = averageDeliveryMinutes(hypothetical);
        double f1 = sumFuel(initial), f2 = sumFuel(optimized), f3 = sumFuel(hypothetical);
        double c1 = sumCost(initial), c2 = sumCost(optimized), c3 = sumCost(hypothetical);
        return new StatisticsDto(
                round(d1), round(d2), round(d3), round(d1-d2), round(d1-d3), pct(d1,d2), pct(d1,d3),
                t1, t2, t3, t1-t2, t1-t3, pct(t1,t2), pct(t1,t3),
                round(a1), round(a2), round(a3), round(a1-a2), round(a1-a3), pct(a1,a2), pct(a1,a3),
                round(f1), round(f2), round(f3), round(f1-f2), round(f1-f3),
                round(c1), round(c2), round(c3), round(c1-c2), round(c1-c3)
        );
    }

    private double averageDeliveryMinutes(List<RouteDto> routes) {
        double completionSum = 0;
        int deliveries = 0;
        for (RouteDto route : routes) {
            double lat = route.depot().latitude(), lon = route.depot().longitude();
            int elapsed = 0;
            for (RouteStopDto stop : route.stops()) {
                double leg = VehicleRoutingEasyScoreCalculator.haversine(lat, lon, stop.latitude(), stop.longitude());
                elapsed += VehicleRoutingEasyScoreCalculator.drivingMinutes(leg);
                if ("DELIVERY".equals(stop.stopType())) {
                    int start = windowStart(stop.timeWindow());
                    int absoluteClock = 8 * 60 + elapsed;
                    if (absoluteClock < start) elapsed += (start - absoluteClock);
                    elapsed += serviceForStop(stop);
                    completionSum += elapsed;
                    deliveries++;
                } else if ("DEPOT_LOAD".equals(stop.stopType())) {
                    elapsed += 12;
                }
                lat = stop.latitude(); lon = stop.longitude();
            }
        }
        return deliveries == 0 ? 0 : completionSum / deliveries;
    }

    private int windowStart(String window) {
        try {
            String[] hm = window.split("-")[0].trim().split(":");
            return Integer.parseInt(hm[0]) * 60 + Integer.parseInt(hm[1]);
        } catch (Exception ignored) {
            return 8 * 60;
        }
    }

    private double sumDistance(List<RouteDto> r){return r.stream().mapToDouble(RouteDto::distanceKm).sum();} private int sumDuration(List<RouteDto> r){return r.stream().mapToInt(RouteDto::durationMinutes).sum();} private double sumFuel(List<RouteDto> r){return r.stream().mapToDouble(RouteDto::fuelLiters).sum();} private double sumCost(List<RouteDto> r){return r.stream().mapToDouble(RouteDto::costRon).sum();}
    private double pct(double initial, double current) { return round(initial == 0 ? 0 : ((initial-current)/initial)*100); }
    private double currentLoad(List<OrderDto> orders) { return orders.stream().mapToDouble(OrderDto::weightKg).sum(); }
    private double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
