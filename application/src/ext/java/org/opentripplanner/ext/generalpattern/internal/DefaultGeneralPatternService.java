package org.opentripplanner.ext.generalpattern.internal;

import java.util.List;
import java.util.Optional;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.generalpattern.GeneralPatternRepository;
import org.opentripplanner.ext.generalpattern.GeneralPatternService;
import org.opentripplanner.ext.generalpattern.model.GeneralPattern;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;

public class DefaultGeneralPatternService implements GeneralPatternService {

  private final GeneralPatternRepository repository;

  public DefaultGeneralPatternService(GeneralPatternRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<GeneralPattern> findById(FeedScopedId id) {
    return repository.findById(id);
  }

  @Override
  public List<GeneralPattern> findByRoute(Route route) {
    return repository.findByRoute(route.getId());
  }

  @Override
  public Optional<GeneralPattern> findByTripPattern(TripPattern tripPattern) {
    TripPattern scheduled = tripPattern.isModified()
      ? tripPattern.getOriginalTripPattern()
      : tripPattern;
    return repository.findByTripPattern(scheduled.getId());
  }
}
