package org.opentripplanner.apis.gtfs.datafetchers;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.opentripplanner.apis.gtfs.generated.GraphQLDataFetchers;
import org.opentripplanner.apis.gtfs.model.TripFare;
import org.opentripplanner.model.fare.FareOffer;

public class TripFareImpl implements GraphQLDataFetchers.GraphQLTripFare {

  @Override
  public DataFetcher<Object> fromStop() {
    return environment -> getSource(environment).fromStop();
  }

  @Override
  public DataFetcher<Object> toStop() {
    return environment -> getSource(environment).toStop();
  }

  @Override
  public DataFetcher<Iterable<FareOffer>> fareProducts() {
    return environment -> getSource(environment).fareProducts();
  }

  private TripFare getSource(DataFetchingEnvironment environment) {
    return environment.getSource();
  }
}
