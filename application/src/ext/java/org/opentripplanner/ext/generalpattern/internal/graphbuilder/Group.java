package org.opentripplanner.ext.generalpattern.internal.graphbuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A set of variants that form one general pattern, with their merged stop order. Every stop
 * between a variant's first and last stop that the variant does not serve is a stop it passes
 * without stopping.
 */
record Group(List<Variant> members, List<Node> order) {
  Group {
    members = List.copyOf(members);
    order = List.copyOf(order);
  }

  static Group of(Variant variant) {
    return new Group(List.of(variant), variant.sequence());
  }

  int sharedNodes(Variant variant) {
    Set<Node> mine = orderSet();
    int n = 0;
    for (Node node : variant.sequence()) {
      if (mine.contains(node)) {
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
