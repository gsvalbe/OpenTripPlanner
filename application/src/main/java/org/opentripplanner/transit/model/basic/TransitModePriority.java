package org.opentripplanner.transit.model.basic;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Defines which {@link TransitMode} is the most important one when a single representative mode
 * must be chosen for a stop or station, regardless of how many patterns use each mode.
 * <p>
 * The order, from most to least important: RAIL, AIRPLANE, FERRY, SUBWAY, MONORAIL, TRAM,
 * FUNICULAR, CABLE_CAR, GONDOLA, TROLLEYBUS, COACH, BUS, TAXI, CARPOOL, SNOW_AND_ICE.
 */
public final class TransitModePriority {

  private static final List<TransitMode> PRIORITY = List.of(
    TransitMode.RAIL,
    TransitMode.AIRPLANE,
    TransitMode.FERRY,
    TransitMode.SUBWAY,
    TransitMode.MONORAIL,
    TransitMode.TRAM,
    TransitMode.FUNICULAR,
    TransitMode.CABLE_CAR,
    TransitMode.GONDOLA,
    TransitMode.TROLLEYBUS,
    TransitMode.COACH,
    TransitMode.BUS,
    TransitMode.TAXI,
    TransitMode.CARPOOL,
    TransitMode.SNOW_AND_ICE
  );

  /** Orders the most important mode first. */
  public static final Comparator<TransitMode> COMPARATOR = Comparator.comparingInt(
    PRIORITY::indexOf
  );

  private TransitModePriority() {}

  /** Return the distinct modes, most important first. */
  public static List<TransitMode> sortDistinct(Stream<TransitMode> modes) {
    return modes.distinct().sorted(COMPARATOR).toList();
  }

  /** Return the most important mode, or empty if there are none. */
  public static Optional<TransitMode> highest(Stream<TransitMode> modes) {
    return modes.min(COMPARATOR);
  }
}
