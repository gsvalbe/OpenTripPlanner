package org.opentripplanner.apis.gtfs.datafetchers;

import static org.opentripplanner.transfer.constrained.model.TransferConstraint.NOT_SET;
import static org.opentripplanner.transfer.constrained.model.TransferPriority.RECOMMENDED;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.opentripplanner.apis.gtfs.GraphQLRequestContext;
import org.opentripplanner.apis.gtfs.generated.GraphQLDataFetchers;
import org.opentripplanner.apis.gtfs.generated.GraphQLTypes;
import org.opentripplanner.apis.gtfs.model.TransferEndpoint;
import org.opentripplanner.transfer.constrained.model.ConstrainedTransfer;
import org.opentripplanner.transfer.constrained.model.TransferConstraint;
import org.opentripplanner.transit.service.TransitService;

public class TripTransferImpl implements GraphQLDataFetchers.GraphQLTripTransfer {

  @Override
  public DataFetcher<TransferEndpoint> from() {
    return environment ->
      TransferEndpoint.of(getSource(environment).getFrom(), getTransitService(environment));
  }

  @Override
  public DataFetcher<String> id() {
    return environment -> {
      var id = getSource(environment).getId();
      return id != null ? id.toString() : null;
    };
  }

  @Override
  public DataFetcher<Integer> maxWaitTime() {
    return environment -> {
      int v = getSource(environment).getTransferConstraint().getMaxWaitTime();
      return v != NOT_SET ? v : null;
    };
  }

  @Override
  public DataFetcher<Integer> minTransferTime() {
    return environment -> {
      int v = getSource(environment).getTransferConstraint().getMinTransferTime();
      return v != NOT_SET ? v : null;
    };
  }

  @Override
  public DataFetcher<TransferEndpoint> to() {
    return environment ->
      TransferEndpoint.of(getSource(environment).getTo(), getTransitService(environment));
  }

  @Override
  public DataFetcher<GraphQLTypes.GraphQLTransferType> transferType() {
    return environment -> {
      TransferConstraint c = getSource(environment).getTransferConstraint();
      if (c.isNotAllowed()) {
        return GraphQLTypes.GraphQLTransferType.FORBIDDEN;
      }
      if (c.isStaySeated()) {
        return GraphQLTypes.GraphQLTransferType.STAY_SEATED;
      }
      if (c.isGuaranteed()) {
        return GraphQLTypes.GraphQLTransferType.GUARANTEED;
      }
      if (c.getMinTransferTime() != NOT_SET) {
        return GraphQLTypes.GraphQLTransferType.MIN_TIME;
      }
      if (c.getPriority() == RECOMMENDED) {
        return GraphQLTypes.GraphQLTransferType.RECOMMENDED;
      }
      return GraphQLTypes.GraphQLTransferType.DEFAULT;
    };
  }

  private TransitService getTransitService(DataFetchingEnvironment environment) {
    return environment.<GraphQLRequestContext>getContext().transitService();
  }

  private ConstrainedTransfer getSource(DataFetchingEnvironment environment) {
    return environment.getSource();
  }
}
