package org.opentripplanner.transit.service;

import static org.opentripplanner.transit.model._data.TransitRepositoryForTest.route;
import static org.opentripplanner.transit.model._data.TransitRepositoryForTest.stopPattern;
import static org.opentripplanner.transit.model._data.TransitRepositoryForTest.tripPattern;

import java.util.List;
import org.opentripplanner.transit.model._data.TransitRepositoryForTest;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.site.RegularStop;

/**
 * Transit model with three route regions: "Riga" containing the smaller "Riga Center", and the
 * separate "Jurmala". There is also one route without a region.
 */
public class RouteRegionsTestModel {

  public static final String RIGA = "Riga";
  public static final String RIGA_CENTER = "Riga Center";
  public static final String JURMALA = "Jurmala";

  private static final TransitRepositoryForTest MODEL = TransitRepositoryForTest.of();

  private static final RegularStop RIGA_SOUTH_WEST = MODEL.stop("RSW", 56.90, 24.00).build();
  private static final RegularStop RIGA_NORTH_EAST = MODEL.stop("RNE", 57.00, 24.20).build();
  private static final RegularStop CENTER_1 = MODEL.stop("C1", 56.94, 24.10).build();
  private static final RegularStop CENTER_2 = MODEL.stop("C2", 56.96, 24.12).build();
  private static final RegularStop JURMALA_WEST = MODEL.stop("JW", 56.95, 23.60).build();
  private static final RegularStop JURMALA_EAST = MODEL.stop("JE", 56.98, 23.80).build();

  public static DefaultTransitService transitService() {
    var siteRepository = MODEL.siteRepositoryBuilder()
      .withRegularStops(
        List.of(RIGA_SOUTH_WEST, RIGA_NORTH_EAST, CENTER_1, CENTER_2, JURMALA_WEST, JURMALA_EAST)
      )
      .build();
    var transitRepository = new TransitRepository(siteRepository);

    // Two routes in "Riga", the region bounds should cover both
    addPattern(transitRepository, "R1", RIGA, RIGA_SOUTH_WEST, CENTER_1);
    addPattern(transitRepository, "R2", RIGA, CENTER_2, RIGA_NORTH_EAST);
    addPattern(transitRepository, "C", RIGA_CENTER, CENTER_1, CENTER_2);
    addPattern(transitRepository, "J", JURMALA, JURMALA_WEST, JURMALA_EAST);
    addPattern(transitRepository, "X", null, JURMALA_WEST, RIGA_NORTH_EAST);

    transitRepository.index();
    return new DefaultTransitService(transitRepository);
  }

  private static void addPattern(
    TransitRepository repository,
    String id,
    String regionName,
    RegularStop... stops
  ) {
    Route route = route(id).withRegionName(regionName).build();
    var pattern = tripPattern(id, route).withStopPattern(stopPattern(stops)).build();
    repository.addTripPattern(pattern.getId(), pattern);
  }
}
