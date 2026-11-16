package org.opentripplanner.ext.stopbearing.internal.graphbuilder;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.opentripplanner.ext.stopbearing.internal.graphbuilder.BearingCalculator.Departure;
import org.opentripplanner.ext.stopbearing.parameters.StopBearingParameters;
import org.opentripplanner.street.geometry.GeometryUtils;

class BearingCalculatorTest {

  private static final double LON = 24.0;
  private static final double LAT = 57.0;

  /** About 60 m of longitude at latitude 57. */
  private static final double LON_60_M = 0.001;

  /** About 111 m of latitude. */
  private static final double LAT_111_M = 0.001;

  private final BearingCalculator calculator = new BearingCalculator(StopBearingParameters.DEFAULT);

  @Test
  void departureBearingOfStraightHops() {
    assertThat(bearing(LON, LAT, LON + LON_60_M, LAT))
      .isWithin(0.1)
      .of(90);
    assertThat(bearing(LON, LAT, LON, LAT + LAT_111_M))
      .isWithin(0.1)
      .of(0);
    assertThat(bearing(LON, LAT, LON - LON_60_M, LAT))
      .isWithin(0.1)
      .of(270);
    assertThat(bearing(LON, LAT, LON, LAT - LAT_111_M))
      .isWithin(0.1)
      .of(180);
  }

  @Test
  void longTwoPointHopIsTreatedAsMissingShape() {
    // About 600 m, longer than maxStraightLineHopLength
    var hop = GeometryUtils.makeLineString(LON, LAT, LON + 10 * LON_60_M, LAT);
    assertThat(calculator.departureBearing(hop).isPresent()).isFalse();
  }

  @Test
  void longShapeWithMorePointsIsUsed() {
    var hop = GeometryUtils.makeLineString(
      LON,
      LAT,
      LON + 5 * LON_60_M,
      LAT,
      LON + 10 * LON_60_M,
      LAT
    );
    assertThat(calculator.departureBearing(hop).getAsDouble()).isWithin(0.1).of(90);
  }

  @Test
  void jitterRightAtTheStopIsSmoothedOut() {
    // 5 m east, then 111 m north: the direction is measured to the point 30 m along
    var hop = GeometryUtils.makeLineString(
      LON,
      LAT,
      LON + 0.00008,
      LAT,
      LON + 0.00008,
      LAT + LAT_111_M
    );
    assertThat(calculator.departureBearing(hop).getAsDouble()).isWithin(3).of(2.5);
  }

  @Test
  void turnAfterTheLookAheadDistanceIsIgnored() {
    // 60 m east, then 1 km north
    var hop = GeometryUtils.makeLineString(
      LON,
      LAT,
      LON + LON_60_M,
      LAT,
      LON + LON_60_M,
      LAT + 9 * LAT_111_M
    );
    assertThat(calculator.departureBearing(hop).getAsDouble()).isWithin(0.1).of(90);
  }

  @Test
  void degenerateHopHasNoBearing() {
    var hop = GeometryUtils.makeLineString(LON, LAT, LON, LAT);
    assertThat(calculator.departureBearing(hop).isPresent()).isFalse();
  }

  @Test
  void combineAveragesCloseDirections() {
    assertThat(combine(dep(80, 1), dep(100, 1))).isEqualTo(OptionalInt.of(90));
  }

  @Test
  void combineWrapsAroundNorth() {
    assertThat(combine(dep(350, 1), dep(10, 1))).isEqualTo(OptionalInt.of(0));
    assertThat(combine(dep(359.8, 1))).isEqualTo(OptionalInt.of(0));
  }

  @Test
  void oppositeDirectionsHaveNoBearing() {
    assertThat(combine(dep(0, 1), dep(180, 1))).isEqualTo(OptionalInt.empty());
    assertThat(combine(dep(0, 3), dep(180, 1))).isEqualTo(OptionalInt.empty());
  }

  @Test
  void rareOutlierIsTolerated() {
    assertThat(combine(dep(0, 97), dep(180, 3))).isEqualTo(OptionalInt.of(0));
    assertThat(combine(dep(0, 90), dep(180, 10))).isEqualTo(OptionalInt.empty());
  }

  @Test
  void directionsWithinTheSpreadAreCombined() {
    // maxSpread is 90, so each may be 45 degrees from the mean
    assertThat(combine(dep(0, 1), dep(90, 1))).isEqualTo(OptionalInt.of(45));
    assertThat(combine(dep(0, 1), dep(100, 1))).isEqualTo(OptionalInt.empty());
  }

  @Test
  void noDeparturesHaveNoBearing() {
    assertThat(combine()).isEqualTo(OptionalInt.empty());
  }

  private double bearing(double lon0, double lat0, double lon1, double lat1) {
    return calculator
      .departureBearing(GeometryUtils.makeLineString(lon0, lat0, lon1, lat1))
      .getAsDouble();
  }

  private OptionalInt combine(Departure... departures) {
    return calculator.combine(List.of(departures));
  }

  private static Departure dep(double bearing, double weight) {
    return new Departure(bearing, weight);
  }
}
