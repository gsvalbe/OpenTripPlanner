package org.opentripplanner.ext.generalpattern.internal.graphbuilder;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.generalpattern.internal.graphbuilder.Group.Unserved;
import org.opentripplanner.ext.generalpattern.model.GeneralPattern;
import org.opentripplanner.ext.generalpattern.model.GeneralPatternPlace;
import org.opentripplanner.ext.generalpattern.model.GeneralPatternVariant;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.site.StopLocation;

/**
 * Maps a build-time {@link Group} to the immutable {@link GeneralPattern} model.
 */
final class GeneralPatternMapper {

  private GeneralPatternMapper() {}

  static GeneralPattern map(FeedScopedId id, Route route, Group group) {
    List<Node> order = group.order();
    List<Variant> members = group.members();

    List<GeneralPatternPlace> places = new ArrayList<>(order.size());
    for (Node node : order) {
      Set<StopLocation> merged = new LinkedHashSet<>();
      for (Variant m : members) {
        if (m.contains(node)) {
          merged.addAll(m.stopsAt(m.indexOf(node)));
        }
      }
      // Prefer a stop this route actually uses: a station key's own representative can come from
      // another feed, with a name in another language.
      StopLocation stop = merged.isEmpty() ? node.key().representative() : merged.iterator().next();
      if (merged.isEmpty()) {
        merged.add(stop);
      }
      places.add(new GeneralPatternPlace(stop, node.key().station(), List.copyOf(merged)));
    }

    List<GeneralPatternVariant> variants = new ArrayList<>(members.size());
    for (Variant m : members) {
      int first = order.indexOf(m.first());
      int last = order.indexOf(m.last());
      BitSet served = new BitSet(order.size());
      BitSet passed = new BitSet(order.size());
      for (int k = first; k <= last; k++) {
        Node node = order.get(k);
        if (m.contains(node)) {
          served.set(k);
        } else if (unserved(group, m, node) == Unserved.PASS) {
          passed.set(k);
        }
      }
      variants.add(new GeneralPatternVariant(m.patterns(), served, passed, m.trips()));
    }
    return GeneralPattern.of(id, route, places, variants);
  }

  private static Unserved unserved(Group group, Variant variant, Node node) {
    Map<Node, Unserved> map = group.unserved().getOrDefault(variant, Map.of());
    return map.getOrDefault(node, Unserved.DETOUR);
  }
}
