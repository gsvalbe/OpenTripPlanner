package org.opentripplanner.apis.gtfs.datafetchers;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.opentripplanner.apis.gtfs.generated.GraphQLDataFetchers;
import org.opentripplanner.ext.generalpattern.model.GeneralPattern;
import org.opentripplanner.ext.generalpattern.model.GeneralPatternStop;
import org.opentripplanner.framework.graphql.GraphQLUtils;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;

public class GeneralPatternImpl implements GraphQLDataFetchers.GraphQLGeneralPattern {

  @Override
  public DataFetcher<String> gtfsId() {
    return environment -> getSource(environment).id().toString();
  }

  @Override
  public DataFetcher<String> name() {
    return environment -> getSource(environment).name(GraphQLUtils.getLocale(environment));
  }

  @Override
  public DataFetcher<Iterable<TripPattern>> patterns() {
    return environment -> getSource(environment).tripPatterns();
  }

  @Override
  public DataFetcher<Route> route() {
    return environment -> getSource(environment).route();
  }

  @Override
  public DataFetcher<Iterable<GeneralPatternStop>> stops() {
    return environment -> getSource(environment).stops();
  }

  private GeneralPattern getSource(DataFetchingEnvironment environment) {
    return environment.getSource();
  }
}
