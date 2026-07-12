package ro.licenta.logistics.solver;

import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.score.calculator.EasyScoreCalculator;
import java.util.*;

public class VehicleRoutingEasyScoreCalculator implements EasyScoreCalculator<VehicleRoutingSolution, HardSoftScore> {

    // Scorul soft ESTE costul zilnic al planului, exprimat în bani (1 RON = 100), plus penalizări convertite tot
    // în bani. Înainte, termenii erau ponderi fără unitate (distanța ×650, timpul de finalizare ×120, ...), iar
    // penalizarea de timp o domina pe cea de distanță de ~10×: solverul minimiza practic timpul, deși aplicația
    // raporta „cost economisit". Acum obiectivul optimizat este exact indicatorul raportat.
    private static final int BANI = 100;

    // Cât valorează comercial o livrare mai devreme. Ține solverul să nu lase colete la coada zilei atunci când
    // costul este egal, fără să depășească în importanță costul propriu-zis.
    private static final double EARLY_DELIVERY_RON_PER_MIN = 0.05;
    // Întârzierea față de fereastra promisă clientului: penalizare directă, ponderată cu prioritatea coletului.
    private static final double LATE_RON_PER_MIN = 2.0;
    // Coletele urgente cât mai devreme în lanț (departajare fină între soluții de cost egal).
    private static final double PRIORITY_ORDER_RON = 0.02;
    // O dubă nu are voie să deservească regiunea altui hub: penalizare comercială uriașă, dar nu hard, pentru ca
    // solverul să rămână capabil să livreze coletele unui hub rămas fără dube.
    private static final double WRONG_HUB_RON = 5_000.0;

    // Distanța în linie dreaptă subestimează drumul real cu ~35%. Solverul TREBUIE să folosească același
    // factor ca raportarea (OptimizationService): altfel verifică orele legale pe drumuri mai scurte decât
    // cele pe care le va parcurge camionul, declară o cursă legală, iar raportul o afișează apoi „peste program".
    public static final double ROAD_FACTOR = 1.35;
    @Override
    public HardSoftScore calculateScore(VehicleRoutingSolution solution) {
        int hard = 0;
        int soft = 0;
        // Indexul de succesori se construiește o singură dată per evaluare: fără el, extractChain
        // rescanează întreaga listă de vizite la fiecare pas (O(n²) per scor), iar euristica
        // constructivă nu apucă să inițializeze toate vizitele în bugetul de timp alocat.
        Map<Standstill, DeliveryVisit> successors = successorIndex(solution.getVisitList());
        for (TruckAnchor truck : solution.getTruckList()) {
            List<DeliveryVisit> chain = extractChain(truck, successors);
            for (DeliveryVisit visit : chain) {
                if (visit.getWeightKg() > truck.getCapacityKg()) hard -= (int)Math.round((visit.getWeightKg() - truck.getCapacityKg()) * 4000);
                if (!Objects.equals(visit.getHubId(), truck.getDepotId())) {
                    soft -= (int)Math.round(WRONG_HUB_RON * BANI);
                }
            }
            // Capacitatea se compară cu vârful de încărcătură, nu cu totalul manipulat într-o zi: o dubă care
            // livrează colete și ridică altele nu cară niciodată simultan tot ce a atins. Fiind o limită fizică
            // (nu o preferință), depășirea ei este penalizată hard.
            double peakLoad = peakLoad(chain);
            if (peakLoad > truck.getCapacityKg()) hard -= (int)Math.round((peakLoad - truck.getCapacityKg()) * 100);
            // Un colet nu poate fi livrat înainte de a fi ridicat. Se aplică doar când ambele operațiuni cad pe
            // aceeași dubă (coletele locale, expediate și livrate în raza aceluiași hub); dacă sunt pe dube diferite,
            // coletul trece prin hub și ordinea între rute nu contează.
            hard -= precedenceViolations(chain) * 50_000;
            double distance = routeDistance(truck, chain);
            int driving = minutesFor(distance, truck.getSpeedKmh());
            if (driving > truck.getMaxDriveMinutes()) hard -= (driving - truck.getMaxDriveMinutes()) * 1000;

            // Costul real al rutei, exact cum îl calculează raportarea: kilometrii consumați + costul fix al zilei
            // (echipaj + amortizare), acesta din urmă doar dacă vehiculul chiar iese pe traseu. În scenariul
            // ipotetic, costul fix este 0 — vehiculele sunt gratuite, deci solverul poate folosi oricâte.
            double routeCostRon = distance * truck.getCostRonPerKm();
            if (!chain.isEmpty()) routeCostRon += truck.getFixedDailyCostRon();
            soft -= (int)Math.round(routeCostRon * BANI);

            soft -= (int)Math.round(deliveryCompletionPenalty(truck, chain) * EARLY_DELIVERY_RON_PER_MIN * BANI);
            soft -= (int)Math.round(timeWindowPenalty(truck, chain) * LATE_RON_PER_MIN * BANI);
            soft -= (int)Math.round(priorityPenalty(chain) * PRIORITY_ORDER_RON * BANI);
        }
        for (DeliveryVisit visit : solution.getVisitList()) {
            if (visit.getTruck() == null || visit.getPreviousStandstill() == null) hard -= 100_000;
        }
        return HardSoftScore.of(hard, soft);
    }

    // Încărcătura reală aflată în vehicul, urmărită de-a lungul traseului: vehiculul pleacă din hub cu toate
    // coletele de livrat, le lasă pe rând și ridică altele. Vârful acestei curbe este singura valoare care are
    // sens comparată cu capacitatea. Sursă unică de adevăr: o folosesc și scorul solverului, și raportarea
    // (OptimizationService), ca cele două să nu poată ajunge să spună lucruri diferite.
    public static double peakLoad(List<DeliveryVisit> chain) {
        // Un colet ridicat CHIAR DE ACEASTĂ dubă nu se află în ea la plecarea din hub — urcă abia la ridicare.
        // Fără excepția asta, coletele locale (același hub la expediere și la livrare) ar fi numărate de două ori:
        // o dată în încărcătura inițială și încă o dată la ridicare.
        Set<Long> pickedUpOnThisRoute = new HashSet<>();
        for (DeliveryVisit visit : chain) {
            if ("PICKUP".equals(visit.getTaskType()) && visit.getShipmentId() != null) {
                pickedUpOnThisRoute.add(visit.getShipmentId());
            }
        }

        double load = 0;
        for (DeliveryVisit visit : chain) {
            if ("DELIVERY".equals(visit.getTaskType()) && !pickedUpOnThisRoute.contains(visit.getShipmentId())) {
                load += visit.getWeightKg();    // sosit prin linehaul: încărcat în dubă la hub, înainte de plecare
            }
        }

        double peak = load;
        for (DeliveryVisit visit : chain) {
            if ("DELIVERY".equals(visit.getTaskType())) load -= visit.getWeightKg();
            else load += visit.getWeightKg();   // PICKUP sau transfer LINEHAUL: marfa urcă în vehicul
            peak = Math.max(peak, load);
        }
        return peak;
    }

    // Câte colete sunt livrate înainte de a fi ridicate, pe aceeași dubă.
    public static int precedenceViolations(List<DeliveryVisit> chain) {
        Map<Long, Integer> pickupAt = new HashMap<>();
        Map<Long, Integer> deliveryAt = new HashMap<>();
        for (int i = 0; i < chain.size(); i++) {
            DeliveryVisit visit = chain.get(i);
            Long shipment = visit.getShipmentId();
            if (shipment == null) continue;
            if ("PICKUP".equals(visit.getTaskType())) pickupAt.putIfAbsent(shipment, i);
            else if ("DELIVERY".equals(visit.getTaskType())) deliveryAt.putIfAbsent(shipment, i);
        }
        int violations = 0;
        for (var entry : pickupAt.entrySet()) {
            Integer delivery = deliveryAt.get(entry.getKey());
            if (delivery != null && delivery < entry.getValue()) violations++;
        }
        return violations;
    }

    // Pauzele legale, calculate identic cu raportarea din OptimizationService: o pauză la fiecare interval de
    // condus, nu una singură pe zi. Altfel solverul optimizează o durată de rută mai mică decât cea afișată.
    public static int legalBreakMinutes(int drivingMinutes, int breakAfterMinutes, int breakDurationMinutes) {
        if (breakAfterMinutes <= 0 || drivingMinutes <= breakAfterMinutes) return 0;
        return ((drivingMinutes - 1) / breakAfterMinutes) * breakDurationMinutes;
    }

    public static int deliveryCompletionPenalty(TruckAnchor truck, List<DeliveryVisit> chain) {
        double lat = truck.getLatitude(), lon = truck.getLongitude();
        int clock = 8 * 60;
        int completionSum = 0;
        int deliveries = 0;
        for (DeliveryVisit stop : chain) {
            double leg = roadKm(lat, lon, stop.getLatitude(), stop.getLongitude());
            clock += minutesFor(leg, truck.getSpeedKmh());
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
            double leg = roadKm(lat, lon, stop.getLatitude(), stop.getLongitude());
            clock += minutesFor(leg, truck.getSpeedKmh());
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

    // Mapează fiecare predecesor (ancoră sau vizită) la succesorul său direct, folosind identitatea
    // de referință — exact semantica lui `previousStandstill == current` din varianta anterioară.
    public static Map<Standstill, DeliveryVisit> successorIndex(List<DeliveryVisit> visits) {
        Map<Standstill, DeliveryVisit> successors = new IdentityHashMap<>(Math.max(16, visits.size() * 2));
        for (DeliveryVisit visit : visits) {
            Standstill previous = visit.getPreviousStandstill();
            if (previous != null) successors.putIfAbsent(previous, visit);
        }
        return successors;
    }

    public static List<DeliveryVisit> extractChain(TruckAnchor truck, Map<Standstill, DeliveryVisit> successors) {
        List<DeliveryVisit> chain = new ArrayList<>();
        Set<DeliveryVisit> guard = Collections.newSetFromMap(new IdentityHashMap<>());
        Standstill current = truck;
        while (true) {
            DeliveryVisit next = successors.get(current);
            if (next == null || !guard.add(next)) break;   // lanț terminat sau ciclu detectat
            chain.add(next);
            current = next;
        }
        return chain;
    }

    public static List<DeliveryVisit> extractChain(TruckAnchor truck, List<DeliveryVisit> visits) {
        return extractChain(truck, successorIndex(visits));
    }

    public static double routeDistance(TruckAnchor truck, List<DeliveryVisit> stops) {
        double total = 0.0, lat = truck.getLatitude(), lon = truck.getLongitude();
        for (DeliveryVisit stop : stops) {
            total += roadKm(lat, lon, stop.getLatitude(), stop.getLongitude());
            lat = stop.getLatitude(); lon = stop.getLongitude();
        }
        total += roadKm(lat, lon, truck.getLatitude(), truck.getLongitude());
        return total;
    }

    // Kilometri de drum, nu în linie dreaptă. Tot ce se transformă în timp (ore legale, ferestre orare) trebuie
    // să pornească de aici.
    public static double roadKm(double lat1, double lon1, double lat2, double lon2) {
        return haversine(lat1, lon1, lat2, lon2) * ROAD_FACTOR;
    }
    public static int drivingMinutes(double km) { return (int)Math.round((km / 28.0) * 60); }
    public static int minutesFor(double km, double kmh) { return (int)Math.round((km / (kmh <= 0 ? 28.0 : kmh)) * 60); }
    public static double haversine(double lat1,double lon1,double lat2,double lon2){double R=6371;double dLat=Math.toRadians(lat2-lat1);double dLon=Math.toRadians(lon2-lon1);double a=Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(dLon/2)*Math.sin(dLon/2);return 2*R*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));}
}
