package ro.licenta.logistics.service;

import org.springframework.stereotype.Service;
import ro.licenta.logistics.dto.*;
import java.util.*;

@Service
public class DemoScenarioService {
    public static final int LEGAL_DAILY_DRIVE_MINUTES = 9 * 60;
    public static final int LEGAL_BREAK_AFTER_MINUTES = 4 * 60 + 30;
    public static final int LEGAL_BREAK_DURATION_MINUTES = 45;
    public static final int LEGAL_DAILY_REST_MINUTES = 11 * 60;
    public static final int LEGAL_WEEKLY_DRIVE_MINUTES = 56 * 60;
    public static final int LEGAL_TWO_WEEK_DRIVE_MINUTES = 90 * 60;
    public static final double STANDARD_TRUCK_CAPACITY_KG = 900.0;
    public static final double STANDARD_TRUCK_CONSUMPTION = 8.0;
    public static final double STANDARD_TRUCK_COST_RON_PER_KM = 2.25;

    private final List<DepotDto> depots = new ArrayList<>();
    private final List<DriverDto> drivers = new ArrayList<>();
    private final List<VehicleDto> vehicles = new ArrayList<>();
    private final List<OrderDto> orders = new ArrayList<>();
    private SettingsDto settings = new SettingsDto(60, 60, 7.45, 300.0);

    public DemoScenarioService() { reset(); }

    public synchronized void reset() {
        depots.clear(); drivers.clear(); vehicles.clear(); orders.clear();
        depots.addAll(List.of(
            new DepotDto("DEP-N", "Depozit Nord - Chitila", 44.5150, 25.9730, List.of("electronice", "fashion", "farmaceutice")),
            new DepotDto("DEP-C", "Depozit Central - București", 44.4268, 26.1025, List.of("alimente", "carti", "fashion", "electronice")),
            new DepotDto("DEP-S", "Depozit Sud - Popești", 44.3820, 26.1450, List.of("alimente", "mobila", "farmaceutice", "carti"))
        ));
        drivers.addAll(List.of(
            driver("DRV-01", "Andrei Popescu"), driver("DRV-02", "Mihai Ionescu"), driver("DRV-03", "Elena Marin"),
            driver("DRV-04", "Radu Stan"), driver("DRV-05", "Ioana Dobre"), driver("DRV-06", "Vlad Georgescu")
        ));
        vehicles.addAll(List.of(
            vehicle("CAMION-01", "DRV-01", "DEP-N"), vehicle("CAMION-02", "DRV-02", "DEP-C"), vehicle("CAMION-03", "DRV-03", "DEP-S"),
            vehicle("CAMION-04", "DRV-04", "DEP-C"), vehicle("CAMION-05", "DRV-05", "DEP-N"), vehicle("CAMION-06", "DRV-06", "DEP-S")
        ));
        orders.addAll(List.of(
            o(1,"Fashion Store","Bd. Unirii 24",44.428,26.112,80,"09:00-11:00",540,660,10,"URGENT","fashion"),
            o(2,"Tech Hub","Pipera 48",44.481,26.119,120,"10:00-13:00",600,780,12,"NORMAL","electronice"),
            o(3,"Book Point","Calea Victoriei 98",44.444,26.094,40,"09:30-12:00",570,720,8,"NORMAL","carti"),
            o(4,"Market Sud","Șos. Giurgiului 120",44.365,26.095,210,"11:00-15:00",660,900,14,"NORMAL","alimente"),
            o(5,"Office Nord","Băneasa Business",44.503,26.084,160,"09:00-12:00",540,720,12,"URGENT","electronice"),
            o(6,"Home Client A","Drumul Taberei 22",44.425,26.035,25,"13:00-16:00",780,960,8,"SCĂZUTĂ","fashion"),
            o(7,"Pharma East","Pantelimon 301",44.451,26.174,95,"08:00-10:30",480,630,10,"URGENT","farmaceutice"),
            o(8,"Mega Retail","Militari 140",44.438,25.987,180,"12:00-17:00",720,1020,14,"NORMAL","alimente"),
            o(9,"Coffee Chain","Tineretului 8",44.408,26.108,55,"10:00-14:00",600,840,8,"SCĂZUTĂ","alimente"),
            o(10,"Electro Mall","Vitan 55",44.418,26.137,140,"14:00-18:00",840,1080,12,"NORMAL","electronice"),
            o(11,"Warehouse Partner","Chitila Logistic Park",44.515,25.973,230,"09:00-15:00",540,900,15,"URGENT","fashion"),
            o(12,"Clinic Vest","Iuliu Maniu 65",44.434,26.018,70,"08:30-12:30",510,750,10,"URGENT","farmaceutice"),
            o(13,"Mobila Berceni","Berceni 88",44.377,26.126,310,"12:00-18:00",720,1080,18,"NORMAL","mobila"),
            o(14,"Librărie Floreasca","Calea Floreasca 180",44.466,26.102,35,"09:00-11:30",540,690,8,"NORMAL","carti"),
            o(15,"Supermarket Titan","Bd. 1 Decembrie 1918",44.426,26.168,240,"08:30-12:00",510,720,14,"URGENT","alimente"),
            o(16,"Clinică Pipera","Erou Iancu Nicolae 30",44.500,26.121,65,"09:30-13:00",570,780,10,"URGENT","farmaceutice"),
            o(17,"Showroom Militari","Preciziei 12",44.431,25.987,150,"15:00-19:00",900,1140,12,"SCĂZUTĂ","electronice"),
            o(18,"Client Otopeni","Str. 23 August",44.549,26.072,45,"10:00-14:30",600,870,8,"NORMAL","fashion"),
            o(19,"Magazin Rahova","Calea Rahovei 260",44.407,26.052,130,"11:00-15:30",660,930,12,"NORMAL","alimente"),
            o(20,"Mobila Voluntari","Voluntari Retail Park",44.492,26.164,280,"13:00-18:00",780,1080,18,"NORMAL","mobila"),
            o(21,"Farmacie Colentina","Șos. Colentina 210",44.464,26.141,60,"08:00-10:00",480,600,10,"URGENT","farmaceutice"),
            o(22,"Book Sud","Șos. Olteniței 52",44.397,26.119,50,"10:30-14:00",630,840,8,"SCĂZUTĂ","carti"),
            o(23,"Outlet Fashion Vest","Chiajna",44.459,25.980,110,"14:00-18:00",840,1080,12,"NORMAL","fashion"),
            o(24,"Grocery Nord","Mogoșoaia",44.529,25.993,190,"08:30-12:30",510,750,14,"NORMAL","alimente")
        ));
    }

    public synchronized List<DepotDto> depots() { return List.copyOf(depots); }
    public synchronized DepotDto depot() { return depots.get(1); }
    public synchronized List<DriverDto> drivers() { return List.copyOf(drivers); }
    public synchronized List<VehicleDto> vehicles() { return List.copyOf(vehicles); }
    public synchronized List<OrderDto> orders() { return List.copyOf(orders); }
    public synchronized SettingsDto settings() { return settings; }
    public synchronized SettingsDto updateSettings(SettingsDto incoming) {
        int realSeconds = Math.max(60, Math.min(300, incoming.realSolverSeconds()));
        int hypotheticalSeconds = Math.max(60, Math.min(300, incoming.hypotheticalSolverSeconds()));
        double fuelPrice = Math.max(1.0, Math.min(30.0, incoming.fuelPriceRonPerLiter()));
        double driverSalary = Math.max(0.0, Math.min(2000.0, incoming.driverDailySalaryRon()));
        settings = new SettingsDto(realSeconds, hypotheticalSeconds, Math.round(fuelPrice * 100.0) / 100.0, Math.round(driverSalary * 100.0) / 100.0);
        return settings;
    }

    public synchronized OrderDto addOrder(OrderDto o) { long next = orders.stream().mapToLong(OrderDto::id).max().orElse(0) + 1; OrderDto created = new OrderDto(next, o.customerName(), o.address(), o.latitude(), o.longitude(), o.weightKg(), o.timeWindow(), o.windowStartMinute(), o.windowEndMinute(), o.serviceMinutes(), o.priority(), o.requiredProduct()); orders.add(created); return created; }
    public synchronized void deleteOrder(long id) { orders.removeIf(o -> o.id() == id); }
    public synchronized DriverDto addDriver(DriverDto d) { String id = "DRV-" + String.format("%02d", drivers.size() + 1); DriverDto created = driver(id, d.name()); drivers.add(created); return created; }
    public synchronized void deleteDriver(String id) { drivers.removeIf(d -> d.id().equals(id)); vehicles.removeIf(v -> v.driverId().equals(id)); }
    public synchronized VehicleDto addVehicle(VehicleDto v) { String code = "CAMION-" + String.format("%02d", vehicles.size() + 1); String driverName = drivers.stream().filter(d -> d.id().equals(v.driverId())).map(DriverDto::name).findFirst().orElse("Șofer neasignat"); VehicleDto created = new VehicleDto(code, v.driverId(), driverName, v.depotId(), STANDARD_TRUCK_CAPACITY_KG, STANDARD_TRUCK_CONSUMPTION, STANDARD_TRUCK_COST_RON_PER_KM); vehicles.add(created); return created; }
    public synchronized void deleteVehicle(String code) { vehicles.removeIf(v -> v.code().equals(code)); }

    private DriverDto driver(String id, String name) { return new DriverDto(id, name, LEGAL_DAILY_DRIVE_MINUTES, LEGAL_BREAK_AFTER_MINUTES, LEGAL_BREAK_DURATION_MINUTES, LEGAL_DAILY_REST_MINUTES, LEGAL_WEEKLY_DRIVE_MINUTES, LEGAL_TWO_WEEK_DRIVE_MINUTES); }
    private VehicleDto vehicle(String code, String driverId, String depotId) { String driverName = drivers.stream().filter(d -> d.id().equals(driverId)).map(DriverDto::name).findFirst().orElse("Șofer neasignat"); return new VehicleDto(code, driverId, driverName, depotId, STANDARD_TRUCK_CAPACITY_KG, STANDARD_TRUCK_CONSUMPTION, STANDARD_TRUCK_COST_RON_PER_KM); }
    private OrderDto o(long id, String name, String address, double lat, double lon, double weight, String tw, int start, int end, int service, String priority, String product) { return new OrderDto(id, name, address, lat, lon, weight, tw, start, end, service, priority, product); }
}
