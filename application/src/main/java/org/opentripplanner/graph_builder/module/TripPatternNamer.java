package org.opentripplanner.graph_builder.module;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.opentripplanner.graph_builder.model.GraphBuilderModule;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.StopSequenceNamer;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.service.TransitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TripPatternNamer implements GraphBuilderModule {

  private static final Logger LOG = LoggerFactory.getLogger(TripPatternNamer.class);
  private final TransitRepository transitRepository;

  @Inject
  public TripPatternNamer(TransitRepository transitRepository) {
    this.transitRepository = transitRepository;
  }

  @Override
  public void buildGraph() {
    /* Generate unique human-readable names for all the TableTripPatterns. */
    generateUniqueNames(transitRepository.getAllTripPatterns());
  }

  /**
   * Creates human-readable names for the trip patterns that do not have one yet, unique among the
   * patterns of a route.
   * <p>
   * A GTFS route name is meant for general customer information, but riders sometimes need to
   * know where a particular trip actually goes. Each pattern is therefore named after its stops,
   * see {@link StopSequenceNamer}: first and last stop ({@code "A - B"}), plus a stop near the
   * middle that only this pattern serves when several patterns have the same ends
   * ({@code "A - M - B"}). Patterns that cannot be told apart by their stop names get a number.
   * Names contain stop names only, so they work in every language.
   * <p>
   * A route with a single pattern uses the route name, and a name given in the input data (for
   * example in NeTEx or a GTFS extension) is kept. Names are not stable across graph builds.
   */
  public static void generateUniqueNames(Collection<TripPattern> tableTripPatterns) {
    LOG.info("Generating unique names for stop patterns on each route.");

    /* Group TripPatterns by Route */
    Multimap<Route, TripPattern> patternsByRoute = ArrayListMultimap.create();
    for (TripPattern ttp : tableTripPatterns) {
      patternsByRoute.put(ttp.getRoute(), ttp);
    }

    /* Iterate over all routes, giving the patterns within each route unique names. */
    for (Route route : patternsByRoute.keySet()) {
      Collection<TripPattern> routeTripPatterns = patternsByRoute.get(route);

      // Only generate name for patterns with at least one missing name
      if (routeTripPatterns.stream().allMatch(tripPattern -> tripPattern.getName() != null)) {
        continue;
      }

      /* Simplest case: there's only one route variant, so we'll just give it the route's name. */
      if (routeTripPatterns.size() == 1) {
        routeTripPatterns.iterator().next().initName(route.getName());
        continue;
      }

      List<TripPattern> toName = new ArrayList<>();
      for (TripPattern pattern : routeTripPatterns) {
        if (pattern.getName() != null) {
          continue;
        }
        String patternName = pattern.getTripPatternName();
        if (patternName != null && !patternName.isBlank()) {
          pattern.initName(patternName);
        } else {
          toName.add(pattern);
        }
      }
      toName.sort(Comparator.comparing(TripPattern::getId));

      List<List<String>> sequences = toName
        .stream()
        .map(p -> p.getStops().stream().map(TripPatternNamer::stopName).toList())
        .toList();
      List<StopSequenceNamer.Name> names = StopSequenceNamer.name(sequences);
      for (int i = 0; i < toName.size(); i++) {
        List<String> sequence = sequences.get(i);
        toName.get(i).initName(names.get(i).format(sequence::get));
      }
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Done generating unique names for stop patterns on each route.");
      for (Route route : patternsByRoute.keySet()) {
        Collection<TripPattern> routeTripPatterns = patternsByRoute.get(route);
        LOG.debug("Named {} patterns in route {}", routeTripPatterns.size(), route.getName());
        for (TripPattern pattern : routeTripPatterns) {
          LOG.debug("    {} ({} stops)", pattern.getName(), pattern.numberOfStops());
        }
      }
    }
  }

  private static String stopName(StopLocation stop) {
    return stop.getName() == null ? stop.getId().getId() : stop.getName().toString();
  }
}
