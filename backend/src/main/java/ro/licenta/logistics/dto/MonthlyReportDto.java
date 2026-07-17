package ro.licenta.logistics.dto;

// Raport estimativ pe lună. Costul operațional (combustibil, șoferi, service, taxe) este separat de
// amortizarea flotei: prima scade când optimizezi rutele, a doua scade doar dacă redimensionezi flota.
// profitRon este profitul real, după amortizare — indicatorul pe care îl urmărește contabilitatea.
public record MonthlyReportDto(
        String label,
        int year,
        int shipments,
        double revenueRon,
        double baselineCostRon,
        double optimizedCostRon,
        double amortizationRon,
        double savedByLogiOptRon,
        double profitBeforeAmortizationRon,
        double profitRon
) {
}
