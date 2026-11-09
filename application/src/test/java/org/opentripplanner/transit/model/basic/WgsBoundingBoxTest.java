package org.opentripplanner.transit.model.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.opentripplanner.street.geometry.WgsCoordinate;

class WgsBoundingBoxTest {

  private static final WgsBoundingBox BOX = new WgsBoundingBox(56.0, 24.0, 57.0, 25.0);

  @Test
  void ofCoordinates() {
    var box = WgsBoundingBox.of(
      List.of(
        new WgsCoordinate(56.5, 24.9),
        new WgsCoordinate(57.0, 24.0),
        new WgsCoordinate(56.0, 24.5)
      )
    );
    assertEquals(Optional.of(new WgsBoundingBox(56.0, 24.0, 57.0, 24.9)), box);
  }

  @Test
  void ofNoCoordinates() {
    assertEquals(Optional.empty(), WgsBoundingBox.of(List.of()));
  }

  @Test
  void invalidBounds() {
    assertThrows(IllegalArgumentException.class, () -> new WgsBoundingBox(57, 24, 56, 25));
    assertThrows(IllegalArgumentException.class, () -> new WgsBoundingBox(56, 25, 57, 24));
  }

  @Test
  void contains() {
    assertTrue(BOX.contains(new WgsCoordinate(56.5, 24.5)));
    assertTrue(BOX.contains(new WgsCoordinate(56.0, 24.0)));
    assertFalse(BOX.contains(new WgsCoordinate(57.1, 24.5)));
    assertFalse(BOX.contains(new WgsCoordinate(56.5, 23.9)));
  }

  @Test
  void distanceTo() {
    assertEquals(0, BOX.distanceTo(new WgsCoordinate(56.5, 24.5)));
    // One tenth of a degree latitude north of the box is roughly 11.1 km
    assertEquals(11_119, BOX.distanceTo(new WgsCoordinate(57.1, 24.5)), 10);
    // Diagonal distance to the north-east corner is longer than either side
    double corner = BOX.distanceTo(new WgsCoordinate(57.1, 25.1));
    assertTrue(corner > BOX.distanceTo(new WgsCoordinate(57.1, 24.5)));
    assertTrue(corner > BOX.distanceTo(new WgsCoordinate(56.5, 25.1)));
  }

  @Test
  void area() {
    var small = new WgsBoundingBox(56.4, 24.4, 56.6, 24.6);
    assertTrue(small.area() < BOX.area());
    assertEquals(0, new WgsBoundingBox(56, 24, 56, 25).area());
    // Same size in degrees covers less area further north
    var north = new WgsBoundingBox(66.0, 24.0, 67.0, 25.0);
    assertTrue(north.area() < BOX.area());
  }
}
