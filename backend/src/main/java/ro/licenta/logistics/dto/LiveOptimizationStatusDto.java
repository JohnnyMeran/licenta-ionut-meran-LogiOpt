package ro.licenta.logistics.dto;

public record LiveOptimizationStatusDto(
        String status,
        String mode,
        int iteration,
        double bestDistanceKm,
        double bestCostRon,
        int bestDurationMinutes,
        ScenarioDto scenario
) {
}
