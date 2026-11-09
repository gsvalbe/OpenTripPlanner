package org.opentripplanner.ext.generalpattern.internal.graphbuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.opentripplanner.ext.generalpattern.internal.graphbuilder.Group.Unserved;
import org.opentripplanner.ext.generalpattern.parameters.GeneralPatternParameters;

/**
 * Groups the variants of one route into general patterns.
 * <p>
 * A variant joins a group when they share enough stations and their combined stop order has no
 * cycle (which keeps the two directions apart). When the combined order is ambiguous, tied stops
 * are ordered by their position along the shape. Such a merge is kept only if the variants run
 * along the same corridor; otherwise they are branches or different roads.
 */
final class VariantMerger {

  /** Distance between sample points when testing corridor overlap, in meters. */
  private static final double SAMPLE_STEP = 500;

  /** Slack around the measure window used when classifying an unserved stop, in meters. */
  private static final double WINDOW_SLACK = 50;

  private final GeneralPatternParameters parameters;
  private final double skipRadius;
  private final double lonScale;
  private final Map<Node, double[]> xyCache = new HashMap<>();

  VariantMerger(GeneralPatternParameters parameters, double skipRadius, double lonScale) {
    this.parameters = parameters;
    this.skipRadius = skipRadius;
    this.lonScale = lonScale;
  }

  List<Group> merge(List<Variant> variants) {
    var sorted = new ArrayList<>(variants);
    sorted.sort(
      Comparator.comparingInt(Variant::trips)
        .reversed()
        .thenComparing(Comparator.comparingInt(Variant::size).reversed())
    );

    List<Group> groups = new ArrayList<>();
    for (Variant variant : sorted) {
      int required = Math.min(parameters.minSharedStations(), variant.size());
      var candidates = new ArrayList<>(groups);
      candidates.sort(Comparator.comparingInt((Group g) -> g.sharedNodes(variant)).reversed());
      boolean merged = false;
      for (Group candidate : candidates) {
        if (candidate.sharedNodes(variant) < required) {
          break;
        }
        Group result = tryMerge(candidate, variant);
        if (result != null) {
          groups.set(groups.indexOf(candidate), result);
          merged = true;
          break;
        }
      }
      if (!merged) {
        groups.add(Group.of(variant));
      }
    }
    mergeGroupsUntilStable(groups);
    groups.sort(Comparator.comparingInt(Group::trips).reversed());
    return groups;
  }

  /**
   * The greedy pass depends on the order in which variants arrive. Merge whole groups pairwise
   * until no more merges are possible.
   */
  private void mergeGroupsUntilStable(List<Group> groups) {
    boolean changed = true;
    while (changed) {
      changed = false;
      outer: for (int i = 0; i < groups.size(); i++) {
        for (int j = i + 1; j < groups.size(); j++) {
          Group a = groups.get(i);
          Group b = groups.get(j);
          if (a.sharedNodes(b) < parameters.minSharedStations()) {
            continue;
          }
          Group current = a;
          for (Variant variant : b.members()) {
            current = tryMerge(current, variant);
            if (current == null) {
              break;
            }
          }
          if (current != null) {
            groups.set(i, current);
            groups.remove(j);
            changed = true;
            break outer;
          }
        }
      }
    }
  }

  @Nullable
  Group tryMerge(Group group, Variant variant) {
    List<Variant> members = new ArrayList<>(group.members());
    members.add(variant);
    var order = mergeOrder(members);
    if (order == null) {
      return null;
    }
    var unserved = classify(members, order.nodes());
    if (order.ties() > 0 && hasDetour(unserved)) {
      Variant reference = group
        .members()
        .stream()
        .max(
          Comparator.comparingInt((Variant m) -> m.sharedNodes(variant)).thenComparingDouble(m ->
            m.line().length()
          )
        )
        .orElseThrow();
      if (reference.sharedNodes(variant) < parameters.minSharedStations()) {
        return null;
      }
      double overlap = Math.min(
        corridorOverlap(variant, reference, order.nodes()),
        corridorOverlap(reference, variant, order.nodes())
      );
      if (overlap < parameters.corridorMinOverlap()) {
        return null;
      }
    }
    return new Group(members, order.nodes(), unserved);
  }

  /**
   * Topological order of the precedence graph of all members. Tied stops are ordered by their
   * position along the longest member's shape. Returns {@code null} if the graph has a cycle.
   */
  @Nullable
  Order mergeOrder(List<Variant> members) {
    Map<Node, Set<Node>> successors = new LinkedHashMap<>();
    Map<Node, Integer> inDegree = new HashMap<>();
    Set<Node> nodes = new LinkedHashSet<>();
    for (Variant m : members) {
      nodes.addAll(m.sequence());
      for (int i = 0; i + 1 < m.size(); i++) {
        Node a = m.sequence().get(i);
        Node b = m.sequence().get(i + 1);
        if (successors.computeIfAbsent(a, k -> new LinkedHashSet<>()).add(b)) {
          inDegree.merge(b, 1, Integer::sum);
        }
      }
    }
    Variant reference = members
      .stream()
      .max(Comparator.comparingDouble(m -> m.line().length()))
      .orElseThrow();
    Map<Node, Double> measures = new HashMap<>();

    List<Node> ready = new ArrayList<>();
    for (Node n : nodes) {
      if (inDegree.getOrDefault(n, 0) == 0) {
        ready.add(n);
      }
    }
    List<Node> out = new ArrayList<>(nodes.size());
    int ties = 0;
    while (!ready.isEmpty()) {
      if (ready.size() > 1) {
        ties++;
        for (Node n : ready) {
          measures.computeIfAbsent(n, k -> {
            double[] xy = xy(k);
            return reference.line().project(xy[0], xy[1]).measure();
          });
        }
        ready.sort(Comparator.comparingDouble(measures::get));
      }
      Node n = ready.removeFirst();
      out.add(n);
      for (Node next : successors.getOrDefault(n, Set.of())) {
        int degree = inDegree.merge(next, -1, Integer::sum);
        if (degree == 0) {
          ready.add(next);
        }
      }
    }
    return out.size() == nodes.size() ? new Order(out, ties) : null;
  }

  /**
   * For every member and every stop inside its span that it does not serve: does the vehicle pass
   * the stop, or would serving it need a detour?
   */
  Map<Variant, Map<Node, Unserved>> classify(List<Variant> members, List<Node> order) {
    Map<Node, Integer> pos = positions(order);
    Map<Variant, Map<Node, Unserved>> result = new HashMap<>();
    for (Variant m : members) {
      int lo = pos.get(m.first());
      int hi = pos.get(m.last());
      Map<Node, Double> servedMeasures = new HashMap<>();
      double last = 0;
      for (Node n : m.sequence()) {
        double[] xy = xy(n);
        double measure = m.line().project(xy[0], xy[1], last, Double.POSITIVE_INFINITY).measure();
        servedMeasures.put(n, measure);
        last = measure;
      }
      Map<Node, Unserved> unserved = new HashMap<>();
      Node previous = m.first();
      for (int k = lo; k <= hi; k++) {
        Node n = order.get(k);
        if (m.contains(n)) {
          previous = n;
          continue;
        }
        Node next = nextServed(m, order, k);
        double[] xy = xy(n);
        double distance = m
          .line()
          .project(
            xy[0],
            xy[1],
            servedMeasures.get(previous) - WINDOW_SLACK,
            servedMeasures.get(next) + WINDOW_SLACK
          )
          .distance();
        unserved.put(n, distance <= skipRadius ? Unserved.PASS : Unserved.DETOUR);
      }
      result.put(m, unserved);
    }
    return result;
  }

  /**
   * Share of {@code a}'s shape within {@code corridorBuffer} of {@code b}'s shape, sampled along
   * the part of {@code a} that lies inside {@code b}'s span in the merged order. A branch tail that
   * leaves {@code b}'s corridor therefore counts against the merge; a short turn does not.
   */
  double corridorOverlap(Variant a, Variant b, List<Node> order) {
    Map<Node, Integer> pos = positions(order);
    int lo = pos.get(b.first());
    int hi = pos.get(b.last());
    double minMeasure = Double.POSITIVE_INFINITY;
    double maxMeasure = Double.NEGATIVE_INFINITY;
    int inWindow = 0;
    for (Node n : a.sequence()) {
      int p = pos.get(n);
      if (p < lo || p > hi) {
        continue;
      }
      inWindow++;
      double[] xy = xy(n);
      double measure = a.line().project(xy[0], xy[1]).measure();
      minMeasure = Math.min(minMeasure, measure);
      maxMeasure = Math.max(maxMeasure, measure);
    }
    if (inWindow < 2 || maxMeasure - minMeasure < SAMPLE_STEP) {
      return 1.0;
    }
    int samples = 0;
    int close = 0;
    for (double m = minMeasure; m <= maxMeasure; m += SAMPLE_STEP) {
      double[] p = a.line().pointAt(m);
      samples++;
      if (b.line().project(p[0], p[1]).distance() <= parameters.corridorBuffer()) {
        close++;
      }
    }
    return (double) close / samples;
  }

  private static boolean hasDetour(Map<Variant, Map<Node, Unserved>> unserved) {
    return unserved
      .values()
      .stream()
      .anyMatch(m -> m.containsValue(Unserved.DETOUR));
  }

  private static Node nextServed(Variant m, List<Node> order, int from) {
    for (int k = from; k < order.size(); k++) {
      if (m.contains(order.get(k))) {
        return order.get(k);
      }
    }
    throw new IllegalStateException("A node inside a variant's span has a served node after it");
  }

  private static Map<Node, Integer> positions(List<Node> order) {
    Map<Node, Integer> pos = new HashMap<>();
    for (int i = 0; i < order.size(); i++) {
      pos.put(order.get(i), i);
    }
    return pos;
  }

  private double[] xy(Node n) {
    return xyCache.computeIfAbsent(n, k -> new double[] {
      ProjectedLine.projectX(k.key().coordinate().longitude(), lonScale),
      ProjectedLine.projectY(k.key().coordinate().latitude()),
    });
  }

  /** A merged stop order and the number of times the topological sort had to break a tie. */
  record Order(List<Node> nodes, int ties) {}
}
