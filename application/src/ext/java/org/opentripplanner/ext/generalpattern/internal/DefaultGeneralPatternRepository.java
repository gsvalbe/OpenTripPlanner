package org.opentripplanner.ext.generalpattern.internal;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.generalpattern.GeneralPatternRepository;
import org.opentripplanner.ext.generalpattern.model.GeneralPattern;
import org.opentripplanner.ext.generalpattern.model.GeneralPatternVariant;
import org.opentripplanner.transit.model.network.TripPattern;

@Singleton
public class DefaultGeneralPatternRepository implements GeneralPatternRepository {

  private final Map<FeedScopedId, GeneralPattern> byId = new LinkedHashMap<>();
  private final Map<FeedScopedId, List<GeneralPattern>> byRoute = new HashMap<>();
  private final Map<FeedScopedId, GeneralPattern> byTripPattern = new HashMap<>();

  @Inject
  public DefaultGeneralPatternRepository() {}

  @Override
  public void addAll(Collection<GeneralPattern> generalPatterns) {
    for (var gp : generalPatterns) {
      if (byId.putIfAbsent(gp.id(), gp) != null) {
        throw new IllegalArgumentException("Duplicate general pattern id: " + gp.id());
      }
      byRoute.computeIfAbsent(gp.route().getId(), id -> new ArrayList<>()).add(gp);
      for (GeneralPatternVariant variant : gp.variants()) {
        for (TripPattern pattern : variant.patterns()) {
          byTripPattern.put(pattern.getId(), gp);
        }
      }
    }
  }

  @Override
  public Collection<GeneralPattern> listAll() {
    return Collections.unmodifiableCollection(byId.values());
  }

  @Override
  public Optional<GeneralPattern> findById(FeedScopedId id) {
    return Optional.ofNullable(byId.get(id));
  }

  @Override
  public List<GeneralPattern> findByRoute(FeedScopedId routeId) {
    return List.copyOf(byRoute.getOrDefault(routeId, List.of()));
  }

  @Override
  public Optional<GeneralPattern> findByTripPattern(FeedScopedId tripPatternId) {
    return Optional.ofNullable(byTripPattern.get(tripPatternId));
  }
}
