package ro.licenta.logistics.dto;

public record StatisticsDto(
    double initialDistanceKm, double optimizedDistanceKm, double hypotheticalDistanceKm,
    double distanceSavedKm, double hypotheticalDistanceSavedKm, double distanceImprovementPercent, double hypotheticalDistanceImprovementPercent,
    int initialDurationMinutes, int optimizedDurationMinutes, int hypotheticalDurationMinutes,
    int durationSavedMinutes, int hypotheticalDurationSavedMinutes, double durationImprovementPercent, double hypotheticalDurationImprovementPercent,
    double initialAverageDeliveryMinutes, double optimizedAverageDeliveryMinutes, double hypotheticalAverageDeliveryMinutes,
    double averageDeliverySavedMinutes, double hypotheticalAverageDeliverySavedMinutes, double averageDeliveryImprovementPercent, double hypotheticalAverageDeliveryImprovementPercent,
    double initialFuelLiters, double optimizedFuelLiters, double hypotheticalFuelLiters,
    double fuelSavedLiters, double hypotheticalFuelSavedLiters,
    double initialCostRon, double optimizedCostRon, double hypotheticalCostRon,
    double costSavedRon, double hypotheticalCostSavedRon,
    double totalRevenueRon, double baselineProfitRon, double optimizedProfitRon, double moneySavedByLogiOptRon
) {}
