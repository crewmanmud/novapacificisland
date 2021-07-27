package ca.andrewmccallum.novapacificisland.booking.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ca.andrewmccallum.novapacificisland.booking.model.Booking;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface BookingRepository extends CrudRepository<Booking, UUID> {

    /**
     * Get bookings with a check-in date within the provided window.
     * @param startDate the start date (inclusive)
     * @param endDate the end date (exclusive)
     * @return a list of bookings
     */
    @Transactional(readOnly = true)
    List<Booking> findBookingsByCheckinDateBetween(LocalDate startDate, LocalDate endDate);
}
