package org.opentripplanner.transit.model.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.transit.model.basic.WgsBoundingBox;

class RouteRegionTest {

  private static final RouteRegion LARGE = new RouteRegion(
    "Large",
    new WgsBoundingBox(56.0, 23.0, 58.0, 26.0)
  );
  private static final RouteRegion SMALL = new RouteRegion(
    "Small",
    new WgsBoundingBox(56.9, 24.0, 57.1, 24.2)
  );
  private static final RouteRegion FAR = new RouteRegion(
    "Far",
    new WgsBoundingBox(59.0, 24.0, 60.0, 25.0)
  );
  private static final RouteRegion NEAR = new RouteRegion(
    "Near",
    new WgsBoundingBox(58.1, 24.0, 58.5, 25.0)
  );
  private static final RouteRegion NO_BOUNDS = new RouteRegion("No bounds", null);

  @Test
  void containingRegionsFirstSmallestBeforeLargest() {
    var location = new WgsCoordinate(57.0, 24.1);
    assertEquals(List.of(SMALL, LARGE, NEAR, FAR, NO_BOUNDS), sort(location));
  }

  @Test
  void closestFirstWhenOutsideAllRegions() {
    var location = new WgsCoordinate(58.7, 24.5);
    assertEquals(List.of(NEAR, FAR, LARGE, SMALL, NO_BOUNDS), sort(location));
  }

  @Test
  void tiesAreBrokenByName() {
    var box = new WgsBoundingBox(56, 24, 57, 25);
    var b = new RouteRegion("B", box);
    var a = new RouteRegion("A", box);
    var noBoundsB = new RouteRegion("D", null);
    var noBoundsA = new RouteRegion("C", null);
    var sorted = Stream.of(noBoundsB, b, noBoundsA, a)
      .sorted(RouteRegion.closestFirst(new WgsCoordinate(56.5, 24.5)))
      .toList();
    assertEquals(List.of(a, b, noBoundsA, noBoundsB), sorted);
  }

  private static List<RouteRegion> sort(WgsCoordinate location) {
    return Stream.of(NO_BOUNDS, FAR, LARGE, NEAR, SMALL)
      .sorted(RouteRegion.closestFirst(location))
      .toList();
  }
}
