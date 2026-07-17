package ro.licenta.logistics.dto;

// Descompunerea costului zilnic: combustibil, șoferi, amortizarea vehiculelor rulate, service/mentenanță
// și alte costuri operaționale (taxe drum etc.).
//
// idleAmortizationRon este partea pe care optimizarea rutelor NU o poate elimina: un vehicul deținut se
// depreciază chiar dacă rămâne în curte. Singurul mod de a scăpa de ea este redimensionarea flotei —
// de aceea totalWithIdleRon (costul real de proprietate) este mai mare decât totalRon (costul rulării).
public record CostBreakdownDto(
        double fuelRon,
        double driverRon,
        double amortizationRon,
        double idleAmortizationRon,
        double serviceRon,
        double operationalRon,
        double totalRon,
        double totalWithIdleRon,
        int vansUsed,
        int trucksUsed,
        int vansIdle,
        int trucksIdle
) {
}
