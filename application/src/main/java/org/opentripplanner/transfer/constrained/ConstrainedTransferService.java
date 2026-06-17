package org.opentripplanner.transfer.constrained;

import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.transfer.constrained.model.ConstrainedTransfer;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.Trip;

public interface ConstrainedTransferService {
  void addAll(Collection<ConstrainedTransfer> transfers);

  List<ConstrainedTransfer> listAll();

  @Nullable
  ConstrainedTransfer findTransfer(
    Trip fromTrip,
    int fromStopPosition,
    StopLocation fromStop,
    Trip toTrip,
    int toStopPosition,
    StopLocation toStop
  );

  /** All constrained transfers where the FROM endpoint is a specific trip. */
  List<ConstrainedTransfer> findTransfersByFromTrip(Trip trip);

  /** All constrained transfers where the TO endpoint is a specific trip. */
  List<ConstrainedTransfer> findTransfersByToTrip(Trip trip);
}
