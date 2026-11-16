package org.opentripplanner.apis.gtfs.datafetchers;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentripplanner.apis.support.graphql.DataFetchingSupport;
import org.opentripplanner.ext.generalpattern.internal.DefaultGeneralPatternRepository;
import org.opentripplanner.ext.generalpattern.internal.DefaultGeneralPatternService;
import org.opentripplanner.ext.stopbearing.internal.DefaultStopBearingRepository;
import org.opentripplanner.ext.stopbearing.internal.DefaultStopBearingService;
import org.opentripplanner.transit.model._data.TransitRepositoryForTest;

class StopImplTest {

  private static final TransitRepositoryForTest MODEL = TransitRepositoryForTest.of();
  private static final StopImpl STOP_IMPL = new StopImpl();

  @Test
  void bearingOfStopAndStation() throws Exception {
    var stop = MODEL.stop("A").build();
    var station = MODEL.station("S").build();
    var other = MODEL.stop("B").build();
    var repository = new DefaultStopBearingRepository();
    repository.putAll(Map.of(stop.getId(), 90, station.getId(), 270));

    assertThat(bearing(stop, repository)).isEqualTo(90);
    assertThat(bearing(station, repository)).isEqualTo(270);
    assertThat(bearing(other, repository)).isNull();
  }

  private static Integer bearing(Object source, DefaultStopBearingRepository repository)
    throws Exception {
    var env = DataFetchingSupport.dataFetchingEnvironment(
      source,
      Map.of(),
      null,
      null,
      new DefaultGeneralPatternService(new DefaultGeneralPatternRepository()),
      new DefaultStopBearingService(repository)
    );
    return STOP_IMPL.bearing().get(env);
  }
}
