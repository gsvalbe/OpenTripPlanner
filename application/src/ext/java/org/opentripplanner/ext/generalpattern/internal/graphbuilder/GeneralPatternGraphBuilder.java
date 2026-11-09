package org.opentripplanner.ext.generalpattern.internal.graphbuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.generalpattern.GeneralPatternRepository;
import org.opentripplanner.ext.generalpattern.model.GeneralPattern;
import org.opentripplanner.ext.generalpattern.parameters.GeneralPatternParameters;
import org.opentripplanner.graph_builder.model.GraphBuilderModule;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.StopSequenceNamer;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.service.TransitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the general patterns of every included route from the scheduled trip patterns, and
 * stores them in the {@link GeneralPatternRepository}. Must run after the trip patterns are final.
 */
public class GeneralPatternGraphBuilder implements GraphBuilderModule {

  private static final Logger LOG = LoggerFactory.getLogger(GeneralPatternGraphBuilder.class);
  private static final int MOST_COMPLEX_LOGGED = 10;

  private final GeneralPatternParameters parameters;
  private final TransitRepository transitRepository;
  private final GeneralPatternRepository repository;

  public GeneralPatternGraphBuilder(
    GeneralPatternParameters parameters,
    TransitRepository transitRepository,
    GeneralPatternRepository repository
  ) {
    this.parameters = parameters;
    this.transitRepository = transitRepository;
    this.repository = repository;
  }

  @Override
  public void buildGraph() {
    Map<Route, List<TripPattern>> patternsByRoute = new LinkedHashMap<>();
    transitRepository
      .getAllTripPatterns()
      .stream()
      .filter(p -> parameters.includes(p.getRoute()))
      .sorted(Comparator.comparing(TripPattern::getId))
      .forEach(p -> patternsByRoute.computeIfAbsent(p.getRoute(), r -> new ArrayList<>()).add(p));

    Set<StopLocation> stops = new HashSet<>();
    patternsByRoute.values().forEach(list -> list.forEach(p -> stops.addAll(p.getStops())));
    var stationKeys = StationKeyResolver.resolve(stops, parameters.stationFallbackRadius());

    var stats = new EnumMap<TransitMode, int[]>(TransitMode.class);
    List<GeneralPattern> result = new ArrayList<>();
    for (var entry : patternsByRoute.entrySet()) {
      Route route = entry.getKey();
      List<GeneralPattern> generalPatterns = build(route, entry.getValue(), stationKeys);
      result.addAll(generalPatterns);
      int[] s = stats.computeIfAbsent(route.getMode(), m -> new int[3]);
      s[0]++;
      s[1] += entry.getValue().size();
      s[2] += generalPatterns.size();
    }
    repository.addAll(result);

    stats.forEach((mode, s) ->
      LOG.info(
        "General patterns for {}: {} routes, {} trip patterns -> {} general patterns",
        mode,
        s[0],
        s[1],
        s[2]
      )
    );
    logMostComplex(result);
  }

  /** Log the general patterns with the most variants, to help tune the parameters. */
  private static void logMostComplex(List<GeneralPattern> generalPatterns) {
    generalPatterns
      .stream()
      .sorted(Comparator.comparingInt((GeneralPattern gp) -> gp.variants().size()).reversed())
      .limit(MOST_COMPLEX_LOGGED)
      .filter(gp -> gp.variants().size() > 1)
      .forEach(gp ->
        LOG.info(
          "General pattern {} ({}): {} variants, {} stops",
          gp.id(),
          gp.name(Locale.ROOT),
          gp.variants().size(),
          gp.stops().size()
        )
      );
  }

  List<GeneralPattern> build(
    Route route,
    List<TripPattern> patterns,
    Map<StopLocation, StationKey> stationKeys
  ) {
    Map<List<Node>, Variant> variants = new LinkedHashMap<>();
    for (TripPattern pattern : patterns) {
      int trips = (int) pattern.scheduledTripsAsStream().count();
      if (trips == 0 || pattern.numberOfStops() < 2) {
        continue;
      }
      List<Node> sequence = toSequence(pattern, stationKeys);
      variants.computeIfAbsent(sequence, Variant::new).add(pattern, trips);
    }
    if (variants.isEmpty()) {
      return List.of();
    }
    double lonScale = ProjectedLine.lonScale(
      patterns.getFirst().getStops().getFirst().getCoordinate().latitude()
    );
    variants.values().forEach(v -> v.finish(lonScale));

    var merger = new VariantMerger(parameters, parameters.skipRadius(route.getMode()), lonScale);
    List<Group> groups = merger.merge(new ArrayList<>(variants.values()));

    Map<String, Integer> idCount = new HashMap<>();
    List<GeneralPattern> result = new ArrayList<>(groups.size());
    for (Group group : groups) {
      result.add(GeneralPatternMapper.map(id(route, group, idCount), route, group));
    }
    return withUniqueNames(result);
  }

  /** Name the general patterns of one route so that no two get the same name. */
  private static List<GeneralPattern> withUniqueNames(List<GeneralPattern> generalPatterns) {
    List<List<String>> sequences = generalPatterns
      .stream()
      .map(gp ->
        gp
          .stops()
          .stream()
          .map(s -> s.name() == null ? s.stop().getId().getId() : s.name().toString())
          .toList()
      )
      .toList();
    List<StopSequenceNamer.Name> names = StopSequenceNamer.name(sequences);
    List<GeneralPattern> result = new ArrayList<>(generalPatterns.size());
    for (int i = 0; i < generalPatterns.size(); i++) {
      var name = names.get(i);
      result.add(generalPatterns.get(i).withName(name.stopIndexes(), name.number()));
    }
    return result;
  }

  private static List<Node> toSequence(
    TripPattern pattern,
    Map<StopLocation, StationKey> stationKeys
  ) {
    Map<StationKey, Integer> occurrences = new HashMap<>();
    List<Node> sequence = new ArrayList<>(pattern.numberOfStops());
    for (StopLocation stop : pattern.getStops()) {
      StationKey key = stationKeys.get(stop);
      int occurrence = occurrences.merge(key, 1, Integer::sum) - 1;
      sequence.add(new Node(key, occurrence));
    }
    return sequence;
  }

  /**
   * A readable id built from the route and the first and last station, e.g.
   * {@code feed:R1~stationA~stationB}. A second general pattern with the same ends gets a suffix.
   */
  private static FeedScopedId id(Route route, Group group, Map<String, Integer> idCount) {
    String base =
      route.getId().getId() +
      "~" +
      group.order().getFirst().key().id().getId() +
      "~" +
      group.order().getLast().key().id().getId();
    int n = idCount.merge(base, 1, Integer::sum);
    return new FeedScopedId(route.getId().getFeedId(), n == 1 ? base : base + "~" + n);
  }
}
