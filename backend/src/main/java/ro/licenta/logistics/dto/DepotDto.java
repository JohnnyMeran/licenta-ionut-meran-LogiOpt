package ro.licenta.logistics.dto;
import java.util.List;
public record DepotDto(String id, String name, double latitude, double longitude, List<String> products) {}
