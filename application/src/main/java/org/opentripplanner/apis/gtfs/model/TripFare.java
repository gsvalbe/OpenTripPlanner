package org.opentripplanner.apis.gtfs.model;

import java.util.List;
import org.opentripplanner.model.fare.FareOffer;
import org.opentripplanner.transit.model.site.StopLocation;

/**
 * The fare for travelling on a single trip between two of its stops. Used to back the GraphQL
 * {@code TripFare} type returned by {@code Trip.faresFrom} and {@code Trip.fareBetween}.
 */
public record TripFare(StopLocation fromStop, StopLocation toStop, List<FareOffer> fareProducts) {}
