package org.opentripplanner.apis.gtfs.datafetchers;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.opentripplanner.apis.gtfs.generated.GraphQLDataFetchers;
import org.opentripplanner.transit.model.network.WgsBoundingBox;

public class BoundingBoxImpl implements GraphQLDataFetchers.GraphQLBoundingBox {

  @Override
  public DataFetcher<Double> maxLat() {
    return environment -> getSource(environment).maxLat();
  }

  @Override
  public DataFetcher<Double> maxLon() {
    return environment -> getSource(environment).maxLon();
  }

  @Override
  public DataFetcher<Double> minLat() {
    return environment -> getSource(environment).minLat();
  }

  @Override
  public DataFetcher<Double> minLon() {
    return environment -> getSource(environment).minLon();
  }

  private WgsBoundingBox getSource(DataFetchingEnvironment environment) {
    return environment.getSource();
  }
}
