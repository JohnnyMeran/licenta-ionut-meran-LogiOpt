package ro.licenta.logistics.dto;

public record VehicleDto(String code, String driverId, String driverName, String depotId, double capacityKg, double consumptionLPer100Km, double costRonPerKm) {}
