package ro.licenta.logistics.dto;

// stopType: "PICKUP" (ridicare colet), "DELIVERY" (livrare colet), "HUB" (nod hub linehaul).
public record RouteStopDto(
        Long shipmentId,
        int sequence,
        String name,
        String address,
        String city,
        double latitude,
        double longitude,
        String priority,
        String timeWindow,
        String stopType
) {
}
