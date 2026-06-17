package org.opentripplanner.apis.gtfs.model;

import javax.annotation.Nullable;
import org.opentripplanner.transfer.constrained.model.RouteStopTransferPoint;
import org.opentripplanner.transfer.constrained.model.StopTransferPoint;
import org.opentripplanner.transfer.constrained.model.TransferPoint;
import org.opentripplanner.transfer.constrained.model.TripTransferPoint;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.service.TransitService;

public record TransferEndpoint(
  @Nullable Trip trip,
  @Nullable StopLocation stop,
  @Nullable Integer stopPosition
) {
  public static TransferEndpoint of(TransferPoint point, TransitService service) {
    return switch (point) {
      case TripTransferPoint tp -> {
        var pattern = service.findPattern(tp.getTrip());
        var stop = pattern != null ? pattern.getStops().get(tp.getStopPositionInPattern()) : null;
        yield new TransferEndpoint(tp.getTrip(), stop, tp.getStopPositionInPattern());
      }
      case RouteStopTransferPoint rp -> new TransferEndpoint(null, rp.getStop(), null);
      case StopTransferPoint sp -> new TransferEndpoint(null, sp.getStop(), null);
      default -> new TransferEndpoint(null, null, null);
    };
  }
}
