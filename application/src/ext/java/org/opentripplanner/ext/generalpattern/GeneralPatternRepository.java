package org.opentripplanner.ext.generalpattern;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.generalpattern.model.GeneralPattern;

/**
 * Holds the general patterns built during graph build. It is serialized with the graph and is
 * read-only after the build.
 */
public interface GeneralPatternRepository extends Serializable {
  /** Add general patterns. Used only by the graph build. */
  void addAll(Collection<GeneralPattern> generalPatterns);

  Collection<GeneralPattern> listAll();

  Optional<GeneralPattern> findById(FeedScopedId id);

  /** The general patterns of a route, most trips first. Empty if the route has none. */
  List<GeneralPattern> findByRoute(FeedScopedId routeId);

  /** The general pattern a trip pattern was merged into. */
  Optional<GeneralPattern> findByTripPattern(FeedScopedId tripPatternId);
}
