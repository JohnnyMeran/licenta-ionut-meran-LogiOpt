package ro.licenta.logistics.dto;

import java.util.List;

// Recomandarea LogiOpt: cu câte vehicule ar trebui redusă sau majorată flota, ce impact financiar are
// decizia (amortizare economisită / capital investit) și motivele din spatele ei.
//
// action: "REDUCE" | "EXTINDE" | "REECHILIBREAZĂ" | "MENȚINE"
// delta > 0 → mai trebuie cumpărate vehicule; delta < 0 → există surplus care poate fi vândut.
public record FleetRecommendationDto(
    int currentVans,
    int currentTrucks,
    int currentDrivers,
    int usedVans,
    int usedTrucks,
    int requiredVans,
    int requiredTrucks,
    int recommendedVans,
    int recommendedTrucks,
    int recommendedDrivers,
    int vanDelta,
    int truckDelta,
    int driverDelta,
    String action,
    String headline,
    double reservePercent,
    double vanDailyAmortizationRon,
    double truckDailyAmortizationRon,
    double idleAmortizationPerDayRon,
    double annualSavingRon,
    double resaleValueRon,
    double investmentRon,
    double annualExtraAmortizationRon,
    double fleetUtilizationPercent,
    List<String> reasons,
    List<HubFleetDto> perHub,
    String note
) {}
