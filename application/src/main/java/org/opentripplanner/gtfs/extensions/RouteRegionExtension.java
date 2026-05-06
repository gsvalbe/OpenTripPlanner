package org.opentripplanner.gtfs.extensions;

import org.onebusaway.csv_entities.schema.annotations.CsvField;

/**
 * OBA Route extension that captures the {@code region_name} column from {@code routes.txt},
 * allowing feeds to associate routes with a named geographic region.
 *
 * <p>Registered against {@code org.onebusaway.gtfs.model.Route} via {@code addExtension(...)} in
 * {@link org.opentripplanner.gtfs.graphbuilder.GtfsModule}.
 */
public class RouteRegionExtension {

  @CsvField(name = "region_name", optional = true)
  private String regionName;

  public String getRegionName() {
    return regionName;
  }

  public void setRegionName(String regionName) {
    this.regionName = regionName;
  }
}
