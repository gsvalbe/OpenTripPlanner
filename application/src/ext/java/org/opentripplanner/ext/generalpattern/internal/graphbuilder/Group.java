package org.opentripplanner.ext.generalpattern.internal.graphbuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A set of variants that form one general pattern, with their merged stop order and, for every
 * variant, how it handles the stops in its span that it does not serve.
 */
record Group(List<Variant> members, List<Node> order, Map<Variant, Map<Node, Unserved>> unserved) {
  enum Unserved {
    /** The vehicle passes close to the stop without stopping. */
    PASS,
    /** The stop is off the variant's route: other variants make a detour to serve it. */
    DETOUR,
  }

  Group {
    members = List.copyOf(members);
    order = List.copyOf(order);
    unserved = Map.copyOf(unserved);
  }

  static Group of(Variant variant) {
    return new Group(List.of(variant), variant.sequence(), Map.of());
  }

  int sharedNodes(Variant variant) {
    int n = 0;
    for (Node node : variant.sequence()) {
      if (orderSet().contains(node)) {
        n++;
      }
    }
    return n;
  }

  int sharedNodes(Group other) {
    Set<Node> mine = orderSet();
    int n = 0;
    for (Node node : other.order) {
      if (mine.contains(node)) {
        n++;
      }
    }
    return n;
  }

  int trips() {
    return members.stream().mapToInt(Variant::trips).sum();
  }

  private Set<Node> orderSet() {
    return new HashSet<>(order);
  }
}
