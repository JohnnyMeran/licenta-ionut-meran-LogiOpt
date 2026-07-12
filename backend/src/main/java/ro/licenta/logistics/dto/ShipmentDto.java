package ro.licenta.logistics.dto;

// Un colet de curierat: se ridică de la expeditor (pickup), ajunge la hub-ul de origine,
// pleacă prin linehaul spre hub-ul orașului destinație și se livrează la destinatar.
// code = codul de identificare al coletului (LO1000001, LO1000002, ...). Firma nu procesează scrisori
// de transport aerian: codul servește exclusiv la regăsirea coletului în listă.
public record ShipmentDto(
    Long id,
    String code,
    String senderName,
    String recipientName,
    String pickupAddress,
    String pickupCity,
    double pickupLat,
    double pickupLon,
    String originHubId,
    String deliveryAddress,
    String deliveryCity,
    double deliveryLat,
    double deliveryLon,
    String destHubId,
    double weightKg,
    String timeWindow,
    int windowStartMinute,
    int windowEndMinute,
    int serviceMinutes,
    String priority,
    double tariffRon
) {}
