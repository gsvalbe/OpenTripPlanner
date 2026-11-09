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
import org.opentripplanner.ext.generalpattern.parameters.GeneralPatternParameters;

/**
 * Groups the variants of one route into general patterns.
 * <p>
 * Variants are merged only when they differ by shortened trips, extended trips and skipped
 * stops. A variant joins a group when they share enough stations, their combined stop order has
 * no cycle (which keeps the two directions apart), and every member passes, within the skip
 * radius, each stop of the combined order that lies inside its span but that it does not serve.
 * A variant that would have to leave its road to serve such a stop, such as a diversion through a
 * village or a branch, stays in a general pattern of its own.
 */
final class VariantMerger {

  /** Slack around the measure window used when testing an unserved stop, in meters. */
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
    for (Variant m : members) {
      if (!passesAllUnserved(m, order)) {
        return null;
      }
    }
    return new Group(members, order);
  }

  /**
   * Topological order of the precedence graph of all members. Tied stops are ordered by their
   * position along the longest member's shape. Returns {@code null} if the graph has a cycle.
   */
  @Nullable
  List<Node> mergeOrder(List<Variant> members) {
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
    while (!ready.isEmpty()) {
      if (ready.size() > 1) {
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
    return out.size() == nodes.size() ? out : null;
  }

  /**
   * Whether the variant passes, within the skip radius, every stop between its first and last
   * stop in the merged order that it does not serve. Each such stop is tested against the part of
   * the variant's shape between the served stops around it.
   */
  boolean passesAllUnserved(Variant m, List<Node> order) {
    int lo = order.indexOf(m.first());
    int hi = order.indexOf(m.last());
    Map<Node, Double> servedMeasures = new HashMap<>();
    double last = 0;
    for (Node n : m.sequence()) {
      double[] xy = xy(n);
      double measure = m.line().project(xy[0], xy[1], last, Double.POSITIVE_INFINITY).measure();
      servedMeasures.put(n, measure);
      last = measure;
    }
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
      if (distance > skipRadius) {
        return false;
      }
    }
    return true;
  }

  private static Node nextServed(Variant m, List<Node> order, int from) {
    for (int k = from; k < order.size(); k++) {
      if (m.contains(order.get(k))) {
        return order.get(k);
      }
    }
    throw new IllegalStateException("A node inside a variant's span has a served node after it");
  }

  private double[] xy(Node n) {
    return xyCache.computeIfAbsent(n, k -> new double[] {
      ProjectedLine.projectX(k.key().coordinate().longitude(), lonScale),
      ProjectedLine.projectY(k.key().coordinate().latitude()),
    });
  }
}
