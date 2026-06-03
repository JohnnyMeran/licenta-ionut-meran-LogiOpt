package ro.licenta.logistics.dto;
import java.util.List;
public record RouteDto(String vehicleCode, String driverName, DepotDto depot, double loadKg, double capacityKg, double distanceKm, int drivingMinutes, int serviceMinutes, int breakMinutes, int durationMinutes, int legalMaxDriveMinutes, int dailyRestMinutes, double fuelLiters, double costRon, List<RouteStopDto> stops) {}
