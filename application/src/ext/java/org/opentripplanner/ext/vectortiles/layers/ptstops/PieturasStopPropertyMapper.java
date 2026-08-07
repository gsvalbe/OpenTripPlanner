package org.opentripplanner.ext.vectortiles.layers.ptstops;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.opentripplanner.apis.support.mapping.PropertyMapper;
import org.opentripplanner.core.model.i18n.I18NStringMapper;
import org.opentripplanner.inspector.vector.KeyValue;
import org.opentripplanner.transit.service.TransitService;

public class PieturasStopPropertyMapper extends PropertyMapper<StopOrStation> {

  private final TransitService transitService;
  private final I18NStringMapper i18NStringMapper;

  PieturasStopPropertyMapper(TransitService transitService, Locale locale) {
    this.transitService = transitService;
    this.i18NStringMapper = new I18NStringMapper(locale);
  }

  protected static PieturasStopPropertyMapper create(TransitService transitService, Locale locale) {
    return new PieturasStopPropertyMapper(transitService, locale);
  }

  @Override
  protected Collection<KeyValue> map(StopOrStation stop) {
    return getBaseKeyValues(stop, i18NStringMapper, transitService);
  }

  protected static Collection<KeyValue> getBaseKeyValues(
    StopOrStation stop,
    I18NStringMapper i18NStringMapper,
    TransitService transitService
  ) {
    return List.of(
      new KeyValue("gtfsId", stop.getId().toString()),
      new KeyValue("name", i18NStringMapper.mapNonnullToApi(stop.getName())),
      new KeyValue("code", stop.getCode()),
      new KeyValue("desc", i18NStringMapper.mapToApi(stop.getDesc())),
      new KeyValue("mode", stop.getMode(transitService)),
      new KeyValue("routes", stop.getRoutes(transitService))
    );
  }
}
