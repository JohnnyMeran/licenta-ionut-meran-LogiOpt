package ro.licenta.logistics.dto;

// Parametrii economici ai simulării. Amortizarea nu este o valoare fixă: se calculează liniar din
// prețul de achiziție, valoarea reziduală, durata de viață și numărul de zile lucrătoare pe an.
public record SettingsDto(
    int realSolverSeconds,
    int hypotheticalSolverSeconds,
    double fuelPriceRonPerLiter,
    double driverDailySalaryRon,
    double vanPurchasePriceRon,
    double truckPurchasePriceRon,
    int vehicleUsefulLifeYears,
    double residualValuePercent,
    int workingDaysPerYear,
    double fleetReservePercent
) {
    // Amortizare liniară pe zi de exploatare: (preț − valoare reziduală) / (ani de viață × zile lucrătoare).
    public double vanDailyAmortizationRon() { return dailyAmortization(vanPurchasePriceRon); }
    public double truckDailyAmortizationRon() { return dailyAmortization(truckPurchasePriceRon); }

    public double vanResidualValueRon() { return vanPurchasePriceRon * residualValuePercent / 100.0; }
    public double truckResidualValueRon() { return truckPurchasePriceRon * residualValuePercent / 100.0; }

    private double dailyAmortization(double purchasePrice) {
        int years = Math.max(1, vehicleUsefulLifeYears);
        int days = Math.max(1, workingDaysPerYear);
        double residual = purchasePrice * Math.max(0, Math.min(100, residualValuePercent)) / 100.0;
        return Math.max(0, purchasePrice - residual) / (years * (double) days);
    }
}
