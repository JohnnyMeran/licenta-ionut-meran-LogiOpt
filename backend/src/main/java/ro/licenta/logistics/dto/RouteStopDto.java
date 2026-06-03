package ro.licenta.logistics.dto;

public record RouteStopDto(
    Long orderId,
    int sequence,
    String customerName,
    String address,
    double latitude,
    double longitude,
    String priority,
    String requiredProduct,
    String timeWindow,
    String stopType,
    String depotId
) {}
