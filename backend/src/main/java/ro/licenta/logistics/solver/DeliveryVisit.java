package ro.licenta.logistics.solver;

import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.variable.AnchorShadowVariable;
import org.optaplanner.core.api.domain.variable.PlanningVariable;
import org.optaplanner.core.api.domain.variable.PlanningVariableGraphType;

@PlanningEntity
public class DeliveryVisit implements Standstill {
    private Long id;
    private String customerName;
    private String address;
    private double latitude;
    private double longitude;
    private double weightKg;
    private String timeWindow;
    private int windowStartMinute;
    private int windowEndMinute;
    private int serviceMinutes;
    private String priority;
    private String requiredProduct;

    @PlanningVariable(valueRangeProviderRefs = {"truckRange", "visitRange"}, graphType = PlanningVariableGraphType.CHAINED)
    private Standstill previousStandstill;

    @AnchorShadowVariable(sourceVariableName = "previousStandstill")
    private TruckAnchor truck;

    public DeliveryVisit() {}
    public DeliveryVisit(Long id, String customerName, String address, double latitude, double longitude, double weightKg, String timeWindow, int windowStartMinute, int windowEndMinute, int serviceMinutes, String priority, String requiredProduct) {
        this.id = id; this.customerName = customerName; this.address = address; this.latitude = latitude; this.longitude = longitude; this.weightKg = weightKg; this.timeWindow = timeWindow; this.windowStartMinute = windowStartMinute; this.windowEndMinute = windowEndMinute; this.serviceMinutes = serviceMinutes; this.priority = priority; this.requiredProduct = requiredProduct;
    }
    public String getCode() { return "CMD-" + id; }
    public Long getId() { return id; }
    public String getCustomerName() { return customerName; }
    public String getAddress() { return address; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getWeightKg() { return weightKg; }
    public String getTimeWindow() { return timeWindow; }
    public int getWindowStartMinute() { return windowStartMinute; }
    public int getWindowEndMinute() { return windowEndMinute; }
    public int getServiceMinutes() { return serviceMinutes; }
    public String getPriority() { return priority; }
    public String getRequiredProduct() { return requiredProduct; }
    public Standstill getPreviousStandstill() { return previousStandstill; }
    public void setPreviousStandstill(Standstill previousStandstill) { this.previousStandstill = previousStandstill; }
    public TruckAnchor getTruck() { return truck; }
    public void setTruck(TruckAnchor truck) { this.truck = truck; }
}
