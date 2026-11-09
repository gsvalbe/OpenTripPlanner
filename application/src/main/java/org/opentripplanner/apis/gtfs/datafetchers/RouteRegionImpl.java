package org.opentripplanner.apis.gtfs.datafetchers;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.opentripplanner.apis.gtfs.generated.GraphQLDataFetchers;
import org.opentripplanner.transit.model.network.WgsBoundingBox;
import org.opentripplanner.transit.model.network.RouteRegion;

public class RouteRegionImpl implements GraphQLDataFetchers.GraphQLRouteRegion {

  @Override
  public DataFetcher<WgsBoundingBox> bounds() {
    return environment -> getSource(environment).bounds().orElse(null);
  }

  @Override
  public DataFetcher<String> name() {
    return environment -> getSource(environment).name();
  }

  private RouteRegion getSource(DataFetchingEnvironment environment) {
    return environment.getSource();
  }
}
