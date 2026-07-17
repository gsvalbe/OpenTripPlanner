package org.opentripplanner.transit.service;

import static com.google.common.truth.Truth.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.model.StopTimesInPattern;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;

/**
 * Tests how the {@link ArrivalDeparture} filter treats a call which allows neither boarding nor
 * alighting, such as a pass-through timing point.
 */
class StopTimesHelperPickDropTest {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 5, 8);
  private static final ZoneId TIME_ZONE = ZoneId.of("Europe/Paris");
  private static final String TRIP_ID = "TestTrip";

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of(SERVICE_DATE);
  private final RegularStop STOP_A = envBuilder.stop("A", b -> b.withCoordinate(60.0, 10.0));
  private final RegularStop STOP_B = envBuilder.stop("B", b -> b.withCoordinate(60.0, 10.01));
  private final RegularStop STOP_C = envBuilder.stop("C", b -> b.withCoordinate(60.0, 10.02));

  /**
   * STOP_B allows neither boarding nor alighting - the vehicle passes through it.
   */
  private final TripInput tripInput = TripInput.of(TRIP_ID)
    .addStop(STOP_A, "12:00:00", "12:00:00")
    .addStop(STOP_B, "12:30:00", "12:30:00", PickDrop.NONE, PickDrop.NONE)
    .addStop(STOP_C, "13:00:00", "13:00:00");

  private final Instant startTime = SERVICE_DATE.atTime(LocalTime.of(11, 0))
    .atZone(TIME_ZONE)
    .toInstant();

  private final TransitTestEnvironment env = envBuilder.addTrip(tripInput).build();

  @Test
  void allReturnsPassThroughCall() {
    assertThat(callsAtPassThroughStop(ArrivalDeparture.ALL)).hasSize(1);
  }

  @Test
  void bothOmitsPassThroughCall() {
    assertThat(callsAtPassThroughStop(ArrivalDeparture.BOTH)).isEmpty();
  }

  @Test
  void departuresOmitsPassThroughCall() {
    assertThat(callsAtPassThroughStop(ArrivalDeparture.DEPARTURES)).isEmpty();
  }

  @Test
  void arrivalsOmitsPassThroughCall() {
    assertThat(callsAtPassThroughStop(ArrivalDeparture.ARRIVALS)).isEmpty();
  }

  /**
   * A regular call is returned by every filter, so ALL must not change the existing behaviour.
   */
  @Test
  void regularCallIsReturnedByAllFilters() {
    for (var arrivalDeparture : ArrivalDeparture.values()) {
      assertThat(callsAt(STOP_A, arrivalDeparture)).hasSize(1);
    }
  }

  private List<StopTimesInPattern> callsAtPassThroughStop(ArrivalDeparture arrivalDeparture) {
    return callsAt(STOP_B, arrivalDeparture);
  }

  private List<StopTimesInPattern> callsAt(RegularStop stop, ArrivalDeparture arrivalDeparture) {
    return env
      .transitService()
      .findStopTimesInPattern(stop, startTime, Duration.ofHours(4), 10, arrivalDeparture, false);
  }
}
