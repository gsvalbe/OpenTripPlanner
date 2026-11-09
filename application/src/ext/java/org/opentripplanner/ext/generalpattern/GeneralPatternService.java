package org.opentripplanner.ext.generalpattern;

import java.util.List;
import java.util.Optional;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.generalpattern.model.GeneralPattern;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;

/**
 * Read access to general patterns, for the APIs. When the {@code GeneralPatterns} feature is off,
 * every lookup returns nothing.
 */
public interface GeneralPatternService {
  Optional<GeneralPattern> findById(FeedScopedId id);

  /** The general patterns of a route, most trips first. */
  List<GeneralPattern> findByRoute(Route route);

  /**
   * The general pattern a trip pattern belongs to. A pattern modified by real-time updates
   * resolves through its scheduled pattern; a pattern added by real-time has none.
   */
  Optional<GeneralPattern> findByTripPattern(TripPattern tripPattern);
}
