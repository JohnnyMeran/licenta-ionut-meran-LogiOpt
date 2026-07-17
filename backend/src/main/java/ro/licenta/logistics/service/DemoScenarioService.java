package ro.licenta.logistics.service;

import org.springframework.stereotype.Service;
import ro.licenta.logistics.dto.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class DemoScenarioService {
    public static final int LEGAL_DAILY_DRIVE_MINUTES = 9 * 60;
    public static final int LEGAL_BREAK_AFTER_MINUTES = 4 * 60 + 30;
    public static final int LEGAL_BREAK_DURATION_MINUTES = 45;
    public static final int LEGAL_DAILY_REST_MINUTES = 11 * 60;
    public static final int LEGAL_WEEKLY_DRIVE_MINUTES = 56 * 60;
    public static final int LEGAL_TWO_WEEK_DRIVE_MINUTES = 90 * 60;

    // Dubă de curier (ridicări + livrări pe ultima milă în jurul unui hub).
    public static final double VAN_CAPACITY_KG = 700.0;
    public static final double VAN_CONSUMPTION = 9.0;
    public static final double VAN_COST_RON_PER_KM = 1.6;
    // Camion de linehaul (transfer consolidat între hub-uri, distanțe mari, echipaj dublu).
    public static final double LINEHAUL_CAPACITY_KG = 12000.0;
    public static final double LINEHAUL_CONSUMPTION = 28.0;
    public static final double LINEHAUL_COST_RON_PER_KM = 3.2;

    // Numărul de colete generate în setul demo (suficient cât OptaPlanner să aibă o problemă reală de rezolvat).
    private static final int DEMO_SHIPMENT_COUNT = 180;
    private static final long DEMO_SEED = 42L;

    private static final String[] LUNI_RO = {
            "Ianuarie", "Februarie", "Martie", "Aprilie", "Mai", "Iunie",
            "Iulie", "August", "Septembrie", "Octombrie", "Noiembrie", "Decembrie"
    };
    private static final String[] FIRST_NAMES = {
            "Andrei", "Mihai", "Elena", "Radu", "Ioana", "Vlad", "Cristina", "George", "Alina", "Sorin",
            "Diana", "Paul", "Bogdan", "Raluca", "Marius", "Ana", "Dan", "Gabriela", "Florin", "Roxana"
    };
    private static final String[] LAST_NAMES = {
            "Popescu", "Ionescu", "Marin", "Stan", "Dobre", "Georgescu", "Toma", "Nistor", "Barbu", "Ilie",
            "Radu", "Enache", "Pavel", "Șerban", "Voicu", "Dumitrescu", "Constantin", "Matei", "Stoica", "Munteanu"
    };
    private static final String[] COMPANIES = {
            "eMAG Marketplace", "Dedeman Online", "Altex Depozit", "Fashion Days", "Flanco Retail", "PC Garage",
            "Decathlon RO", "IKEA Distribuție", "Profi Logistic", "Carrefour Online", "Atelier Local", "Farmacia Tei",
            "Librăria Cărturești", "Producător Regional", "Distribuție Mobilă", "Auchan Online", "Electro Depot", "Notino RO"
    };
    private static final String[] PEOPLE_SUFFIX = {
            "Client persoană fizică", "Birou firmă", "Punct de lucru", "Magazin partener", "Locker automat", "Depozit client"
    };
    private static final String[] STREETS = {
            "Str. Principală", "Bd. Unirii", "Calea Victoriei", "Str. Mihai Eminescu", "Str. Libertății", "Bd. Republicii",
            "Str. Gării", "Aleea Parcului", "Str. Industriilor", "Șos. Națională", "Str. Florilor", "Str. Nucului"
    };
    // Ponderi pentru alegerea orașelor (București generează cel mai mult trafic).
    private static final String[] HUB_POOL = {
            "HUB-B", "HUB-B", "HUB-B", "HUB-B",
            "HUB-CJ", "HUB-CJ", "HUB-TM", "HUB-TM",
            "HUB-IS", "HUB-IS", "HUB-CT", "HUB-CT",
            "HUB-BV", "HUB-BV", "HUB-CV", "HUB-SB", "HUB-SB"
    };
    private static final int[][] WINDOWS = {
            {540, 780}, {600, 900}, {480, 720}, {660, 960}, {720, 1020}, {840, 1080}, {510, 750}
    };
    private static final String[] PRIORITIES = {
            "URGENT", "URGENT", "NORMAL", "NORMAL", "NORMAL", "NORMAL", "SCĂZUTĂ", "SCĂZUTĂ"
    };
    // Câte dube pornesc din fiecare hub.
    private static final Object[][] VAN_PLAN = {
            {"HUB-B", 5}, {"HUB-CJ", 3}, {"HUB-TM", 3}, {"HUB-IS", 2},
            {"HUB-CT", 2}, {"HUB-BV", 2}, {"HUB-CV", 2}, {"HUB-SB", 2}
    };
    private static final int LINEHAUL_TRUCKS = 6;

    private final List<DepotDto> hubs = new ArrayList<>();
    private final Map<String, DepotDto> hubById = new LinkedHashMap<>();
    private final List<DriverDto> drivers = new ArrayList<>();
    private final List<VehicleDto> vehicles = new ArrayList<>();
    private final List<ShipmentDto> shipments = new ArrayList<>();

    private static final double DEFAULT_VAN_PRICE_RON = 180_000;
    private static final double DEFAULT_TRUCK_PRICE_RON = 420_000;
    private static final int DEFAULT_USEFUL_LIFE_YEARS = 8;
    private static final double DEFAULT_RESIDUAL_PERCENT = 20;
    private static final int DEFAULT_WORKING_DAYS_PER_YEAR = 260;

    private static final double DEFAULT_FLEET_RESERVE_PERCENT = 15;

    private SettingsDto settings = new SettingsDto(20, 15, 7.45, 300.0,
            DEFAULT_VAN_PRICE_RON, DEFAULT_TRUCK_PRICE_RON, DEFAULT_USEFUL_LIFE_YEARS,
            DEFAULT_RESIDUAL_PERCENT, DEFAULT_WORKING_DAYS_PER_YEAR, DEFAULT_FLEET_RESERVE_PERCENT);


    private int version = 0;

    public synchronized int version() {
        return version;
    }

    public DemoScenarioService() {
        reset();
    }

    public synchronized void reset() {
        version++;
        hubs.clear();
        hubById.clear();
        drivers.clear();
        vehicles.clear();
        shipments.clear();

        hubs.addAll(List.of(
                new DepotDto("HUB-B", "Hub București Militari", "București", 44.4268, 26.1025, List.of("Ilfov", "Giurgiu", "Ploiești")),
                new DepotDto("HUB-CJ", "Hub Cluj Napoca", "Cluj-Napoca", 46.7712, 23.6236, List.of("Cluj", "Bistrița", "Zalău")),
                new DepotDto("HUB-TM", "Hub Timișoara", "Timișoara", 45.7489, 21.2087, List.of("Timiș", "Arad", "Reșița")),
                new DepotDto("HUB-IS", "Hub Iași", "Iași", 47.1585, 27.6014, List.of("Iași", "Vaslui", "Botoșani")),
                new DepotDto("HUB-CT", "Hub Constanța", "Constanța", 44.1733, 28.6383, List.of("Constanța", "Tulcea", "Călărași")),
                new DepotDto("HUB-BV", "Hub Brașov", "Brașov", 45.6579, 25.6012, List.of("Brașov", "Covasna", "Sfântu Gheorghe")),
                new DepotDto("HUB-CV", "Hub Craiova", "Craiova", 44.3302, 23.7949, List.of("Dolj", "Olt", "Slatina")),
                new DepotDto("HUB-SB", "Hub Sibiu", "Sibiu", 45.7983, 24.1256, List.of("Sibiu", "Alba", "Mediaș"))
        ));
        hubs.forEach(h -> hubById.put(h.id(), h));

        // 30 de șoferi generați determinist.
        for (int i = 0; i < 30; i++) {
            String id = "DRV-" + String.format("%02d", i + 1);
            String name = FIRST_NAMES[i % FIRST_NAMES.length] + " " + LAST_NAMES[(i * 7) % LAST_NAMES.length];
            drivers.add(driver(id, name));
        }

        int driverCursor = 0;
        int vanCounter = 1;
        for (Object[] plan : VAN_PLAN) {
            String hubId = (String) plan[0];
            int count = (int) plan[1];
            for (int i = 0; i < count; i++) {
                String code = "DUBA-" + String.format("%02d", vanCounter++);
                String driverId = drivers.get(driverCursor++ % drivers.size()).id();
                vehicles.add(van(code, driverId, hubId));
            }
        }
        for (int i = 0; i < LINEHAUL_TRUCKS; i++) {
            String code = "TIR-" + String.format("%02d", i + 1);
            String driverId = drivers.get(driverCursor++ % drivers.size()).id();
            vehicles.add(linehaul(code, driverId, "HUB-B"));
        }

        Random rng = new Random(DEMO_SEED);
        generateShipments(DEMO_SHIPMENT_COUNT, rng);
    }

    private void generateShipments(int n, Random rng) {
        for (long id = 1; id <= n; id++) {
            String origin = HUB_POOL[rng.nextInt(HUB_POOL.length)];
            String dest = rng.nextDouble() < 0.22 ? origin : HUB_POOL[rng.nextInt(HUB_POOL.length)];
            DepotDto o = hubById.get(origin), d = hubById.get(dest);
            double pLat = o.latitude() + (rng.nextDouble() - 0.5) * 0.10;
            double pLon = o.longitude() + (rng.nextDouble() - 0.5) * 0.14;
            double dLat = d.latitude() + (rng.nextDouble() - 0.5) * 0.10;
            double dLon = d.longitude() + (rng.nextDouble() - 0.5) * 0.14;
            double weight = round1(3 + rng.nextDouble() * 45);
            int[] w = WINDOWS[rng.nextInt(WINDOWS.length)];
            String priority = PRIORITIES[rng.nextInt(PRIORITIES.length)];
            int service = 8 + rng.nextInt(7);
            String sender = COMPANIES[rng.nextInt(COMPANIES.length)];
            String recipient = FIRST_NAMES[rng.nextInt(FIRST_NAMES.length)] + " " + LAST_NAMES[rng.nextInt(LAST_NAMES.length)];
            if (rng.nextDouble() < 0.4) recipient = PEOPLE_SUFFIX[rng.nextInt(PEOPLE_SUFFIX.length)];
            String pAddr = STREETS[rng.nextInt(STREETS.length)] + " " + (1 + rng.nextInt(200));
            String dAddr = STREETS[rng.nextInt(STREETS.length)] + " " + (1 + rng.nextInt(200));
            double tariff = estimateTariff(weight, origin, dest);
            shipments.add(buildShipment(id, sender, recipient, origin, pLat, pLon, pAddr, dest, dLat, dLon, dAddr,
                    weight, timeWindow(w[0], w[1]), w[0], w[1], service, priority, tariff));
        }
    }

    public synchronized List<DepotDto> depots() {
        return List.copyOf(hubs);
    }

    public synchronized DepotDto depot() {
        return hubById.get("HUB-B");
    }

    public synchronized DepotDto hub(String id) {
        return hubById.get(id);
    }

    public synchronized Map<String, DepotDto> hubMap() {
        return new LinkedHashMap<>(hubById);
    }

    public synchronized List<DriverDto> drivers() {
        return List.copyOf(drivers);
    }

    public synchronized List<VehicleDto> vehicles() {
        return List.copyOf(vehicles);
    }

    public synchronized List<VehicleDto> vans() {
        return vehicles.stream().filter(v -> "VAN".equals(v.kind())).toList();
    }

    public synchronized List<VehicleDto> lineHaulVehicles() {
        return vehicles.stream().filter(v -> "LINEHAUL".equals(v.kind())).toList();
    }

    public synchronized List<ShipmentDto> shipments() {
        return List.copyOf(shipments);
    }

    public synchronized SettingsDto settings() {
        return settings;
    }

    public synchronized SettingsDto updateSettings(SettingsDto incoming) {
        version++;
        int realSeconds = clampInt(incoming.realSolverSeconds(), 5, 300, settings.realSolverSeconds());
        int hypotheticalSeconds = clampInt(incoming.hypotheticalSolverSeconds(), 5, 300, settings.hypotheticalSolverSeconds());
        double fuelPrice = clamp(incoming.fuelPriceRonPerLiter(), 1.0, 30.0, settings.fuelPriceRonPerLiter());
        // Salariul poate fi legitim 0 (flotă cu șoferi plătiți în alt regim), deci nu se aplică fallback-ul pe 0.
        double driverSalary = Math.max(0.0, Math.min(2000.0, incoming.driverDailySalaryRon()));
        double vanPrice = clamp(incoming.vanPurchasePriceRon(), 20_000, 1_000_000, settings.vanPurchasePriceRon());
        double truckPrice = clamp(incoming.truckPurchasePriceRon(), 50_000, 3_000_000, settings.truckPurchasePriceRon());
        int lifeYears = clampInt(incoming.vehicleUsefulLifeYears(), 1, 25, settings.vehicleUsefulLifeYears());
        double residual = Math.max(0.0, Math.min(80.0, incoming.residualValuePercent()));
        int workingDays = clampInt(incoming.workingDaysPerYear(), 100, 365, settings.workingDaysPerYear());
        double reserve = Math.max(0.0, Math.min(100.0, incoming.fleetReservePercent()));
        settings = new SettingsDto(realSeconds, hypotheticalSeconds, round(fuelPrice), round(driverSalary),
                round(vanPrice), round(truckPrice), lifeYears, round(residual), workingDays, round(reserve));
        return settings;
    }

    // Un câmp numeric absent dintr-un JSON parțial se deserializează în 0 și ar distruge modelul de cost;
    // în loc să fie forțat la limita minimă, păstrează valoarea curentă.
    private double clamp(double value, double min, double max, double fallback) {
        if (value <= 0) return fallback;
        return Math.max(min, Math.min(max, value));
    }

    private int clampInt(int value, int min, int max, int fallback) {
        if (value <= 0) return fallback;
        return Math.max(min, Math.min(max, value));
    }

    // Amortizarea zilnică a întregii flote deținute — se plătește indiferent dacă vehiculul iese sau nu pe traseu.
    public synchronized double fleetDailyAmortizationRon() {
        return vans().size() * settings.vanDailyAmortizationRon()
                + lineHaulVehicles().size() * settings.truckDailyAmortizationRon();
    }

    // Coordonatele lipsă se deserializează în 0.0, adică Golful Guineei: coletul ar fi „ridicat" la ~5.500 km de
    // orice hub, iar distanța, combustibilul și costul întregului scenariu ar deveni absurde. Un colet fără poziție
    // validă în România este respins.
    public static boolean insideRomania(double lat, double lon) {
        return lat >= 43.5 && lat <= 48.5 && lon >= 20.0 && lon <= 30.0;
    }

    public synchronized ShipmentDto addShipment(ShipmentDto s) {
        if (s == null
                || !insideRomania(s.pickupLat(), s.pickupLon())
                || !insideRomania(s.deliveryLat(), s.deliveryLon())
                || s.weightKg() <= 0) {
            throw new IllegalArgumentException(
                    "Colet invalid: ridicarea și livrarea trebuie să aibă coordonate în România, iar greutatea să fie pozitivă.");
        }
        version++;
        long next = shipments.stream().mapToLong(ShipmentDto::id).max().orElse(0) + 1;
        String originHub = nearestHubId(s.pickupLat(), s.pickupLon());
        String destHub = nearestHubId(s.deliveryLat(), s.deliveryLon());
        ShipmentDto created = new ShipmentDto(next, codeFor(next), s.senderName(), s.recipientName(),
                s.pickupAddress(), hubById.get(originHub).city(), s.pickupLat(), s.pickupLon(), originHub,
                s.deliveryAddress(), hubById.get(destHub).city(), s.deliveryLat(), s.deliveryLon(), destHub,
                s.weightKg(), s.timeWindow(), s.windowStartMinute(), s.windowEndMinute(), s.serviceMinutes(), s.priority(),
                s.tariffRon() > 0 ? s.tariffRon() : estimateTariff(s.weightKg(), originHub, destHub));
        shipments.add(created);
        return created;
    }

    public synchronized void deleteShipment(long id) {
        version++;
        shipments.removeIf(s -> s.id() == id);
    }

    public synchronized DriverDto addDriver(DriverDto d) {
        version++;
        String name = d == null || d.name() == null || d.name().isBlank() ? "Șofer nou" : d.name().trim();
        DriverDto created = driver(nextId("DRV-", drivers.stream().map(DriverDto::id).toList()), name);
        drivers.add(created);
        return created;
    }

    public synchronized void deleteDriver(String id) {
        version++;
        drivers.removeIf(d -> d.id().equals(id));
        vehicles.removeIf(v -> Objects.equals(v.driverId(), id)); // driverId poate fi null pentru un vehicul neasignat
    }

    public synchronized VehicleDto addVehicle(VehicleDto v) {
        version++;
        boolean lineHaul = v != null && "LINEHAUL".equalsIgnoreCase(v.kind());
        // Un depot inexistent ar produce NPE la fiecare construire de scenariu, adică 500 pe TOATE endpoint-urile
        // până la un reset. Vehiculul se ancorează la hub-ul cerut doar dacă acesta există.
        String depotId = v != null && hubById.containsKey(v.depotId()) ? v.depotId() : depot().id();
        String requestedDriver = v == null ? null : v.driverId();
        // Un vehicul primește șoferul cerut doar dacă acesta chiar există; altfel rămâne neasignat, cu driverId null.
        DriverDto driver = drivers.stream().filter(d -> d.id().equals(requestedDriver)).findFirst().orElse(null);
        String driverId = driver == null ? null : driver.id();
        String driverName = driver == null ? "Șofer neasignat" : driver.name();

        String code = nextId(lineHaul ? "TIR-" : "DUBA-", vehicles.stream().map(VehicleDto::code).toList());
        VehicleDto created = lineHaul
                ? new VehicleDto(code, driverId, driverName, depotId, "LINEHAUL", LINEHAUL_CAPACITY_KG, LINEHAUL_CONSUMPTION, LINEHAUL_COST_RON_PER_KM)
                : new VehicleDto(code, driverId, driverName, depotId, "VAN", VAN_CAPACITY_KG, VAN_CONSUMPTION, VAN_COST_RON_PER_KM);
        vehicles.add(created);
        return created;
    }

    public synchronized void deleteVehicle(String code) {
        version++;
        vehicles.removeIf(v -> v.code().equals(code));
    }

    // Numerotarea pornește de la maximul existent, nu de la numărul de elemente: după ștergerea lui DUBA-07,
    // o numerotare bazată pe count ar regenera DUBA-21 peste cel existent. Codurile duplicate sunt otrăvitoare —
    // ștergerea ar elimina ambele vehicule, iar în solver cele două ancore cu același cod se contopesc, cu
    // pierderea silențioasă a unei rute întregi.
    private String nextId(String prefix, List<String> existing) {
        int max = 0;
        for (String id : existing) {
            if (id == null || !id.startsWith(prefix)) continue;
            try {
                max = Math.max(max, Integer.parseInt(id.substring(prefix.length())));
            } catch (NumberFormatException ignored) {
                // cod cu altă formă: nu contribuie la numerotare
            }
        }
        return prefix + String.format("%02d", max + 1);
    }

    public synchronized String nearestHubId(double lat, double lon) {
        return hubs.stream()
                .min(Comparator.comparingDouble(h -> haversine(lat, lon, h.latitude(), h.longitude())))
                .map(DepotDto::id).orElse("HUB-B");
    }

    public synchronized double estimateTariff(double weightKg, String originHub, String destHub) {
        DepotDto o = hubById.get(originHub), d = hubById.get(destHub);
        double km = (o == null || d == null) ? 0 : haversine(o.latitude(), o.longitude(), d.latitude(), d.longitude());
        double base = 12 + weightKg * 0.35 + km * 0.06;
        return Math.round(base * 100.0) / 100.0;
    }


    public synchronized List<MonthlyReportDto> monthlyReports() {
        List<MonthlyReportDto> reports = new ArrayList<>();
        double workingDaysPerMonth = settings.workingDaysPerYear() / 12.0;
        double monthlyAmortization = fleetDailyAmortizationRon() * workingDaysPerMonth;
        YearMonth cursor = YearMonth.from(LocalDate.now()).minusMonths(6);
        for (int i = 0; i < 6; i++) {
            cursor = cursor.plusMonths(1);
            int seed = cursor.getYear() * 12 + cursor.getMonthValue();
            int monthShipments = 4200 + (seed % 7) * 260 + i * 210;             // volum lunar în creștere
            double avgTariff = 33.5 + ((seed % 5) * 1.6);
            double revenue = monthShipments * avgTariff;
            double baselineCostPerShipment = 21.5 + ((seed % 4) * 0.8);
            double baselineCost = monthShipments * baselineCostPerShipment;
            double savePct = 0.17 + ((seed % 6) * 0.012) + i * 0.004;           // optimizarea se maturizează în timp
            double optimizedCost = baselineCost * (1 - savePct);
            double saved = baselineCost - optimizedCost;
            double profitBeforeAmortization = revenue - optimizedCost;
            double profit = profitBeforeAmortization - monthlyAmortization;
            reports.add(new MonthlyReportDto(
                    LUNI_RO[cursor.getMonthValue() - 1], cursor.getYear(), monthShipments,
                    round(revenue), round(baselineCost), round(optimizedCost), round(monthlyAmortization),
                    round(saved), round(profitBeforeAmortization), round(profit)));
        }
        return reports;
    }

    private ShipmentDto buildShipment(long id, String sender, String recipient,
                                      String originHub, double pLat, double pLon, String pAddr,
                                      String destHub, double dLat, double dLon, String dAddr,
                                      double weight, String tw, int start, int end, int service, String priority, double tariff) {
        DepotDto o = hubById.get(originHub), d = hubById.get(destHub);
        return new ShipmentDto(id, codeFor(id), sender, recipient,
                pAddr, o.city(), round(pLat), round(pLon), originHub,
                dAddr, d.city(), round(dLat), round(dLon), destHub,
                weight, tw, start, end, service, priority, tariff);
    }

    private String codeFor(long id) {
        return "LO" + (1000000 + id);
    }

    private String timeWindow(int start, int end) {
        return minuteToTime(start) + "-" + minuteToTime(end);
    }

    private String minuteToTime(int m) {
        return String.format("%02d:%02d", m / 60, m % 60);
    }

    private DriverDto driver(String id, String name) {
        return new DriverDto(id, name, LEGAL_DAILY_DRIVE_MINUTES, LEGAL_BREAK_AFTER_MINUTES, LEGAL_BREAK_DURATION_MINUTES, LEGAL_DAILY_REST_MINUTES, LEGAL_WEEKLY_DRIVE_MINUTES, LEGAL_TWO_WEEK_DRIVE_MINUTES);
    }

    private VehicleDto van(String code, String driverId, String hubId) {
        return new VehicleDto(code, driverId, driverName(driverId), hubId, "VAN", VAN_CAPACITY_KG, VAN_CONSUMPTION, VAN_COST_RON_PER_KM);
    }

    private VehicleDto linehaul(String code, String driverId, String hubId) {
        return new VehicleDto(code, driverId, driverName(driverId), hubId, "LINEHAUL", LINEHAUL_CAPACITY_KG, LINEHAUL_CONSUMPTION, LINEHAUL_COST_RON_PER_KM);
    }

    private String driverName(String driverId) {
        return drivers.stream().filter(d -> d.id().equals(driverId)).map(DriverDto::name).findFirst().orElse("Șofer neasignat");
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371, dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
