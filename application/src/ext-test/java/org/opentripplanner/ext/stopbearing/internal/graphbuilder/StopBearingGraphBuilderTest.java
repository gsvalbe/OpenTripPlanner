package org.opentripplanner.ext.stopbearing.internal.graphbuilder;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.stopbearing.parameters.StopBearingParameters;
import org.opentripplanner.graph_builder.issue.service.DefaultDataImportIssueStore;
import org.opentripplanner.model.StopTime;
import org.opentripplanner.street.geometry.GeometryUtils;
import org.opentripplanner.transit.model._data.TransitRepositoryForTest;
import org.opentripplanner.transit.model.framework.Deduplicator;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.network.TripPatternBuilder;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.site.Station;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimesFactory;

class StopBearingGraphBuilderTest {

  private static final double LAT = 57.0;
  private static final double LON = 24.0;

  /** About 100 m of longitude at latitude 57, short enough to use the straight-line hops. */
  private static final double STEP = 0.00165;

  private final TransitRepositoryForTest model = TransitRepositoryForTest.of();
  private final Route route = TransitRepositoryForTest.route("R1").build();
  private final DefaultDataImportIssueStore issueStore = new DefaultDataImportIssueStore();
  private int patternCounter = 0;

  private final RegularStop a = stopOnLine("A", 0);
  private final RegularStop b = stopOnLine("B", 1);
  private final RegularStop c = stopOnLine("C", 2);

  @Test
  void terminalHasNoBearing() {
    var result = compute(pattern(1, a, b, c));

    assertThat(result).containsExactly(a.getId(), 90, b.getId(), 90);
    assertThat(issueStore.listIssues()).isEmpty();
  }

  @Test
  void stopServedInBothDirectionsHasNoBearing() {
    var result = compute(pattern(2, a, b, c), pattern(2, c, b, a));

    assertThat(result).containsExactly(a.getId(), 90, c.getId(), 270);
    assertThat(issueStore.listIssues()).hasSize(1);
    assertThat(issueStore.listIssues().getFirst().getMessage()).contains(b.getId().toString());
  }

  @Test
  void tripCountsWeighTheDirections() {
    // One trip in 40 leaves B westwards, below the default outlier share
    var result = compute(pattern(39, a, b, c), pattern(1, c, b, a));

    assertThat(result).containsEntry(b.getId(), 90);
  }

  @Test
  void stationCombinesTheDeparturesOfItsStops() {
    Station station = model
      .station("S")
      .withCoordinate(LAT, LON + STEP)
      .build();
    // The same physical stop, published by two feeds
    var s1 = model
      .stop("S1", LAT, LON + STEP)
      .withParentStation(station)
      .build();
    var s2 = model
      .stop("S2", LAT, LON + STEP)
      .withParentStation(station)
      .build();

    var result = compute(pattern(1, a, s1, c), pattern(1, a, s2, c));

    assertThat(result).containsEntry(s1.getId(), 90);
    assertThat(result).containsEntry(s2.getId(), 90);
    assertThat(result).containsEntry(station.getId(), 90);
  }

  @Test
  void stationWithPlatformsInOppositeDirectionsHasNoBearing() {
    Station station = model
      .station("S")
      .withCoordinate(LAT, LON + STEP)
      .build();
    var east = model
      .stop("S1", LAT, LON + STEP)
      .withParentStation(station)
      .build();
    var west = model
      .stop("S2", LAT, LON + STEP)
      .withParentStation(station)
      .build();

    var result = compute(pattern(1, a, east, c), pattern(1, c, west, a));

    assertThat(result).containsEntry(east.getId(), 90);
    assertThat(result).containsEntry(west.getId(), 270);
    assertThat(result).doesNotContainKey(station.getId());
  }

  @Test
  void shapeIsUsedInsteadOfTheLineBetweenStops() {
    // The shape leaves A northwards before turning east to B
    var hop = GeometryUtils.makeLineString(LON, LAT, LON, LAT + 0.001, LON + STEP, LAT + 0.001);
    var straight = GeometryUtils.makeLineString(LON + STEP, LAT, LON + 2 * STEP, LAT);
    var pattern = patternBuilder(1, a, b, c).withHopGeometries(List.of(hop, straight)).build();

    assertThat(compute(pattern)).containsEntry(a.getId(), 0);
  }

  @Test
  void patternWithoutTripsIsIgnored() {
    assertThat(compute(pattern(0, a, b, c))).isEmpty();
  }

  private Map<FeedScopedId, Integer> compute(TripPattern... patterns) {
    var builder = new StopBearingGraphBuilder(
      StopBearingParameters.DEFAULT,
      null,
      null,
      issueStore
    );
    return builder.compute(List.of(patterns));
  }

  private RegularStop stopOnLine(String name, double position) {
    return model.stop(name, LAT, LON + position * STEP).build();
  }

  private TripPattern pattern(int trips, RegularStop... stops) {
    return patternBuilder(trips, stops).build();
  }

  private TripPatternBuilder patternBuilder(int trips, RegularStop... stops) {
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
      });
  }
}
