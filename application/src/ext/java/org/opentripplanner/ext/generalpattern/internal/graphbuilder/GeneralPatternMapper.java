package org.opentripplanner.ext.generalpattern.internal.graphbuilder;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.opentripplanner.core.model.id.FeedScopedId;
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
      BitSet served = new BitSet(order.size());
      for (int k = 0; k < order.size(); k++) {
        if (m.contains(order.get(k))) {
          served.set(k);
        }
      }
      variants.add(new GeneralPatternVariant(m.patterns(), served, m.trips()));
    }
    return GeneralPattern.of(id, route, places, variants);
  }
}
