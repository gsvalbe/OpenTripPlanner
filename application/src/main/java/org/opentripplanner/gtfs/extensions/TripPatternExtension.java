package org.opentripplanner.gtfs.extensions;

import org.onebusaway.csv_entities.schema.annotations.CsvField;

/**
 * OBA Trip extension that captures {@code trip_pattern_name} and {@code trip_pattern_type} columns
 * from {@code trips.txt}, providing pattern naming and typicality directly on the trip row.
 *
 * <p>Registered against {@code org.onebusaway.gtfs.model.Trip} via {@code addExtension(...)} in
 * {@link org.opentripplanner.gtfs.graphbuilder.GtfsModule}.
 */
public class TripPatternExtension {

  @CsvField(name = "trip_pattern_name", optional = true)
  private String tripPatternName;

  @CsvField(name = "trip_pattern_type", optional = true)
  private String tripPatternType;

  public String getTripPatternName() {
    return tripPatternName;
  }

  public void setTripPatternName(String tripPatternName) {
    this.tripPatternName = tripPatternName;
  }

  public String getTripPatternType() {
    return tripPatternType;
  }

  public void setTripPatternType(String tripPatternType) {
    this.tripPatternType = tripPatternType;
  }
}
