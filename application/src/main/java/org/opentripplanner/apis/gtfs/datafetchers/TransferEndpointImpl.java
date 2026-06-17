package org.opentripplanner.apis.gtfs.datafetchers;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.opentripplanner.apis.gtfs.generated.GraphQLDataFetchers;
import org.opentripplanner.apis.gtfs.model.TransferEndpoint;
import org.opentripplanner.transit.model.timetable.Trip;

public class TransferEndpointImpl implements GraphQLDataFetchers.GraphQLTransferEndpoint {

  @Override
  public DataFetcher<Object> stop() {
    return environment -> getSource(environment).stop();
  }

  @Override
  public DataFetcher<Integer> stopPosition() {
    return environment -> getSource(environment).stopPosition();
  }

  @Override
  public DataFetcher<Trip> trip() {
    return environment -> getSource(environment).trip();
  }

  private TransferEndpoint getSource(DataFetchingEnvironment environment) {
    return environment.getSource();
  }
}
