package org.opentripplanner.ext.vectortiles.layers.ptstops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.framework.json.ObjectMappers;
import org.opentripplanner.street.geometry.GeometryUtils;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.site.Station;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.service.TransitService;

public class StopOrStation {

  private final Type stopType;
  private final I18NString name;
  private final I18NString desc;
  private final FeedScopedId id;
  private final Point geometry;
  private final String code;
  private final Collection<StopLocation> stopLocations;

  private static final ObjectMapper OBJECT_MAPPER = ObjectMappers.ignoringExtraFields();

  public StopOrStation(RegularStop regStop) {
    this.name = regStop.getName();
    this.desc = regStop.getDescription();
    this.stopType = Type.STOP;
    this.id = regStop.getId();
    this.geometry = regStop.getGeometry();
    this.code = regStop.getCode();

    this.stopLocations = List.of(regStop);
  }

  public StopOrStation(Station station) {
    this.name = station.getName();
    this.desc = station.getDescription();
    this.stopType = Type.STATION;
    this.id = station.getId();
    Coordinate coordinate = station.getCoordinate().asJtsCoordinate();
    this.geometry = GeometryUtils.getGeometryFactory().createPoint(coordinate);
    this.code = station.getCode();

    this.stopLocations = station.getChildStops();
  }

  public Type getStopType() {
    return stopType;
  }

  public I18NString getName() {
    return name;
  }

  public I18NString getDesc() {
    return desc;
  }

  public FeedScopedId getId() {
    return id;
  }

  public Point getGeometry() {
    return geometry;
  }

  public String getCode() {
    return code;
  }

  public enum Type {
    STOP,
    STATION,
  }

  public String getMode(TransitService transitService) {
    return stopLocations
      .stream()
      .flatMap(stop -> transitService.findPatterns(stop).stream())
      .map(TripPattern::getMode)
      .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
      .entrySet()
      .stream()
      .max(Map.Entry.comparingByValue())
      .map(Map.Entry::getKey)
      .map(Enum::name)
      .orElse(null);
  }

  public String getRoutes(TransitService transitService) {
    try {
      var objects = stopLocations
        .stream()
        .flatMap(stop -> transitService.findRoutes(stop).stream())
        .distinct()
        .map(route -> {
          var obj = OBJECT_MAPPER.createObjectNode();
          obj.put("mode", route.getMode().name());
          obj.put("name", route.getName());
          if (route.getColor() != null) {
            obj.put("color", route.getColor());
          }
          if (route.getTextColor() != null) {
            obj.put("textColor", route.getTextColor());
          }
          return obj;
        })
        .toList();
      return OBJECT_MAPPER.writeValueAsString(objects);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
