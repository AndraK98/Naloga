import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.serialization.GtfsReader;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

public class BusTripsApp {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        String userInput = scanner.nextLine();

        String[] parts = userInput.split(" ");

        String stationId = parts[1];
        int numberOfBuses = Integer.parseInt(parts[2]);
        String timeFormat = parts[3];

        GtfsDaoImpl gtfsData;
        try {
            gtfsData = loadGtfsData();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        LocalTime currentTime = LocalTime.of(12,0);

        Stop stationStop = findStopForStation(gtfsData, stationId);

        if (stationStop == null) {
            System.out.println("No stop found for the specified station ID.");
            return;
        }

        System.out.println(stationStop.getName() + " stop (ID " + stationStop.getId() + ")");

        List<Trip> nextBuses = findNextBuses(gtfsData, stationStop, currentTime, Duration.ofHours(2));

        if (nextBuses.isEmpty()) {
            System.out.println("No upcoming buses found.");
            return;
        }

        Map<Route, List<Trip>> tripsByRoute = groupTripsByRoute(nextBuses);

        for (Map.Entry<Route, List<Trip>> entry : tripsByRoute.entrySet()) {
            Route route = entry.getKey();
            List<Trip> trips = entry.getValue();

            System.out.print(route.getShortName() + ": ");

            StringBuilder schedule = new StringBuilder();
            for (int i = 0; i < numberOfBuses && i < trips.size(); i++) {
                Trip trip = trips.get(i);
                LocalTime busTime = getArrivalTimeForStop(gtfsData, trip, stationStop);

                if (timeFormat.equals("relative")) {
                    Duration timeUntilArrival = Duration.between(currentTime, busTime);
                    long minutesUntilArrival = timeUntilArrival.toMinutes();
                    schedule.append(minutesUntilArrival).append("min, ");
                } else if (timeFormat.equals("absolute")) {
                    schedule.append(busTime.toString()).append(", ");
                }
            }

            if (schedule.length() > 0) {
                schedule.delete(schedule.length() - 2, schedule.length());
                System.out.println(schedule);
            }
        }
    }

    private static GtfsDaoImpl loadGtfsData() throws IOException {
        GtfsDaoImpl store = new GtfsDaoImpl();
        GtfsReader reader = new GtfsReader();
        reader.setInputLocation(new File("src/main/resources/gtfs"));
        reader.setEntityStore(store);
        try {
            reader.run();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return store;
    }

    private static Stop findStopForStation(GtfsDaoImpl gtfsData, String stationId) {
        for (Stop stop : gtfsData.getAllStops()) {
            if (stop.getId().equals(stationId)) {
                return stop;
            }
        }
        return null;
    }

    private static List<Trip> findNextBuses(GtfsDaoImpl gtfsData, Stop stationStop, LocalTime currentTime, Duration maxDuration) {
        List<Trip> nextBuses = new ArrayList<>();

        for (Trip trip : gtfsData.getAllTrips()) {
            LocalTime arrivalTime = getArrivalTimeForStop(gtfsData, trip, stationStop);
            if (arrivalTime != null) {
                Duration timeUntilArrival = Duration.between(currentTime, arrivalTime);
                if (timeUntilArrival.compareTo(maxDuration) <= 0 && timeUntilArrival.toMinutes() >= 0) {
                    nextBuses.add(trip);
                }
            }
        }

        nextBuses.sort(Comparator.comparing(trip -> Duration.between(currentTime, getArrivalTimeForStop(gtfsData, trip, stationStop))));

        return nextBuses;
    }

    private static LocalTime getArrivalTimeForStop(GtfsDaoImpl gtfsData, Trip trip, Stop stop) {
        for (StopTime stopTime : gtfsData.getAllStopTimes()) {
            if (stopTime.getStop().equals(stop) && stopTime.getTrip().equals(trip)) {
                int arrivalTime = stopTime.getArrivalTime();
                LocalTime midnight = LocalTime.MIDNIGHT;
                return midnight.plusSeconds(arrivalTime);
            }
        }
        return null;
    }

    private static Map<Route, List<Trip>> groupTripsByRoute(List<Trip> trips) {
        return trips.stream()
                .collect(Collectors.groupingBy(Trip::getRoute));
    }

}
