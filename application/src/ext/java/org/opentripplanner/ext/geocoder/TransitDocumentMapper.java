package org.opentripplanner.ext.geocoder;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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

  TransitDocumentMapper(TransitService transitService) {
    this.transitService = transitService;
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

    return transitService
      .findPatterns(route)
      .stream()
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
