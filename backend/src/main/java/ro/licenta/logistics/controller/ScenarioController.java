package ro.licenta.logistics.controller;

import org.springframework.web.bind.annotation.*;
import ro.licenta.logistics.dto.*;
import ro.licenta.logistics.service.DemoScenarioService;
import ro.licenta.logistics.service.LiveOptimizationService;
import ro.licenta.logistics.service.OptimizationService;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ScenarioController {
    private final OptimizationService service;
    private final DemoScenarioService demo;
    private final LiveOptimizationService live;
    public ScenarioController(OptimizationService service, DemoScenarioService demo, LiveOptimizationService live) { this.service = service; this.demo = demo; this.live = live; }

    @GetMapping("/scenario/demo")
    public ScenarioDto demoScenario() { return service.scenario(false); }

    @PostMapping("/scenario/reset")
    public ScenarioDto resetScenario() { demo.reset(); return service.scenario(false); }

    // Fiecare scenariu se optimizează separat: butonul din interfață rulează doar planul selectat pe hartă.
    @PostMapping("/optimize")
    public ScenarioDto optimize() { return service.optimize(false); }

    @PostMapping("/optimize/hypothetical")
    public ScenarioDto hypothetical() { return service.optimize(true); }

    @PostMapping("/optimize/live/start")
    public LiveOptimizationStatusDto startLive(@RequestParam(defaultValue = "false") boolean hypothetical) { return live.start(hypothetical); }

    @PostMapping("/optimize/live/pause")
    public LiveOptimizationStatusDto pauseLive() { return live.pause(); }

    @PostMapping("/optimize/live/resume")
    public LiveOptimizationStatusDto resumeLive() { return live.resume(); }

    @PostMapping("/optimize/live/stop")
    public LiveOptimizationStatusDto stopLive() { return live.stop(); }

    @GetMapping("/optimize/live/status")
    public LiveOptimizationStatusDto liveStatus() { return live.status(); }

    @GetMapping("/settings")
    public SettingsDto settings() { return demo.settings(); }

    @PutMapping("/settings")
    public ScenarioDto updateSettings(@RequestBody SettingsDto settings) {
        demo.updateSettings(settings);
        return service.scenario(false);
    }

    // Datele invalide sunt refuzate cu 400 și un mesaj citibil, nu cu un 500 opac.
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public java.util.Map<String, String> onInvalidInput(IllegalArgumentException e) {
        return java.util.Map.of("error", e.getMessage());
    }

    @PostMapping("/shipments")
    public ScenarioDto addShipment(@RequestBody ShipmentDto shipment) { demo.addShipment(shipment); return service.scenario(false); }
    @DeleteMapping("/shipments/{id}")
    public ScenarioDto deleteShipment(@PathVariable long id) { demo.deleteShipment(id); return service.scenario(false); }

    @PostMapping("/drivers")
    public ScenarioDto addDriver(@RequestBody DriverDto driver) { demo.addDriver(driver); return service.scenario(false); }
    @DeleteMapping("/drivers/{id}")
    public ScenarioDto deleteDriver(@PathVariable String id) { demo.deleteDriver(id); return service.scenario(false); }

    @PostMapping("/vehicles")
    public ScenarioDto addVehicle(@RequestBody VehicleDto vehicle) { demo.addVehicle(vehicle); return service.scenario(false); }
    @DeleteMapping("/vehicles/{code}")
    public ScenarioDto deleteVehicle(@PathVariable String code) { demo.deleteVehicle(code); return service.scenario(false); }
}
