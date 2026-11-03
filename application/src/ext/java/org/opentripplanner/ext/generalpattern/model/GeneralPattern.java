package org.opentripplanner.ext.generalpattern.model;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 * A readable stop sequence for one route and direction. It merges every trip pattern that differs
 * only by platform, skipped stops, short turns or small detours, so that riders see one line
 * instead of many near-identical patterns. Every variant's stops are a subsequence of
 * {@link #stops()}.
 * <p>
 * General patterns describe the planned timetable. They are built once during graph build and are
 * not changed by real-time updates.
 */
public final class GeneralPattern implements Serializable {

  private final FeedScopedId id;
  private final Route route;
  private final List<GeneralPatternStop> stops;
  private final List<GeneralPatternVariant> variants;
  private final int[] segmentTrips;

  public GeneralPattern(
    FeedScopedId id,
    Route route,
    List<GeneralPatternStop> stops,
    List<GeneralPatternVariant> variants,
    int[] segmentTrips
  ) {
    this.id = Objects.requireNonNull(id);
    this.route = Objects.requireNonNull(route);
    this.stops = List.copyOf(stops);
    this.variants = List.copyOf(variants);
    this.segmentTrips = segmentTrips.clone();
    if (this.stops.size() < 2) {
      throw new IllegalArgumentException("A general pattern needs at least two stops: " + id);
    }
    if (this.variants.isEmpty()) {
      throw new IllegalArgumentException("A general pattern needs at least one variant: " + id);
    }
    if (this.segmentTrips.length != this.stops.size() - 1) {
      throw new IllegalArgumentException("One segment per pair of consecutive stops: " + id);
    }
  }

  public FeedScopedId id() {
    return id;
  }

  public Route route() {
    return route;
  }

  /** Stops in travel order. */
  public List<GeneralPatternStop> stops() {
    return stops;
  }

  /** Variants, most trips first. */
  public List<GeneralPatternVariant> variants() {
    return variants;
  }

  public GeneralPatternStop firstStop() {
    return stops.getFirst();
  }

  public GeneralPatternStop lastStop() {
    return stops.getLast();
  }

  /** Scheduled trips of all variants. */
  public int scheduledTrips() {
    return variants.stream().mapToInt(GeneralPatternVariant::scheduledTrips).sum();
  }

  /**
   * Trips running the segment from stop {@code fromStopIndex} to the next stop, whether or not
   * they stop at either end.
   */
  public int segmentTrips(int fromStopIndex) {
    return segmentTrips[fromStopIndex];
  }

  /** Share of all trips running the segment from {@code fromStopIndex} to the next stop. */
  public double segmentShare(int fromStopIndex) {
    return (double) segmentTrips[fromStopIndex] / scheduledTrips();
  }

  /** The first and last stops of the general pattern are line termini. */
  public boolean isLineTerminus(int stopIndex) {
    return stopIndex == 0 || stopIndex == stops.size() - 1;
  }

  @Override
  public String toString() {
    return ToStringBuilder.of(GeneralPattern.class)
      .addObj("id", id)
      .addObj("first", firstStop().stop().getName())
      .addObj("last", lastStop().stop().getName())
      .addNum("stops", stops.size())
      .addNum("variants", variants.size())
      .addNum("trips", scheduledTrips())
      .toString();
  }
}
