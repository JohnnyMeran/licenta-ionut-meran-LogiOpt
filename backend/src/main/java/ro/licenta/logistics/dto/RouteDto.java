package ro.licenta.logistics.dto;
import java.util.List;

// kind: "REGIONAL" (dubă de curier cu ridicări + livrări în jurul unui hub) sau "LINEHAUL" (transfer între hub-uri).
//
// loadKg     = greutatea TOTALĂ manipulată într-o zi (ridicări + livrări). Este o măsură de volum de muncă.
// peakLoadKg = greutatea maximă aflată efectiv în vehicul la un moment dat. Doar ACEASTA se compară cu capacitatea:
//              duba pleacă din hub cu coletele de livrat, le lasă pe traseu și ridică altele, deci nu cară niciodată
//              simultan tot ce a manipulat în ziua respectivă.
public record RouteDto(String vehicleCode, String driverName, DepotDto depot, String kind, double loadKg, double peakLoadKg, double capacityKg, double distanceKm, int drivingMinutes, int serviceMinutes, int breakMinutes, int durationMinutes, int legalMaxDriveMinutes, int dailyRestMinutes, double fuelLiters, double costRon, List<RouteStopDto> stops) {}
