package org.opentripplanner.ext.generalpattern.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
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

  private GeneralPattern(
    FeedScopedId id,
    Route route,
    List<GeneralPatternStop> stops,
    List<GeneralPatternVariant> variants
  ) {
    this.id = id;
    this.route = route;
    this.stops = List.copyOf(stops);
    this.variants = List.copyOf(variants);
  }

  /**
   * Create a general pattern. The trip counts of every stop are derived from the
   * variants, whose stop indexes refer to {@code places}.
   */
  public static GeneralPattern of(
    FeedScopedId id,
    Route route,
    List<GeneralPatternPlace> places,
    List<GeneralPatternVariant> variants
  ) {
    Objects.requireNonNull(id);
    Objects.requireNonNull(route);
    if (places.size() < 2) {
      throw new IllegalArgumentException("A general pattern needs at least two stops: " + id);
    }
    if (variants.isEmpty()) {
      throw new IllegalArgumentException("A general pattern needs at least one variant: " + id);
    }
    for (var v : variants) {
      if (v.lastStopIndex() >= places.size()) {
        throw new IllegalArgumentException("A variant refers to a stop outside the pattern: " + id);
      }
    }
    var sorted = new ArrayList<>(variants);
    sorted.sort(Comparator.comparingInt(GeneralPatternVariant::scheduledTrips).reversed());

    List<GeneralPatternStop> stops = new ArrayList<>(places.size());
    for (int k = 0; k < places.size(); k++) {
      int reaching = 0,
        served = 0,
        passing = 0,
        starting = 0,
        ending = 0;
      for (var v : sorted) {
        if (!v.reachesStop(k)) {
          continue;
        }
        int trips = v.scheduledTrips();
        reaching += trips;
        if (v.servesStop(k)) {
          served += trips;
        } else if (v.passesStop(k)) {
          passing += trips;
        }
        if (v.firstStopIndex() == k) {
          starting += trips;
        }
        if (v.lastStopIndex() == k) {
          ending += trips;
        }
      }
      int detour = reaching - served - passing;
      stops.add(
        new GeneralPatternStop(
          places.get(k),
          k == 0 || k == places.size() - 1,
          reaching,
          served,
          passing,
          detour,
          starting,
          ending
        )
      );
    }

    return new GeneralPattern(id, route, stops, sorted);
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

  /** The OTP trip patterns merged into this general pattern, most trips first. */
  public List<TripPattern> tripPatterns() {
    return variants
      .stream()
      .flatMap(v -> v.patterns().stream())
      .toList();
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
