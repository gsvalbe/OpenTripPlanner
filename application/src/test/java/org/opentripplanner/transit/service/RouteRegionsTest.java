package org.opentripplanner.transit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.transit.service.RouteRegionsTestModel.JURMALA;
import static org.opentripplanner.transit.service.RouteRegionsTestModel.RIGA;
import static org.opentripplanner.transit.service.RouteRegionsTestModel.RIGA_CENTER;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.basic.WgsBoundingBox;
import org.opentripplanner.transit.model.network.RouteRegion;

class RouteRegionsTest {

  @Test
  void boundsAreDerivedFromStopsOfAllRoutesInRegion() {
    Map<String, RouteRegion> regions = RouteRegionsTestModel.transitService()
      .listRouteRegions()
      .stream()
      .collect(Collectors.toMap(RouteRegion::name, Function.identity()));

    assertEquals(
      Map.of(
        RIGA,
        new RouteRegion(RIGA, new WgsBoundingBox(56.90, 24.00, 57.00, 24.20)),
        RIGA_CENTER,
        new RouteRegion(RIGA_CENTER, new WgsBoundingBox(56.94, 24.10, 56.96, 24.12)),
        JURMALA,
        new RouteRegion(JURMALA, new WgsBoundingBox(56.95, 23.60, 56.98, 23.80))
      ),
      regions
    );
  }
}
