package org.opentripplanner.apis.gtfs.datafetchers;

import static com.google.common.truth.Truth.assertThat;
import static org.opentripplanner.apis.support.graphql.DataFetchingSupport.dataFetchingEnvironment;

import org.junit.jupiter.api.Test;
import org.opentripplanner.apis.gtfs.generated.GraphQLTypes;
import org.opentripplanner.core.model.accessibility.Accessibility;
import org.opentripplanner.model.TripTimeOnDate;
import org.opentripplanner.transit.model._data.TimetableRepositoryForTest;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.timetable.ScheduledTripTimes;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;

class StoptimeImplTest {

  private static final TimetableRepositoryForTest MODEL = TimetableRepositoryForTest.of();
  private static final RegularStop STOP = MODEL.stop("r1", 60.0, 10.0).build();
  private static final StopPattern STOP_PATTERN = TimetableRepositoryForTest.stopPattern(
    STOP,
    STOP,
    STOP
  );
  private static final Trip TRIP = TimetableRepositoryForTest.trip("trip1")
    .withWheelchairBoarding(Accessibility.POSSIBLE)
    .build();
  private static final TripPattern PATTERN = TimetableRepositoryForTest.tripPattern(
    "p",
    TRIP.getRoute()
  )
    .withStopPattern(STOP_PATTERN)
    .build();
  private static final ScheduledTripTimes TRIP_TIMES = ScheduledTripTimes.of()
    .withArrivalTimes("10:00 11:00 12:00")
    .withGtfsSequenceOfStopIndex(new int[] { 0, 1, 2 })
    .withTrip(TRIP)
    .build();
  private static final StoptimeImpl IMPL = new StoptimeImpl();

  @Test
  void wheelchairAccessibleFallsBackToScheduledValue() throws Exception {
    var env = dataFetchingEnvironment(stoptime(TRIP_TIMES));
    assertThat(IMPL.wheelchairAccessible().get(env)).isEqualTo(
      GraphQLTypes.GraphQLWheelchairBoarding.POSSIBLE
    );
  }

  @Test
  void wheelchairAccessibleUsesRealTimeValue() throws Exception {
    var realTimeTripTimes = TRIP_TIMES.createRealTimeFromScheduledTimes()
      .withWheelchairAccessibility(Accessibility.NOT_POSSIBLE)
      .build();
    var env = dataFetchingEnvironment(stoptime(realTimeTripTimes));
    assertThat(IMPL.wheelchairAccessible().get(env)).isEqualTo(
      GraphQLTypes.GraphQLWheelchairBoarding.NOT_POSSIBLE
    );
  }

  private static TripTimeOnDate stoptime(TripTimes tripTimes) {
    return new TripTimeOnDate(tripTimes, 0, PATTERN);
  }
}
