package org.opentripplanner.ext.geocoder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.generalpattern.GeneralPatternRepository;
import org.opentripplanner.ext.generalpattern.model.GeneralPattern;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.utils.collection.ListUtils;

/**
 * Maps routes and trip patterns from the transit model into the intermediate form that
 * {@link LuceneIndex} indexes.
 */
class TransitDocumentMapper {

  private final TransitService transitService;

  private final GeneralPatternRepository generalPatternRepository;

  TransitDocumentMapper(
    TransitService transitService,
    @Nullable GeneralPatternRepository generalPatternRepository
  ) {
    this.transitService = transitService;
    this.generalPatternRepository = generalPatternRepository;
  }

  /**
   * Builds one document per route. A route is searchable by its long name, its region name and its
   * short name (the route number), and is located at every stop it serves, so that a
   * distance-ranked search finds a long line whenever any part of it is nearby.
   */
  List<LuceneTransitDocument> generateRouteDocuments() {
    return transitService
      .listRoutes()
      .stream()
      .map(route ->
        new LuceneTransitDocument(
          route.getId().toString(),
          searchableRouteNames(route),
          ListUtils.ofNullable(route.getShortName()),
          route.getMode(),
          routeCoordinates(route)
        )
      )
      .toList();
  }

  /**
   * Everything a route can be found by in the name field: its own names plus the region it runs in,
   * so that "riga 31" finds route 31 in the Riga region rather than route 31 anywhere.
   */
  private static List<String> searchableRouteNames(Route route) {
    return ListUtils.combine(routeNames(route), ListUtils.ofNullable(route.getRegionName()));
  }

  /**
   * Builds documents for the trip patterns whose name says something the route's own name does not.
   * <p>
   * Most patterns are named after their route, so indexing every one of them would return a dozen
   * near-identical rows for a query like "5" and bury everything else. Patterns are therefore
   * deduplicated by their searchable text within a route, and dropped entirely when that text adds
   * nothing over the route.
   * <p>
   * Trip patterns merged into a general pattern are indexed once, as that general pattern, under its
   * unique readable name. The document id is that of the general pattern's main trip pattern, so
   * the result still resolves to a pattern.
   */
  List<LuceneTransitDocument> generatePatternDocuments() {
    return transitService
      .listRoutes()
      .stream()
      .flatMap(route -> patternDocumentsFor(route).stream())
      .toList();
  }

  private List<LuceneTransitDocument> patternDocumentsFor(Route route) {
    var routeNames = routeNames(route)
      .stream()
      .map(name -> name.toLowerCase())
      .collect(Collectors.toSet());

    var covered = new HashSet<FeedScopedId>();
    var generalDocs = generalPatternDocumentsFor(route, routeNames, covered);

    var tripPatternDocs = transitService
      .findPatterns(route)
      .stream()
      .filter(pattern -> !covered.contains(pattern.getId()))
      .map(pattern -> new PatternWithNames(pattern, distinctiveNames(pattern, routeNames)))
      .filter(p -> !p.names().isEmpty())
      // one document per distinct set of names: patterns that read the same are indistinguishable
      // in a search result, so the first of them stands in for the rest
      .collect(Collectors.toMap(PatternWithNames::names, p -> p, (first, duplicate) -> first))
      .values()
      .stream()
      .map(p ->
        new LuceneTransitDocument(
          p.pattern().getId().toString(),
          p.names(),
          ListUtils.ofNullable(route.getShortName()),
          p.pattern().getMode(),
          coordinatesOf(p.pattern().getStops())
        )
      )
      .toList();
    return ListUtils.combine(generalDocs, tripPatternDocs);
  }

  /**
   * One document per general pattern of the route. Every trip pattern merged into one is added to
   * {@code covered}, so that it is not indexed on its own.
   */
  private List<LuceneTransitDocument> generalPatternDocumentsFor(
    Route route,
    Set<String> routeNames,
    Set<FeedScopedId> covered
  ) {
    if (generalPatternRepository == null) {
      return List.of();
    }
    var docs = new ArrayList<LuceneTransitDocument>();
    for (GeneralPattern gp : generalPatternRepository.findByRoute(route.getId())) {
      if (gp.tripPatterns().isEmpty()) {
        continue;
      }
      var main = gp.tripPatterns().getFirst();
      gp.tripPatterns().forEach(p -> covered.add(p.getId()));

      var names = new LinkedHashSet<String>();
      names.add(gp.name(Locale.ROOT));
      for (var pattern : gp.tripPatterns()) {
        names.add(pattern.getTripPatternName());
      }
      names.remove(null);
      names.removeIf(name -> name.isBlank() || routeNames.contains(name.toLowerCase()));
      if (names.isEmpty()) {
        continue;
      }
      docs.add(
        new LuceneTransitDocument(
          main.getId().toString(),
          names,
          ListUtils.ofNullable(route.getShortName()),
          main.getMode(),
          coordinatesOf(
            gp
              .stops()
              .stream()
              .map(s -> s.stop())
              .toList()
          )
        )
      );
    }
    return docs;
  }

  /**
   * The pattern's names that are worth indexing: the custom trip pattern name from the feed and the
   * generated pattern name, minus anything the route is already findable by.
   */
  private static Set<String> distinctiveNames(TripPattern pattern, Set<String> routeNames) {
    var names = new LinkedHashSet<String>();
    names.add(pattern.getTripPatternName());
    names.add(pattern.getName());
    names.remove(null);
    names.removeIf(name -> name.isBlank() || routeNames.contains(name.toLowerCase()));
    return names;
  }

  private static List<String> routeNames(Route route) {
    return ListUtils.combine(
      ListUtils.ofNullable(route.getLongName()).stream().map(Objects::toString).toList(),
      ListUtils.ofNullable(route.getShortName())
    );
  }

  private List<WgsCoordinate> routeCoordinates(Route route) {
    var stops = transitService
      .findPatterns(route)
      .stream()
      .flatMap(pattern -> pattern.getStops().stream())
      .toList();
    return coordinatesOf(stops);
  }

  /**
   * Reduces a list of stops to their coordinates, collapsing ones within roughly 100 m of each
   * other. A route can be indexed at hundreds of stops otherwise, which costs index size without
   * making the distance ranking any more accurate at the scale a search box cares about.
   */
  private static List<WgsCoordinate> coordinatesOf(Collection<StopLocation> stops) {
    return stops
      .stream()
      .map(StopLocation::getCoordinate)
      .filter(Objects::nonNull)
      .map(WgsCoordinate::roundToApproximate100m)
      .distinct()
      .toList();
  }

  private record PatternWithNames(TripPattern pattern, Set<String> names) {}
}
