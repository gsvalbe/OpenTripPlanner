package org.opentripplanner.transit.model.network;

/**
 * How typical a stopping pattern is for its route and direction, as defined by the
 * {@code trip_pattern_type} column in {@code trips.txt}.
 */
public enum TripPatternType {
  /** Primary pattern — the most common, representative stopping pattern. */
  PRIMARY(0),
  /** Secondary pattern — an occasional alternative to the primary pattern. */
  SECONDARY(1),
  /** Technical pattern — used for operational purposes (e.g. deadheads, short turns). */
  TECHNICAL(2),
  /** No-service pattern — reference pattern not scheduled for passenger service. */
  NO_SERVICE(3);

  public final int gtfsCode;

  TripPatternType(int gtfsCode) {
    this.gtfsCode = gtfsCode;
  }

  public static TripPatternType ofGtfsCode(int code) {
    return switch (code) {
      case 1 -> SECONDARY;
      case 2 -> TECHNICAL;
      case 3 -> NO_SERVICE;
      default -> PRIMARY;
    };
  }
}
