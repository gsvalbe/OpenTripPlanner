package org.opentripplanner.ext.generalpattern.model;

import java.io.Serializable;
import java.util.BitSet;
import java.util.List;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 * One station-level stop sequence inside a general pattern. Several {@link TripPattern}s collapse
 * into a variant when they differ only by platform or pickup/drop-off rules. A variant passes,
 * without stopping, every stop between its first and last stop that it does not serve.
 */
public final class GeneralPatternVariant implements Serializable {

  private final List<TripPattern> patterns;
  private final BitSet servedStops;
  private final int scheduledTrips;

  public GeneralPatternVariant(List<TripPattern> patterns, BitSet servedStops, int scheduledTrips) {
    if (patterns.isEmpty()) {
      throw new IllegalArgumentException("A variant needs at least one pattern");
    }
    if (servedStops.cardinality() < 2) {
      throw new IllegalArgumentException("A variant must serve at least two stops");
    }
    this.patterns = List.copyOf(patterns);
    this.servedStops = (BitSet) servedStops.clone();
    this.scheduledTrips = scheduledTrips;
  }

  /** The OTP trip patterns merged into this variant. */
  public List<TripPattern> patterns() {
    return patterns;
  }

  /** Index, in the general pattern, of the first stop served. */
  public int firstStopIndex() {
    return servedStops.nextSetBit(0);
  }

  /** Index, in the general pattern, of the last stop served. */
  public int lastStopIndex() {
    return servedStops.length() - 1;
  }

  public boolean servesStop(int stopIndex) {
    return servedStops.get(stopIndex);
  }

  /** The vehicle passes the stop without stopping. */
  public boolean passesStop(int stopIndex) {
    return reachesStop(stopIndex) && !servesStop(stopIndex);
  }

  /** The stop lies between the variant's first and last stop. */
  public boolean reachesStop(int stopIndex) {
    return stopIndex >= firstStopIndex() && stopIndex <= lastStopIndex();
  }

  /** Scheduled trips over the whole timetable. */
  public int scheduledTrips() {
    return scheduledTrips;
  }

  @Override
  public String toString() {
    return ToStringBuilder.of(GeneralPatternVariant.class)
      .addNum("patterns", patterns.size())
      .addNum("first", firstStopIndex())
      .addNum("last", lastStopIndex())
      .addNum("served", servedStops.cardinality())
      .addNum("trips", scheduledTrips)
      .toString();
  }
}
