package ro.licenta.logistics.solver;

import org.optaplanner.core.api.domain.solution.PlanningEntityCollectionProperty;
import org.optaplanner.core.api.domain.solution.PlanningScore;
import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.solution.ProblemFactCollectionProperty;
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;

import java.util.*;

@PlanningSolution
public class VehicleRoutingSolution {
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "truckRange")
    private List<TruckAnchor> truckList;

    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "visitRange")
    private List<DeliveryVisit> visitList;

    @PlanningScore
    private HardSoftScore score;

    public VehicleRoutingSolution() {
    }

    public VehicleRoutingSolution(List<TruckAnchor> truckList, List<DeliveryVisit> visitList) {
        this.truckList = truckList;
        this.visitList = visitList;
    }

    public List<TruckAnchor> getTruckList() {
        return truckList;
    }

    public void setTruckList(List<TruckAnchor> truckList) {
        this.truckList = truckList;
    }

    public List<DeliveryVisit> getVisitList() {
        return visitList;
    }

    public void setVisitList(List<DeliveryVisit> visitList) {
        this.visitList = visitList;
    }

    public HardSoftScore getScore() {
        return score;
    }

    public void setScore(HardSoftScore score) {
        this.score = score;
    }
}
