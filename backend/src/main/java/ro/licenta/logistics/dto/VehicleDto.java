package ro.licenta.logistics.dto;

// kind: "VAN" (curier regional: ridicări + livrări) sau "LINEHAUL" (TIR între hub-uri).
public record VehicleDto(String code, String driverId, String driverName, String depotId, String kind,
                         double capacityKg, double consumptionLPer100Km, double costRonPerKm) {
}
