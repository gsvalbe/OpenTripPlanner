package org.opentripplanner.ext.vectortiles.layers.ptstops;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.opentripplanner.apis.support.mapping.PropertyMapper;
import org.opentripplanner.core.model.i18n.I18NStringMapper;
import org.opentripplanner.ext.stopbearing.StopBearingService;
import org.opentripplanner.inspector.vector.KeyValue;
import org.opentripplanner.transit.service.TransitService;

public class PieturasStopPropertyMapper extends PropertyMapper<StopOrStation> {

  private final TransitService transitService;
  private final StopBearingService stopBearingService;
  private final I18NStringMapper i18NStringMapper;

  PieturasStopPropertyMapper(
    TransitService transitService,
    StopBearingService stopBearingService,
    Locale locale
  ) {
    this.transitService = transitService;
    this.stopBearingService = stopBearingService;
    this.i18NStringMapper = new I18NStringMapper(locale);
  }

  protected static PieturasStopPropertyMapper create(
    TransitService transitService,
    StopBearingService stopBearingService,
    Locale locale
  ) {
    return new PieturasStopPropertyMapper(transitService, stopBearingService, locale);
  }

  @Override
  protected Collection<KeyValue> map(StopOrStation stop) {
    return getBaseKeyValues(stop, i18NStringMapper, transitService, stopBearingService);
  }

  /**
   * The {@code bearing} is the direction vehicles leave the stop, in degrees clockwise from north.
   * It is left out when the stop has none, so map styles can fall back to an unrotated icon.
   */
  protected static Collection<KeyValue> getBaseKeyValues(
    StopOrStation stop,
    I18NStringMapper i18NStringMapper,
    TransitService transitService,
    StopBearingService stopBearingService
  ) {
    return List.of(
      new KeyValue("gtfsId", stop.getId().toString()),
      new KeyValue("name", i18NStringMapper.mapNonnullToApi(stop.getName())),
      new KeyValue("code", stop.getCode()),
      new KeyValue("desc", i18NStringMapper.mapToApi(stop.getDesc())),
      new KeyValue("mode", stop.getMode(transitService)),
      new KeyValue("routes", stop.getRoutes(transitService)),
      new KeyValue("bearing", stopBearingService.findBearing(stop.getId()).orElse(null))
    );
  }
}
