package ro.licenta.logistics.solver;

import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.variable.AnchorShadowVariable;
import org.optaplanner.core.api.domain.variable.PlanningVariable;
import org.optaplanner.core.api.domain.variable.PlanningVariableGraphType;

// O "vizită" pentru solver reprezintă o operațiune de curierat la o adresă:
// fie o RIDICARE (PICKUP) a unui colet, fie o LIVRARE (DELIVERY). Fiecare vizită
// aparține unui hub regional (hubId) și poate fi deservită doar de dubele acelui hub.
@PlanningEntity
public class DeliveryVisit implements Standstill {
    private Long id;
    private Long shipmentId;
    private String name;
    private String address;
    private String city;
    private double latitude;
    private double longitude;
    private double weightKg;
    private String timeWindow;
    private int windowStartMinute;
    private int windowEndMinute;
    private int serviceMinutes;
    private String priority;
    private String taskType;
    private String hubId;

    @PlanningVariable(valueRangeProviderRefs = {"truckRange", "visitRange"}, graphType = PlanningVariableGraphType.CHAINED)
    private Standstill previousStandstill;

    @AnchorShadowVariable(sourceVariableName = "previousStandstill")
    private TruckAnchor truck;

    public DeliveryVisit() {
    }

    public DeliveryVisit(Long id, Long shipmentId, String name, String address, String city, double latitude, double longitude, double weightKg, String timeWindow, int windowStartMinute, int windowEndMinute, int serviceMinutes, String priority, String taskType, String hubId) {
        this.id = id;
        this.shipmentId = shipmentId;
        this.name = name;
        this.address = address;
        this.city = city;
        this.latitude = latitude;
        this.longitude = longitude;
        this.weightKg = weightKg;
        this.timeWindow = timeWindow;
        this.windowStartMinute = windowStartMinute;
        this.windowEndMinute = windowEndMinute;
        this.serviceMinutes = serviceMinutes;
        this.priority = priority;
        this.taskType = taskType;
        this.hubId = hubId;
    }

    public String getCode() {
        return "TASK-" + id;
    }

    public Long getId() {
        return id;
    }

    public Long getShipmentId() {
        return shipmentId;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getCity() {
        return city;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public String getTimeWindow() {
        return timeWindow;
    }

    public int getWindowStartMinute() {
        return windowStartMinute;
    }

    public int getWindowEndMinute() {
        return windowEndMinute;
    }

    public int getServiceMinutes() {
        return serviceMinutes;
    }

    public String getPriority() {
        return priority;
    }

    public String getTaskType() {
        return taskType;
    }

    public String getHubId() {
        return hubId;
    }

    public Standstill getPreviousStandstill() {
        return previousStandstill;
    }

    public void setPreviousStandstill(Standstill previousStandstill) {
        this.previousStandstill = previousStandstill;
    }

    public TruckAnchor getTruck() {
        return truck;
    }

    public void setTruck(TruckAnchor truck) {
        this.truck = truck;
    }
}
