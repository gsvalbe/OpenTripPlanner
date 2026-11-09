package org.opentripplanner.apis.gtfs.datafetchers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opentripplanner.apis.support.graphql.DataFetchingSupport.dataFetchingEnvironment;
import static org.opentripplanner.transit.service.RouteRegionsTestModel.JURMALA;
import static org.opentripplanner.transit.service.RouteRegionsTestModel.RIGA;
import static org.opentripplanner.transit.service.RouteRegionsTestModel.RIGA_CENTER;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.network.RouteRegion;
import org.opentripplanner.transit.service.RouteRegionsTestModel;
import org.opentripplanner.transit.service.TransitService;

class QueryTypeImplRouteRegionsTest {

  private static final TransitService TRANSIT_SERVICE = RouteRegionsTestModel.transitService();
  private static final QueryTypeImpl IMPL = new QueryTypeImpl();

  /** A location inside both Riga and Riga Center. */
  private static final Map<String, Object> IN_RIGA_CENTER = Map.of("lat", 56.95, "lon", 24.11);

  /** A location inside Jurmala, west of Riga. */
  private static final Map<String, Object> IN_JURMALA = Map.of("lat", 56.97, "lon", 23.70);

  @Test
  void regionsWithoutLocationReturnsAllNames() throws Exception {
    var names = regions(Map.of());
    assertEquals(Set.of(RIGA, RIGA_CENTER, JURMALA), Set.copyOf(names));
    assertEquals(3, names.size());
  }

  @Test
  void regionsSortedByLocation() throws Exception {
    assertEquals(List.of(RIGA_CENTER, RIGA, JURMALA), regions(IN_RIGA_CENTER));
    assertEquals(List.of(JURMALA, RIGA, RIGA_CENTER), regions(IN_JURMALA));
  }

  @Test
  void routeRegionsWithoutLocationSortedByName() throws Exception {
    assertEquals(List.of(JURMALA, RIGA, RIGA_CENTER), names(routeRegions(Map.of())));
  }

  @Test
  void routeRegionsSortedByLocation() throws Exception {
    assertEquals(List.of(RIGA_CENTER, RIGA, JURMALA), names(routeRegions(IN_RIGA_CENTER)));
    assertEquals(List.of(JURMALA, RIGA, RIGA_CENTER), names(routeRegions(IN_JURMALA)));
  }

  @Test
  void routeRegionsBounds() throws Exception {
    var center = routeRegions(Map.of()).get(2);
    var bounds = new BoundingBoxImpl();
    var regionImpl = new RouteRegionImpl();
    var box = regionImpl.bounds().get(dataFetchingEnvironment(center));
    assertEquals(RIGA_CENTER, regionImpl.name().get(dataFetchingEnvironment(center)));
    assertEquals(56.94, bounds.minLat().get(dataFetchingEnvironment(box)));
    assertEquals(24.10, bounds.minLon().get(dataFetchingEnvironment(box)));
    assertEquals(56.96, bounds.maxLat().get(dataFetchingEnvironment(box)));
    assertEquals(24.12, bounds.maxLon().get(dataFetchingEnvironment(box)));
  }

  @Test
  void latitudeWithoutLongitudeIsRejected() {
    var env = dataFetchingEnvironment(null, Map.of("lat", 56.95), TRANSIT_SERVICE);
    assertThrows(IllegalArgumentException.class, () -> IMPL.routeRegions().get(env));
    assertThrows(IllegalArgumentException.class, () -> IMPL.regions().get(env));
  }

  private static List<String> regions(Map<String, Object> args) throws Exception {
    var env = dataFetchingEnvironment(null, args, TRANSIT_SERVICE);
    return StreamSupport.stream(IMPL.regions().get(env).spliterator(), false).toList();
  }

  private static List<RouteRegion> routeRegions(Map<String, Object> args) throws Exception {
    var env = dataFetchingEnvironment(null, args, TRANSIT_SERVICE);
    return StreamSupport.stream(IMPL.routeRegions().get(env).spliterator(), false).toList();
  }

  private static List<String> names(List<RouteRegion> regions) {
    return regions.stream().map(RouteRegion::name).toList();
  }
}
