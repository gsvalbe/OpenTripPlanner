package org.opentripplanner.ext.generalpattern.internal.graphbuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;

/**
 * Build-time variant: all trip patterns of a route that have the same station sequence. The
 * shape used for geometry tests is the one of the pattern with the most trips.
 */
final class Variant {

  private final List<Node> sequence;
  private final Map<Node, Integer> index = new HashMap<>();
  private final List<TripPattern> patterns = new ArrayList<>();
  private final List<Set<StopLocation>> stopsAt = new ArrayList<>();
  private int trips = 0;
  private TripPattern heaviest = null;
  private int heaviestTrips = -1;
  private ProjectedLine line;

  Variant(List<Node> sequence) {
    this.sequence = List.copyOf(sequence);
    for (int i = 0; i < this.sequence.size(); i++) {
      index.put(this.sequence.get(i), i);
      stopsAt.add(new LinkedHashSet<>());
    }
  }

  /**
   * Add a pattern whose stops map, one to one, onto this variant's station sequence.
   */
  void add(TripPattern pattern, int patternTrips) {
    List<StopLocation> stops = pattern.getStops();
    if (stops.size() != sequence.size()) {
      throw new IllegalArgumentException("Pattern does not match the variant: " + pattern.getId());
    }
    patterns.add(pattern);
    for (int i = 0; i < stops.size(); i++) {
      stopsAt.get(i).add(stops.get(i));
    }
    trips += patternTrips;
    if (patternTrips > heaviestTrips) {
      heaviest = pattern;
      heaviestTrips = patternTrips;
    }
  }

  /** Project the shape of the heaviest pattern. Call once, after all patterns are added. */
  void finish(double lonScale) {
    LineString geometry = heaviest.getGeometry();
    if (geometry != null && geometry.getNumPoints() >= 2) {
      line = ProjectedLine.of(geometry, lonScale);
    } else {
      List<WgsCoordinate> points = new ArrayList<>();
      heaviest.getStops().forEach(s -> points.add(s.getCoordinate()));
      line = ProjectedLine.of(points, lonScale);
    }
  }

  List<Node> sequence() {
    return sequence;
  }

  int size() {
    return sequence.size();
  }

  boolean contains(Node node) {
    return index.containsKey(node);
  }

  int indexOf(Node node) {
    return index.get(node);
  }

  Node first() {
    return sequence.getFirst();
  }

  Node last() {
    return sequence.getLast();
  }

  List<TripPattern> patterns() {
    return patterns;
  }

  /** The stops of all patterns at the given position in the sequence. */
  Set<StopLocation> stopsAt(int position) {
    return stopsAt.get(position);
  }

  int trips() {
    return trips;
  }

  ProjectedLine line() {
    return line;
  }

  @Override
  public String toString() {
    return sequence.getFirst() + " -> " + sequence.getLast() + " (" + trips + " trips)";
  }
}
