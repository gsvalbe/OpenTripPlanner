package org.opentripplanner.ext.generalpattern.internal.graphbuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.ext.generalpattern.model.GeneralPattern;
import org.opentripplanner.ext.generalpattern.model.GeneralPatternStop;
import org.opentripplanner.ext.generalpattern.model.StopStatus;
import org.opentripplanner.ext.generalpattern.parameters.GeneralPatternParameters;
import org.opentripplanner.model.StopTime;
import org.opentripplanner.transit.model._data.TransitRepositoryForTest;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.framework.Deduplicator;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.site.Station;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimesFactory;

class GeneralPatternGraphBuilderTest {

  /** Roughly 4.8 km between consecutive stops at latitude 57. */
  private static final double STEP = 0.08;
  private static final double LAT = 57.0;
  private static final double LON = 24.0;

  private final TransitRepositoryForTest model = TransitRepositoryForTest.of();
  private final Route route = TransitRepositoryForTest.route("R1")
    .withMode(TransitMode.RAIL)
    .build();
  private int patternCounter = 0;

  private final RegularStop a = stopOnLine("A", 0);
  private final RegularStop b = stopOnLine("B", 1);
  private final RegularStop c = stopOnLine("C", 2);
  private final RegularStop d = stopOnLine("D", 3);
  private final RegularStop e = stopOnLine("E", 4);

  @Test
  void shortTurnsAndSkippedStopsFormOneGeneralPattern() {
    var gps = build(pattern(3, a, b, c, d, e), pattern(1, a, b, c), pattern(1, a, c, e));

    assertEquals(1, gps.size());
    var gp = gps.getFirst();
    assertEquals(List.of("A", "B", "C", "D", "E"), names(gp));
    assertEquals(3, gp.variants().size());
    assertEquals(3, gp.variants().getFirst().scheduledTrips());

    var stopB = gp.stops().get(1);
    assertEquals(5, stopB.reachingTrips());
    assertEquals(4, stopB.servedTrips());
    assertEquals(1, stopB.passingTrips());
    assertEquals(StopStatus.SOMETIMES_SKIPPED, stopB.status());

    var stopC = gp.stops().get(2);
    assertEquals(StopStatus.ALWAYS, stopC.status());
    assertEquals(1, stopC.endingTrips());

    var stopD = gp.stops().get(3);
    assertEquals(4, stopD.reachingTrips());
    assertEquals(StopStatus.SOMETIMES_SKIPPED, stopD.status());
  }

  @Test
  void oppositeDirectionsAreSeparateWithoutDirectionId() {
    var gps = build(pattern(3, a, b, c, d, e), pattern(2, e, d, c, b, a));

    assertEquals(2, gps.size());
    assertEquals(List.of("A", "B", "C", "D", "E"), names(gps.get(0)));
    assertEquals(List.of("E", "D", "C", "B", "A"), names(gps.get(1)));
  }

  @Test
  void platformsOfOneStationCollapseIntoOneVariant() {
    Station station = model
      .station("S")
      .withCoordinate(LAT, LON + 2 * STEP)
      .build();
    var platform1 = model
      .stop("S1", LAT, LON + 2 * STEP)
      .withParentStation(station)
      .build();
    var platform2 = model
      .stop("S2", LAT, LON + 2 * STEP)
      .withParentStation(station)
      .build();

    var gps = build(pattern(2, a, b, platform1, d), pattern(1, a, b, platform2, d));

    assertEquals(1, gps.size());
    var gp = gps.getFirst();
    assertEquals(1, gp.variants().size());
    assertEquals(2, gp.variants().getFirst().patterns().size());
    GeneralPatternStop stop = gp.stops().get(2);
    assertEquals(station, stop.station());
    assertEquals(Set.of(platform1, platform2), new HashSet<>(stop.mergedStops()));
  }

  @Test
  void branchesStaySeparate() {
    var x = model.stop("X", LAT + 0.045, LON + 2 * STEP).build();
    var y = model.stop("Y", LAT + 0.09, LON + 2 * STEP).build();

    var gps = build(pattern(2, a, b, c, d, e), pattern(1, a, b, c, x, y));

    assertEquals(2, gps.size());
  }

  @Test
  void expressTripsServingDifferentStopsOnOneRoadAreMerged() {
    var v1 = stopOnLine("V1", 1);
    var v2 = stopOnLine("V2", 2);
    var v3 = stopOnLine("V3", 3);

    var gps = build(pattern(1, a, v1, v3, e), pattern(1, a, v2, e));

    assertEquals(1, gps.size());
    var gp = gps.getFirst();
    assertEquals(List.of("A", "V1", "V2", "V3", "E"), names(gp));
    for (int i = 1; i <= 3; i++) {
      assertEquals(StopStatus.SOMETIMES_SKIPPED, gp.stops().get(i).status(), names(gp).get(i));
    }
  }

  @Test
  void detourWithUnambiguousOrderIsMerged() {
    // X lies about 2 km off the line between B and C
    var x = model.stop("X", LAT + 0.018, LON + 1.5 * STEP).build();

    var gps = build(pattern(3, a, b, c), pattern(1, a, b, x, c));

    assertEquals(1, gps.size());
    var stopX = gps.getFirst().stops().get(2);
    assertEquals("X", stopX.stop().getName().toString());
    assertEquals(StopStatus.DETOUR, stopX.status());
    assertEquals(3, stopX.detourTrips());
  }

  @Test
  void loopVisitingAStationTwiceKeepsBothVisits() {
    var gps = build(pattern(2, a, b, c, b, a));

    assertEquals(1, gps.size());
    assertEquals(List.of("A", "B", "C", "B", "A"), names(gps.getFirst()));
  }

  private List<GeneralPattern> build(TripPattern... patterns) {
    Set<StopLocation> stops = new HashSet<>();
    Arrays.stream(patterns).forEach(p -> stops.addAll(p.getStops()));
    Map<StopLocation, StationKey> keys = StationKeyResolver.resolve(stops, 400);
    var builder = new GeneralPatternGraphBuilder(GeneralPatternParameters.DEFAULT, null, null);
    var result = builder.build(route, Arrays.asList(patterns), keys);
    result.forEach(gp -> assertNotNull(gp.id()));
    return result;
  }

  private RegularStop stopOnLine(String name, double position) {
    return model.stop(name, LAT, LON + position * STEP).build();
  }

  private TripPattern pattern(int trips, RegularStop... stops) {
    String id = "P" + ++patternCounter;
    return TransitRepositoryForTest.tripPattern(id, route)
      .withStopPattern(TransitRepositoryForTest.stopPattern(stops))
      .withScheduledTimeTableBuilder(builder -> {
        for (int t = 0; t < trips; t++) {
          Trip trip = TransitRepositoryForTest.trip(id + "-" + t)
            .withRoute(route)
            .build();
          List<StopTime> stopTimes = new ArrayList<>();
          for (int s = 0; s < stops.length; s++) {
            var st = model.stopTime(trip, s, stops[s]);
            int time = 3600 + t * 900 + s * 300;
            st.setArrivalTime(time);
            st.setDepartureTime(time);
            stopTimes.add(st);
          }
          builder.addTripTimes(TripTimesFactory.tripTimes(trip, stopTimes, new Deduplicator()));
        }
        return builder;
      })
      .build();
  }

  private static List<String> names(GeneralPattern gp) {
    return gp
      .stops()
      .stream()
      .map(s -> s.stop().getName().toString())
      .toList();
  }

  @Test
  void stationsWithoutParentAreMergedByNameAndDistance() {
    // Same name, other side of the road (about 110 m away) and in another village (about 5.5 km)
    var near = model
      .stop("A-near", LAT + 0.001, LON)
      .withName(new NonLocalizedString("A."))
      .build();
    var far = model
      .stop("A-far", LAT + 0.05, LON)
      .withName(new NonLocalizedString("a"))
      .build();

    var keys = StationKeyResolver.resolve(List.of(a, near, far, b), 400);

    assertEquals(keys.get(a), keys.get(near));
    assertNotEquals(keys.get(a), keys.get(far));
    assertNotEquals(keys.get(a), keys.get(b));
  }
}
