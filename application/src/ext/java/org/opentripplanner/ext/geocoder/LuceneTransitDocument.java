package org.opentripplanner.ext.geocoder;

import java.util.Collection;
import javax.annotation.Nullable;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.transit.model.basic.TransitMode;

/**
 * A package-private helper type for transporting a route or a pattern before it is indexed.
 */
record LuceneTransitDocument(
  String id,
  Collection<String> names,
  Collection<String> codes,
  @Nullable TransitMode mode,
  Collection<WgsCoordinate> coordinates
) {}
