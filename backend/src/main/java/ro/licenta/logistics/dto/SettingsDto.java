package ro.licenta.logistics.dto;

public record SettingsDto(int realSolverSeconds, int hypotheticalSolverSeconds, double fuelPriceRonPerLiter, double driverDailySalaryRon) {}
