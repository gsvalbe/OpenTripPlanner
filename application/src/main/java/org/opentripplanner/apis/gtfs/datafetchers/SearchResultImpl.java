package org.opentripplanner.apis.gtfs.datafetchers;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.util.Locale;
import org.opentripplanner.apis.gtfs.generated.GraphQLDataFetchers;
import org.opentripplanner.apis.gtfs.model.SearchResultModel;
import org.opentripplanner.framework.graphql.GraphQLUtils;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.site.StopLocationsGroup;

public class SearchResultImpl implements GraphQLDataFetchers.GraphQLSearchResult {

  @Override
  public DataFetcher<Double> distance() {
    return environment -> getSource(environment).distance();
  }

  @Override
  public DataFetcher<Object> item() {
    return environment -> getSource(environment).item();
  }

  @Override
  public DataFetcher<String> name() {
    return environment ->
      nameOf(getSource(environment).item(), GraphQLUtils.getLocale(environment));
  }

  @Override
  public DataFetcher<Double> score() {
    return environment -> getSource(environment).score();
  }

  /**
   * The same label the entity's own type would give, so that {@code name} and, say, {@code Stop.name}
   * never disagree.
   */
  private static String nameOf(Object item, Locale locale) {
    return switch (item) {
      case StopLocation stop -> stop.getName().toString(locale);
      case StopLocationsGroup station -> station.getName().toString(locale);
      case Route route -> route.getName(locale);
      // a pattern added by a real-time update is never named, since only the graph build names
      // patterns; its route is then the best label available, and is also what the namer itself
      // falls back to for a route with a single pattern
      case TripPattern pattern -> pattern.getName() != null
        ? pattern.getName()
        : pattern.getRoute().getName(locale);
      default -> throw new IllegalStateException(
        "Unhandled search result item type: " + item.getClass()
      );
    };
  }

  private SearchResultModel getSource(DataFetchingEnvironment environment) {
    return environment.getSource();
  }
}
