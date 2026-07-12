package ro.licenta.logistics.dto;
import java.util.List;
public record ScenarioDto(List<DepotDto> depots, DepotDto depot, List<ShipmentDto> shipments, List<DriverDto> drivers, List<VehicleDto> vehicles, List<RouteDto> initialRoutes, List<RouteDto> routes, List<RouteDto> hypotheticalRoutes, StatisticsDto statistics, List<MonthlyReportDto> monthlyReports, CostAnalysisDto costAnalysis, SettingsDto settings) {}
