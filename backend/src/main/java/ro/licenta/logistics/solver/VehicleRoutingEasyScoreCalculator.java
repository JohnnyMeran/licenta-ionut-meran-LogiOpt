package ro.licenta.logistics.solver;

import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.score.calculator.EasyScoreCalculator;
import java.util.*;

public class VehicleRoutingEasyScoreCalculator implements EasyScoreCalculator<VehicleRoutingSolution, HardSoftScore> {
    public static double DRIVER_DAILY_SALARY_RON = 300.0;
    @Override
    public HardSoftScore calculateScore(VehicleRoutingSolution solution) {
        int hard = 0;
        int soft = 0;
        for (TruckAnchor truck : solution.getTruckList()) {
            List<DeliveryVisit> chain = extractChain(truck, solution.getVisitList());
            if (!chain.isEmpty()) {
                // Cost fix pe șofer/camion folosit. Astfel solverul real NU este obligat să folosească toate camioanele,
                // iar varianta ipotetică nu va crea camioane suplimentare doar pentru economii mici de combustibil.
                soft -= (int)Math.round(DRIVER_DAILY_SALARY_RON * 220);
            }
            double totalLoad = 0;
            for (DeliveryVisit visit : chain) {
                totalLoad += visit.getWeightKg();
                if (visit.getWeightKg() > truck.getCapacityKg()) hard -= (int)Math.round((visit.getWeightKg() - truck.getCapacityKg()) * 4000);
                if (!truck.getProducts().contains(visit.getRequiredProduct())) {
                    // In varianta reala camionul poate incarca intermediar din alt depozit.
                    // De aceea nu este hard constraint, ci o penalizare soft pentru timp/cost suplimentar.
                    soft -= 8_000;
                }
            }
            if (totalLoad > truck.getCapacityKg()) hard -= (int)Math.round((totalLoad - truck.getCapacityKg()) * 200);
            double distance = routeDistance(truck, chain);
            int driving = drivingMinutes(distance);
            int breakMinutes = driving > truck.getBreakAfterMinutes() ? truck.getBreakDurationMinutes() : 0;
            int service = chain.stream().mapToInt(DeliveryVisit::getServiceMinutes).sum();
            int total = driving + service + breakMinutes;
            if (driving > truck.getMaxDriveMinutes()) hard -= (driving - truck.getMaxDriveMinutes()) * 1000;

            soft -= (int)Math.round(distance * 650);       // cost/combustibil
            soft -= deliveryCompletionPenalty(truck, chain) * 120; // timp mediu de livrare per comandă
            soft -= total * 12;                            // menține și ruta compactă ca timp total
            soft -= timeWindowPenalty(truck, chain) * 55;  // întârzieri față de intervalul cerut
            soft -= priorityPenalty(chain) * 60;           // comenzile urgente mai devreme în lanț
        }
        for (DeliveryVisit visit : solution.getVisitList()) {
            if (visit.getTruck() == null || visit.getPreviousStandstill() == null) hard -= 100_000;
        }
        return HardSoftScore.of(hard, soft);
    }

    public static int deliveryCompletionPenalty(TruckAnchor truck, List<DeliveryVisit> chain) {
        double lat = truck.getLatitude(), lon = truck.getLongitude();
        int clock = 8 * 60;
        int completionSum = 0;
        int deliveries = 0;
        for (DeliveryVisit stop : chain) {
            double leg = haversine(lat, lon, stop.getLatitude(), stop.getLongitude());
            clock += drivingMinutes(leg);
            if (clock < stop.getWindowStartMinute()) clock = stop.getWindowStartMinute();
            clock += stop.getServiceMinutes();
            completionSum += (clock - 8 * 60);
            deliveries++;
            lat = stop.getLatitude(); lon = stop.getLongitude();
        }
        return deliveries == 0 ? 0 : completionSum;
    }

    public static int timeWindowPenalty(TruckAnchor truck, List<DeliveryVisit> chain) {
        double lat = truck.getLatitude(), lon = truck.getLongitude();
        int clock = 8 * 60;
        int penalty = 0;
        for (DeliveryVisit stop : chain) {
            double leg = haversine(lat, lon, stop.getLatitude(), stop.getLongitude());
            clock += drivingMinutes(leg);
            if (clock < stop.getWindowStartMinute()) clock = stop.getWindowStartMinute();
            if (clock > stop.getWindowEndMinute()) penalty += (clock - stop.getWindowEndMinute()) * priorityWeight(stop.getPriority());
            clock += stop.getServiceMinutes();
            lat = stop.getLatitude(); lon = stop.getLongitude();
        }
        return penalty;
    }

    public static int priorityPenalty(List<DeliveryVisit> chain) {
        int penalty = 0;
        for (int i = 0; i < chain.size(); i++) penalty += i * priorityWeight(chain.get(i).getPriority());
        return penalty;
    }

    public static int priorityWeight(String p) {
        if (p == null) return 1;
        return p.equalsIgnoreCase("URGENT") ? 6 : p.toLowerCase().contains("scăzut") ? 1 : 3;
    }

    public static List<DeliveryVisit> extractChain(TruckAnchor truck, List<DeliveryVisit> visits) {
        List<DeliveryVisit> chain = new ArrayList<>();
        Standstill current = truck;
        Set<DeliveryVisit> guard = new HashSet<>();
        while (true) {
            Standstill finalCurrent = current;
            Optional<DeliveryVisit> next = visits.stream().filter(v -> v.getPreviousStandstill() == finalCurrent).findFirst();
            if (next.isEmpty() || guard.contains(next.get())) break;
            chain.add(next.get());
            guard.add(next.get());
            current = next.get();
        }
        return chain;
    }

    public static double routeDistance(TruckAnchor truck, List<DeliveryVisit> stops) {
        double total = 0.0, lat = truck.getLatitude(), lon = truck.getLongitude();
        for (DeliveryVisit stop : stops) {
            total += haversine(lat, lon, stop.getLatitude(), stop.getLongitude());
            lat = stop.getLatitude(); lon = stop.getLongitude();
        }
        total += haversine(lat, lon, truck.getLatitude(), truck.getLongitude());
        return total;
    }
    public static int drivingMinutes(double km) { return (int)Math.round((km / 28.0) * 60); }
    public static double haversine(double lat1,double lon1,double lat2,double lon2){double R=6371;double dLat=Math.toRadians(lat2-lat1);double dLon=Math.toRadians(lon2-lon1);double a=Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(dLon/2)*Math.sin(dLon/2);return 2*R*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));}
}
