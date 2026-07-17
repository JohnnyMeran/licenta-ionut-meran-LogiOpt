package ro.licenta.logistics.service;

import org.optaplanner.core.api.solver.Solver;
import org.springframework.stereotype.Service;
import ro.licenta.logistics.dto.*;
import ro.licenta.logistics.solver.*;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class LiveOptimizationService {
    private static final int SOLVER_SLICE_SECONDS = 2;

    private final DemoScenarioService demo;
    private final OptimizationService optimizationService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Object lock = new Object();
    private volatile Solver<VehicleRoutingSolution> currentSolver;
    private VehicleRoutingSolution problem;
    private VehicleRoutingSolution bestSolution;
    private List<RouteDto> bestRoutes = List.of();
    private String status = "IDLE";
    private String mode = "real";
    private int iteration = 0;

    private volatile int generation = 0;

    public LiveOptimizationService(DemoScenarioService demo, OptimizationService optimizationService) {
        this.demo = demo;
        this.optimizationService = optimizationService;
    }

    public LiveOptimizationStatusDto start(boolean hypothetical) {
        synchronized (lock) {
            if ("RUNNING".equals(status)) return status();

            generation++;                    // invalidează orice rezultat al buclei anterioare, încă în lucru
            terminateCurrentSolver();
            mode = hypothetical ? "hypothetical" : "real";
            iteration = 0;
            bestRoutes = List.of();
            bestSolution = null;

            List<DepotDto> depots = demo.depots();
            List<ShipmentDto> shipments = demo.shipments();
            List<VehicleDto> vehicles = hypothetical
                    ? optimizationService.createUnlimitedHypotheticalFleet(depots, shipments)
                    : demo.vans();

            problem = optimizationService.vehicleRoutingProblem(depots, shipments, vehicles, demo.settings());
            status = "RUNNING";
            int runGeneration = generation;
            executor.submit(() -> solveLoop(runGeneration));
            return status();
        }
    }

    public LiveOptimizationStatusDto pause() {
        synchronized (lock) {
            if ("RUNNING".equals(status)) {
                status = "PAUSED";
                terminateCurrentSolver();
            }
            return status();
        }
    }

    public LiveOptimizationStatusDto resume() {
        synchronized (lock) {
            if ("PAUSED".equals(status) || "STOPPED".equals(status)) {
                status = "RUNNING";
                generation++;
                int runGeneration = generation;
                executor.submit(() -> solveLoop(runGeneration));
            }
            return status();
        }
    }

    public LiveOptimizationStatusDto stop() {
        synchronized (lock) {
            if (!"IDLE".equals(status)) {
                status = "STOPPED";
                terminateCurrentSolver();
            }
            return status();
        }
    }

    public LiveOptimizationStatusDto status() {
        synchronized (lock) {
            ScenarioDto scenario = bestRoutes.isEmpty()
                    ? null
                    : optimizationService.scenarioFromLiveRoutes(bestRoutes, "hypothetical".equals(mode));
            return new LiveOptimizationStatusDto(
                    status,
                    mode,
                    iteration,
                    sumDistance(bestRoutes),
                    sumCost(bestRoutes),
                    sumDuration(bestRoutes),
                    scenario
            );
        }
    }

    private void solveLoop(int runGeneration) {
        while (true) {
            VehicleRoutingSolution input;
            SettingsDto settings = demo.settings();
            synchronized (lock) {
                if (runGeneration != generation || !"RUNNING".equals(status)) return;
                input = cloneSolution(bestSolution != null ? bestSolution : problem);
            }

            Solver<VehicleRoutingSolution> solver = optimizationService.buildVehicleRoutingSolver(SOLVER_SLICE_SECONDS, settings);
            currentSolver = solver;
            solver.addEventListener(event -> updateBestSolution(event.getNewBestSolution(), runGeneration));

            VehicleRoutingSolution solved = solver.solve(input);
            updateBestSolution(solved, runGeneration);

            synchronized (lock) {
                if (runGeneration != generation) return;   // între timp a pornit altă rulare
                currentSolver = null;
                if ("RUNNING".equals(status)) {
                    iteration++;
                } else {
                    return;
                }
            }
        }
    }

    private void updateBestSolution(VehicleRoutingSolution solution, int runGeneration) {
        if (solution == null || runGeneration != generation) return;
        VehicleRoutingSolution copy = cloneSolution(solution);
        List<RouteDto> routes = optimizationService.routesFromSolution(copy, demo.depots(), demo.settings());
        synchronized (lock) {
            if (runGeneration != generation) return;   // rezultat dintr-o rulare abandonată: se aruncă
            bestSolution = copy;
            bestRoutes = routes;
        }
    }

    private VehicleRoutingSolution cloneSolution(VehicleRoutingSolution source) {
        if (source == null) return null;

        Map<String, TruckAnchor> truckByCode = new LinkedHashMap<>();
        for (TruckAnchor truck : source.getTruckList()) {
            truckByCode.put(truck.getCode(), new TruckAnchor(
                    truck.getCode(),
                    truck.getDriverName(),
                    truck.getDepotId(),
                    truck.getDepotName(),
                    new HashSet<>(truck.getProducts()),
                    truck.getCapacityKg(),
                    truck.getConsumptionLPer100Km(),
                    truck.getCostRonPerKm(),
                    truck.getFixedDailyCostRon(),
                    truck.getMaxDriveMinutes(),
                    truck.getBreakAfterMinutes(),
                    truck.getBreakDurationMinutes(),
                    truck.getLatitude(),
                    truck.getLongitude(),
                    truck.getSpeedKmh()
            ));
        }

        Map<Long, DeliveryVisit> visitById = new LinkedHashMap<>();
        for (DeliveryVisit visit : source.getVisitList()) {
            visitById.put(visit.getId(), new DeliveryVisit(
                    visit.getId(),
                    visit.getShipmentId(),
                    visit.getName(),
                    visit.getAddress(),
                    visit.getCity(),
                    visit.getLatitude(),
                    visit.getLongitude(),
                    visit.getWeightKg(),
                    visit.getTimeWindow(),
                    visit.getWindowStartMinute(),
                    visit.getWindowEndMinute(),
                    visit.getServiceMinutes(),
                    visit.getPriority(),
                    visit.getTaskType(),
                    visit.getHubId()
            ));
        }

        for (DeliveryVisit sourceVisit : source.getVisitList()) {
            DeliveryVisit targetVisit = visitById.get(sourceVisit.getId());
            Standstill previous = sourceVisit.getPreviousStandstill();
            if (previous instanceof TruckAnchor truck) {
                targetVisit.setPreviousStandstill(truckByCode.get(truck.getCode()));
            } else if (previous instanceof DeliveryVisit previousVisit) {
                targetVisit.setPreviousStandstill(visitById.get(previousVisit.getId()));
            }
            TruckAnchor sourceTruck = sourceVisit.getTruck();
            if (sourceTruck != null) {
                targetVisit.setTruck(truckByCode.get(sourceTruck.getCode()));
            }
        }

        VehicleRoutingSolution clone = new VehicleRoutingSolution(
                new ArrayList<>(truckByCode.values()),
                new ArrayList<>(visitById.values())
        );
        clone.setScore(source.getScore());
        return clone;
    }

    private void terminateCurrentSolver() {
        Solver<VehicleRoutingSolution> solver = currentSolver;
        if (solver != null) solver.terminateEarly();
    }

    private double sumDistance(List<RouteDto> routes) {
        return round(routes.stream().mapToDouble(RouteDto::distanceKm).sum());
    }

    private double sumCost(List<RouteDto> routes) {
        return round(routes.stream().mapToDouble(RouteDto::costRon).sum());
    }

    private int sumDuration(List<RouteDto> routes) {
        return routes.stream().mapToInt(RouteDto::durationMinutes).sum();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
