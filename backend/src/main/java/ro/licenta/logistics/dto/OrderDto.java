package ro.licenta.logistics.dto;
public record OrderDto(Long id, String customerName, String address, double latitude, double longitude, double weightKg, String timeWindow, int windowStartMinute, int windowEndMinute, int serviceMinutes, String priority, String requiredProduct) {}
