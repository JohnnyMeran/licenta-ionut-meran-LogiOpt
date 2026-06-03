package ro.licenta.logistics.dto;

public record DriverDto(String id, String name, int maxDailyDriveMinutes, int breakAfterMinutes, int breakDurationMinutes, int dailyRestMinutes, int maxWeeklyDriveMinutes, int maxBiWeeklyDriveMinutes) {}
