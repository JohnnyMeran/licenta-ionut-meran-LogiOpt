package ro.licenta.logistics.solver;

import java.util.Set;

public class TruckAnchor implements Standstill {
    private String code;
    private String driverName;
    private String depotId;
    private String depotName;
    private Set<String> products;
    private double capacityKg;
    private double consumptionLPer100Km;
    private double costRonPerKm;
    private int maxDriveMinutes;
    private int breakAfterMinutes;
    private int breakDurationMinutes;
    private double latitude;
    private double longitude;

    public TruckAnchor() {}
    public TruckAnchor(String code, String driverName, String depotId, String depotName, Set<String> products, double capacityKg, double consumptionLPer100Km, double costRonPerKm, int maxDriveMinutes, int breakAfterMinutes, int breakDurationMinutes, double latitude, double longitude) {
        this.code = code; this.driverName = driverName; this.depotId = depotId; this.depotName = depotName; this.products = products; this.capacityKg = capacityKg; this.consumptionLPer100Km = consumptionLPer100Km; this.costRonPerKm = costRonPerKm; this.maxDriveMinutes = maxDriveMinutes; this.breakAfterMinutes = breakAfterMinutes; this.breakDurationMinutes = breakDurationMinutes; this.latitude = latitude; this.longitude = longitude;
    }
    public String getCode() { return code; }
    public String getDriverName() { return driverName; }
    public String getDepotId() { return depotId; }
    public String getDepotName() { return depotName; }
    public Set<String> getProducts() { return products; }
    public double getCapacityKg() { return capacityKg; }
    public double getConsumptionLPer100Km() { return consumptionLPer100Km; }
    public double getCostRonPerKm() { return costRonPerKm; }
    public int getMaxDriveMinutes() { return maxDriveMinutes; }
    public int getBreakAfterMinutes() { return breakAfterMinutes; }
    public int getBreakDurationMinutes() { return breakDurationMinutes; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
}
