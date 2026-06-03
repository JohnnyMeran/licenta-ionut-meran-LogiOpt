package ro.licenta.logistics.controller;

import org.springframework.web.bind.annotation.*;
import ro.licenta.logistics.dto.*;
import ro.licenta.logistics.service.DemoScenarioService;
import ro.licenta.logistics.service.OptimizationService;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ScenarioController {
    private final OptimizationService service;
    private final DemoScenarioService demo;
    public ScenarioController(OptimizationService service, DemoScenarioService demo) { this.service = service; this.demo = demo; }

    @GetMapping("/scenario/demo")
    public ScenarioDto demoScenario() { return service.scenario(false); }

    @PostMapping("/scenario/reset")
    public ScenarioDto resetScenario() { demo.reset(); return service.scenario(false); }

    @PostMapping("/optimize")
    public ScenarioDto optimize() { return service.scenario(true); }

    @PostMapping("/optimize/hypothetical")
    public ScenarioDto hypothetical() { return service.scenarioWithHypothetical(); }

    @GetMapping("/settings")
    public SettingsDto settings() { return demo.settings(); }

    @PutMapping("/settings")
    public ScenarioDto updateSettings(@RequestBody SettingsDto settings) {
        demo.updateSettings(settings);
        return service.scenario(false);
    }

    @PostMapping("/orders")
    public ScenarioDto addOrder(@RequestBody OrderDto order) { demo.addOrder(order); return service.scenario(false); }
    @DeleteMapping("/orders/{id}")
    public ScenarioDto deleteOrder(@PathVariable long id) { demo.deleteOrder(id); return service.scenario(false); }

    @PostMapping("/drivers")
    public ScenarioDto addDriver(@RequestBody DriverDto driver) { demo.addDriver(driver); return service.scenario(false); }
    @DeleteMapping("/drivers/{id}")
    public ScenarioDto deleteDriver(@PathVariable String id) { demo.deleteDriver(id); return service.scenario(false); }

    @PostMapping("/vehicles")
    public ScenarioDto addVehicle(@RequestBody VehicleDto vehicle) { demo.addVehicle(vehicle); return service.scenario(false); }
    @DeleteMapping("/vehicles/{code}")
    public ScenarioDto deleteVehicle(@PathVariable String code) { demo.deleteVehicle(code); return service.scenario(false); }
}
