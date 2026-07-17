package ro.licenta.logistics.dto;

// Dimensionarea flotei la nivel de hub: câte dube deține hub-ul, câte a folosit efectiv ziua optimizată,
// câte îi sunt strict necesare (capacitate + timp legal de lucru) și câte recomandă LogiOpt (cu rezervă).
public record HubFleetDto(
        String city,
        int vansOwned,
        int vansUsed,
        int vansRequired,
        int vansRecommended,
        int delta,
        double loadKg,
        double capacityKg,
        double utilizationPercent,
        String reason
) {
}
