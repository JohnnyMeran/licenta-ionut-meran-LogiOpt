package ro.licenta.logistics.dto;
import java.util.List;
public record ScenarioDto(List<DepotDto> depots, DepotDto depot, List<OrderDto> orders, List<DriverDto> drivers, List<VehicleDto> vehicles, List<RouteDto> routes, List<RouteDto> hypotheticalRoutes, StatisticsDto statistics, SettingsDto settings) {}
