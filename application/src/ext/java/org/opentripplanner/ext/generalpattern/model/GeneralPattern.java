package org.opentripplanner.ext.generalpattern.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.StopSequenceNamer;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 * A readable stop sequence for one route and direction. It merges every trip pattern that differs
 * only by platform, skipped stops, or trips that start or end at other stops along the line, so
 * that riders see one line instead of many near-identical patterns. Patterns that take another road
 * belong to another general pattern. Every variant's stops are a subsequence of
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
  private final List<Integer> nameStopIndexes;
  private final int nameNumber;

  private GeneralPattern(
    FeedScopedId id,
    Route route,
    List<GeneralPatternStop> stops,
    List<GeneralPatternVariant> variants,
    List<Integer> nameStopIndexes,
    int nameNumber
  ) {
    this.id = id;
    this.route = route;
    this.stops = List.copyOf(stops);
    this.variants = List.copyOf(variants);
    this.nameStopIndexes = List.copyOf(nameStopIndexes);
    this.nameNumber = nameNumber;
    for (int i : this.nameStopIndexes) {
      if (i < 0 || i >= this.stops.size()) {
        throw new IllegalArgumentException("A name stop is outside the pattern: " + id);
      }
    }
  }

  /**
   * Create a general pattern. The trip counts of every stop are derived from the
   * variants, whose stop indexes refer to {@code places}. It is named after its first and last
   * stop; use {@link #withName} to make the name unique among the route's general patterns.
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
        }
        if (v.firstStopIndex() == k) {
          starting += trips;
        }
        if (v.lastStopIndex() == k) {
          ending += trips;
        }
      }
      stops.add(
        new GeneralPatternStop(
          places.get(k),
          k == 0 || k == places.size() - 1,
          reaching,
          served,
          reaching - served,
          starting,
          ending
        )
      );
    }

    return new GeneralPattern(id, route, stops, sorted, List.of(0, stops.size() - 1), 0);
  }

  /**
   * A copy with another name.
   *
   * @param stopIndexes indexes of the stops whose names make up the name, in travel order
   * @param number 0, or a number that tells the general pattern apart from others named after the
   *               same stops
   */
  public GeneralPattern withName(List<Integer> stopIndexes, int number) {
    return new GeneralPattern(id, route, stops, variants, stopIndexes, number);
  }

  public FeedScopedId id() {
    return id;
  }

  public Route route() {
    return route;
  }

  /**
   * A name that is unique among the general patterns of the route. It consists of stop names
   * only: the first and the last stop and, if needed, a stop in between that only this general
   * pattern serves, for example {@code "A - M - B"}.
   */
  public String name(Locale locale) {
    var names = nameStops()
      .stream()
      .map(s -> s.name() == null ? s.stop().getId().getId() : s.name().toString(locale))
      .toList();
    return StopSequenceNamer.format(names, nameNumber);
  }

  /** The stops whose names make up {@link #name}, in travel order. */
  public List<GeneralPatternStop> nameStops() {
    return nameStopIndexes.stream().map(stops::get).toList();
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
      .addStr("name", name(Locale.ROOT))
      .addNum("stops", stops.size())
      .addNum("variants", variants.size())
      .addNum("trips", scheduledTrips())
      .toString();
  }
}
