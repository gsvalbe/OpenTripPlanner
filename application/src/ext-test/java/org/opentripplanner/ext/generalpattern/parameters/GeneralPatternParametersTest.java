package org.opentripplanner.ext.generalpattern.parameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model._data.TimetableRepositoryForTest;
import org.opentripplanner.transit.model.basic.TransitMode;

class GeneralPatternParametersTest {

  private static GeneralPatternParameters excluding(
    Set<String> regions,
    Set<String> feeds,
    Set<String> agencies,
    Set<TransitMode> modes
  ) {
    var d = GeneralPatternParameters.DEFAULT;
    return new GeneralPatternParameters(
      regions,
      feeds,
      agencies,
      modes,
      d.stationFallbackRadius(),
      d.minSharedStations(),
      d.corridorBuffer(),
      d.corridorMinOverlap(),
      d.skipRadius(),
      d.railSkipRadius()
    );
  }

  @Test
  void defaultIncludesEveryRoute() {
    var route = TimetableRepositoryForTest.route("R1").withRegionName("Rīga").build();
    assertTrue(GeneralPatternParameters.DEFAULT.includes(route));
  }

  @Test
  void excludeByRegionFeedAgencyAndMode() {
    var cityRoute = TimetableRepositoryForTest.route("R1").withRegionName("Rīga").build();
    var regional = TimetableRepositoryForTest.route("R2").withRegionName("Sigulda").build();
    var rail = TimetableRepositoryForTest.route("R3").withMode(TransitMode.RAIL).build();

    var byRegion = excluding(Set.of("Rīga"), Set.of(), Set.of(), Set.of());
    assertFalse(byRegion.includes(cityRoute));
    assertTrue(byRegion.includes(regional));

    var byFeed = excluding(Set.of(), Set.of(cityRoute.getId().getFeedId()), Set.of(), Set.of());
    assertFalse(byFeed.includes(regional));

    var agencyId = cityRoute.getAgency().getId().toString();
    var byAgency = excluding(Set.of(), Set.of(), Set.of(agencyId), Set.of());
    assertFalse(byAgency.includes(regional));

    var byMode = excluding(Set.of(), Set.of(), Set.of(), Set.of(TransitMode.RAIL));
    assertFalse(byMode.includes(rail));
    assertTrue(byMode.includes(regional));
  }

  @Test
  void railUsesItsOwnSkipRadius() {
    var d = GeneralPatternParameters.DEFAULT;
    assertEquals(250, d.skipRadius(TransitMode.RAIL));
    assertEquals(400, d.skipRadius(TransitMode.BUS));
  }

  @Test
  void invalidValuesAreRejected() {
    assertThrows(IllegalArgumentException.class, () ->
      new GeneralPatternParameters(
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        400,
        2,
        1000,
        1.5,
        400,
        250
      )
    );
    assertThrows(IllegalArgumentException.class, () ->
      new GeneralPatternParameters(
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        0,
        2,
        1000,
        0.8,
        400,
        250
      )
    );
  }
}
