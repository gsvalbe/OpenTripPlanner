package org.opentripplanner.ext.generalpattern.internal.graphbuilder;

import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.street.geometry.WgsCoordinate;

/**
 * A polyline in a local planar projection (meters), with the distance along the line (the
 * measure) at every vertex. Used to project stops onto a variant's shape (linear referencing).
 * <p>
 * The projection is equirectangular around one reference latitude per route. Over the extent of a
 * single route the error is far below the thresholds used (hundreds of meters).
 */
final class ProjectedLine {

  private static final double METERS_PER_DEGREE_LAT = 110_540;
  private static final double METERS_PER_DEGREE_LON_AT_EQUATOR = 111_320;

  private final double[] xs;
  private final double[] ys;
  private final double[] measures;

  private ProjectedLine(double[] xs, double[] ys) {
    this.xs = xs;
    this.ys = ys;
    this.measures = new double[xs.length];
    for (int i = 1; i < xs.length; i++) {
      measures[i] = measures[i - 1] + Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
    }
  }

  /** The projection constant for longitude, given the route's reference latitude. */
  static double lonScale(double referenceLatitude) {
    return METERS_PER_DEGREE_LON_AT_EQUATOR * Math.cos(Math.toRadians(referenceLatitude));
  }

  static double projectX(double lon, double lonScale) {
    return lon * lonScale;
  }

  static double projectY(double lat) {
    return lat * METERS_PER_DEGREE_LAT;
  }

  static ProjectedLine of(LineString line, double lonScale) {
    Coordinate[] cs = line.getCoordinates();
    double[] xs = new double[cs.length];
    double[] ys = new double[cs.length];
    for (int i = 0; i < cs.length; i++) {
      xs[i] = projectX(cs[i].x, lonScale);
      ys[i] = projectY(cs[i].y);
    }
    return new ProjectedLine(xs, ys);
  }

  static ProjectedLine of(List<WgsCoordinate> points, double lonScale) {
    double[] xs = new double[points.size()];
    double[] ys = new double[points.size()];
    for (int i = 0; i < points.size(); i++) {
      xs[i] = projectX(points.get(i).longitude(), lonScale);
      ys[i] = projectY(points.get(i).latitude());
    }
    return new ProjectedLine(xs, ys);
  }

  double length() {
    return measures[measures.length - 1];
  }

  Projection project(double x, double y) {
    return project(x, y, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
  }

  /**
   * Nearest point on the line, searching only the segments that overlap the measure range
   * {@code [lo, hi]}.
   */
  Projection project(double x, double y, double lo, double hi) {
    if (xs.length == 1) {
      return new Projection(Math.hypot(xs[0] - x, ys[0] - y), 0);
    }
    double bestDistance = Double.POSITIVE_INFINITY;
    double bestMeasure = 0;
    for (int i = 0; i < xs.length - 1; i++) {
      if (measures[i + 1] < lo || measures[i] > hi) {
        continue;
      }
      double dx = xs[i + 1] - xs[i];
      double dy = ys[i + 1] - ys[i];
      double len2 = dx * dx + dy * dy;
      double t = len2 == 0 ? 0 : ((x - xs[i]) * dx + (y - ys[i]) * dy) / len2;
      t = Math.max(0, Math.min(1, t));
      double d = Math.hypot(xs[i] + t * dx - x, ys[i] + t * dy - y);
      if (d < bestDistance) {
        bestDistance = d;
        bestMeasure = measures[i] + t * Math.sqrt(len2);
      }
    }
    return new Projection(bestDistance, bestMeasure);
  }

  /** The point at the given measure, as {@code [x, y]}. */
  double[] pointAt(double measure) {
    if (xs.length == 1 || measure <= 0) {
      return new double[] { xs[0], ys[0] };
    }
    for (int i = 0; i < xs.length - 1; i++) {
      if (measures[i + 1] >= measure) {
        double seg = measures[i + 1] - measures[i];
        double t = seg == 0 ? 0 : (measure - measures[i]) / seg;
        return new double[] { xs[i] + t * (xs[i + 1] - xs[i]), ys[i] + t * (ys[i + 1] - ys[i]) };
      }
    }
    return new double[] { xs[xs.length - 1], ys[ys.length - 1] };
  }

  /**
   * @param distance distance in meters from the point to the line
   * @param measure distance in meters along the line to the nearest point
   */
  record Projection(double distance, double measure) {}
}
