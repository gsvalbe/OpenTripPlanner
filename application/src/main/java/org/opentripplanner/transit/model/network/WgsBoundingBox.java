package org.opentripplanner.transit.model.network;

import java.util.Collection;
import java.util.Optional;
import org.opentripplanner.street.geometry.SphericalDistanceLibrary;
import org.opentripplanner.street.geometry.WgsCoordinate;

/**
 * An axis-aligned WGS84 bounding box. Boxes crossing the 180th meridian are not supported; such a
 * box is simply stretched across the whole longitude range covered by its coordinates.
 */
public record WgsBoundingBox(double minLat, double minLon, double maxLat, double maxLon) {
  public WgsBoundingBox {
    if (minLat > maxLat || minLon > maxLon) {
      throw new IllegalArgumentException(
        "Invalid bounding box: [%s, %s, %s, %s]".formatted(minLat, minLon, maxLat, maxLon)
      );
    }
  }

  /**
   * Create the smallest bounding box containing all the given coordinates, or empty if there are
   * no coordinates.
   */
  public static Optional<WgsBoundingBox> of(Collection<WgsCoordinate> coordinates) {
    if (coordinates.isEmpty()) {
      return Optional.empty();
    }
    double minLat = Double.MAX_VALUE;
    double minLon = Double.MAX_VALUE;
    double maxLat = -Double.MAX_VALUE;
    double maxLon = -Double.MAX_VALUE;
    for (WgsCoordinate c : coordinates) {
      minLat = Math.min(minLat, c.latitude());
      minLon = Math.min(minLon, c.longitude());
      maxLat = Math.max(maxLat, c.latitude());
      maxLon = Math.max(maxLon, c.longitude());
    }
    return Optional.of(new WgsBoundingBox(minLat, minLon, maxLat, maxLon));
  }

  public boolean contains(WgsCoordinate c) {
    return (
      c.latitude() >= minLat &&
      c.latitude() <= maxLat &&
      c.longitude() >= minLon &&
      c.longitude() <= maxLon
    );
  }

  /**
   * Distance in meters from the given coordinate to the nearest point of this box, {@code 0} if
   * the coordinate is inside the box.
   */
  public double distanceTo(WgsCoordinate c) {
    if (contains(c)) {
      return 0;
    }
    double lat = clamp(c.latitude(), minLat, maxLat);
    double lon = clamp(c.longitude(), minLon, maxLon);
    return SphericalDistanceLibrary.distance(c.latitude(), c.longitude(), lat, lon);
  }

  /**
   * Approximate area in square meters. Precise enough to compare the size of boxes.
   */
  public double area() {
    double height = SphericalDistanceLibrary.degreesLatitudeToMeters(maxLat - minLat);
    double cosLat = Math.cos(Math.toRadians((minLat + maxLat) / 2));
    double width = SphericalDistanceLibrary.degreesLatitudeToMeters(maxLon - minLon) * cosLat;
    return height * width;
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }
}
