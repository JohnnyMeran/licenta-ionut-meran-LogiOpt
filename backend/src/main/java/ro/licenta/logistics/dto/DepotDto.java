package ro.licenta.logistics.dto;

import java.util.List;

// Hub regional al rețelei de curierat (fostul "depozit"). Fiecare oraș mare are un hub
// unde se colectează coletele ridicate și de unde pleacă livrările pe ultima milă.
public record DepotDto(String id, String name, String city, double latitude, double longitude, List<String> coverage) {
}
