package org.opentripplanner.ext.vectortiles.layers.ptstops;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.opentripplanner.apis.support.mapping.PropertyMapper;
import org.opentripplanner.ext.vectortiles.VectorTilesResource;
import org.opentripplanner.ext.vectortiles.layers.LayerFilters;
import org.opentripplanner.inspector.vector.LayerBuilder;
import org.opentripplanner.inspector.vector.LayerParameters;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.service.TransitService;

public class PTStopsLayerBuilder extends LayerBuilder<StopOrStation> {
  static Map<PTStopsLayerBuilder.MapperType, BiFunction<TransitService, Locale, PropertyMapper<StopOrStation>>> mappers =
    Map.of(PTStopsLayerBuilder.MapperType.Pieturas, PieturasStopPropertyMapper::create);

  private final TransitService transitService;
  private final Predicate<RegularStop> filter;

  public PTStopsLayerBuilder(
    TransitService transitService,
    LayerParameters<VectorTilesResource.LayerType> layerParameters,
    Locale locale
  ) {
    super(
      mappers.get(PTStopsLayerBuilder.MapperType.valueOf(layerParameters.mapper())).apply(transitService, locale),
      layerParameters.name(),
      layerParameters.expansionFactor()
    );
    this.transitService = transitService;
    this.filter = LayerFilters.forType(layerParameters.filterType(), transitService);
  }

  protected List<Geometry> getGeometries(Envelope query) {
    var regStops = transitService
      .findRegularStopsByBoundingBox(query)
      .stream()
      .filter(st -> st.getParentStation() == null)
      .filter(filter)
      .map(StopOrStation::new)
      .toList();

    var stations = transitService
      .listStations()
      .stream()
      .map(StopOrStation::new)
      .toList();

    return Stream.concat(regStops.stream(), stations.stream())
      .map(st -> {
        Geometry point = st.getGeometry();
        point.setUserData(st);

        return point;
      })
      .toList();
  }

  enum MapperType {
    Pieturas,
  }
}
