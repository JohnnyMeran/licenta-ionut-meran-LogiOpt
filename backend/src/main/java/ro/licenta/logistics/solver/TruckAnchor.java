package ro.licenta.logistics.solver;

import java.util.Set;

// Costurile vehiculului fac parte din model, nu din niște constante globale:
//   costRonPerKm      = combustibil + taxe de drum + service, în RON pe kilometru
//   fixedDailyCostRon = salariile echipajului + amortizarea, plătite doar dacă vehiculul iese pe traseu
// Astfel funcția de scor poate calcula exact costul pe care îl raportează aplicația, iar în scenariul ipotetic
// („resurse nelimitate") un vehicul cu fixedDailyCostRon = 0 este gratuit — de aceea solverul chiar are un motiv
// să folosească mai multe dube ca să livreze mai repede.
public class TruckAnchor implements Standstill {
    private String code;
    private String driverName;
    private String depotId;
    private String depotName;
    private Set<String> products;
    private double capacityKg;
    private double consumptionLPer100Km;
    private double costRonPerKm;
    private double fixedDailyCostRon;
    private int maxDriveMinutes;
    private int breakAfterMinutes;
    private int breakDurationMinutes;
    private double latitude;
    private double longitude;
    private double speedKmh;

    public TruckAnchor() {}
    public TruckAnchor(String code, String driverName, String depotId, String depotName, Set<String> products, double capacityKg, double consumptionLPer100Km, double costRonPerKm, double fixedDailyCostRon, int maxDriveMinutes, int breakAfterMinutes, int breakDurationMinutes, double latitude, double longitude, double speedKmh) {
        this.code = code; this.driverName = driverName; this.depotId = depotId; this.depotName = depotName; this.products = products; this.capacityKg = capacityKg; this.consumptionLPer100Km = consumptionLPer100Km; this.costRonPerKm = costRonPerKm; this.fixedDailyCostRon = fixedDailyCostRon; this.maxDriveMinutes = maxDriveMinutes; this.breakAfterMinutes = breakAfterMinutes; this.breakDurationMinutes = breakDurationMinutes; this.latitude = latitude; this.longitude = longitude; this.speedKmh = speedKmh;
    }
    public String getCode() { return code; }
    public String getDriverName() { return driverName; }
    public String getDepotId() { return depotId; }
    public String getDepotName() { return depotName; }
    public Set<String> getProducts() { return products; }
    public double getCapacityKg() { return capacityKg; }
    public double getConsumptionLPer100Km() { return consumptionLPer100Km; }
    public double getCostRonPerKm() { return costRonPerKm; }
    public double getFixedDailyCostRon() { return fixedDailyCostRon; }
    public int getMaxDriveMinutes() { return maxDriveMinutes; }
    public int getBreakAfterMinutes() { return breakAfterMinutes; }
    public int getBreakDurationMinutes() { return breakDurationMinutes; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getSpeedKmh() { return speedKmh; }
}
