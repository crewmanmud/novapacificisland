package ca.andrewmccallum.novapacificisland.booking.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import ca.andrewmccallum.novapacificisland.booking.dto.BookingDTO;
import ca.andrewmccallum.novapacificisland.booking.model.Booking;
import ca.andrewmccallum.novapacificisland.booking.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Service for managing bookings.
 */
@Service
public class BookingService {

    /**
     * The number of nights a booking may be made for.
     */
    private static final int BOOKING_MAX_NIGHTS = 3;
    /**
     * The minimum number of days between booking and check-in.
     */
    private static final int BOOKING_MIN_DAYS_FROM_TODAY = 1;

    private static final String ERROR_BOOKING_NOT_FOUND = "Cannot find booking with specified ID.";

    private final BookingRepository bookingRepository;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock(true);

    @Autowired
    public BookingService(BookingRepository bookingRepository, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.clock = clock;
    }

    /**
     * Find the current availability given a range of dates. If {@code to} parameter is not specified, defaults to a month following {@code from}.
     * @param from the date (inclusive) from which to query
     * @param to the date (exclusive) to serve as upper bound of query
     * @return a list of dates available
     */
    public List<LocalDate> findAvailability(LocalDate from, @Nullable LocalDate to) {

        var toDate = to == null ?
                from.plusMonths(1) :
                to;

        if (from.isAfter(toDate)) {
            throw new IllegalArgumentException("The `from` date must be before the `to` date.");
        }

        // ensure we're not exposing past bookings to competitors
        if (from.isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("The `from` date must not be in the past.");
        }

        var bookingsDuringPeriod = bookingRepository.findBookingsByCheckinDateBetween(from, toDate);
        var daysOccupied = bookingsDuringPeriod.stream()
                .flatMap(b -> b.getCheckinDate().datesUntil(b.getCheckoutDate()))
                .collect(Collectors.toSet());

        return from.datesUntil(toDate)
                .filter(d -> !daysOccupied.contains(d))
                .collect(Collectors.toList());
    }

    /**
     * Create a new booking, assuming latest availabilty agrees.
     * @param bookingDTO the booking details
     * @return the ID of the booking
     * @throws IllegalArgumentException if date validations fail, more than three nights requested, or date(s) are unavailable
     */
    public UUID createBooking(BookingDTO bookingDTO) {

        validateDates(bookingDTO.getCheckinDate(), bookingDTO.getCheckoutDate());

        var newBooking = new Booking(null,
                bookingDTO.getCheckinDate(),
                bookingDTO.getCheckoutDate(),
                bookingDTO.getEmail(),
                bookingDTO.getFullName());

        return lockAndSaveOrUpdate(newBooking, null).getId();
    }

    private void validateDates(LocalDate checkinDate, LocalDate checkoutDate) {

        if (checkinDate.isAfter(checkoutDate)) {
            throw new IllegalArgumentException("Check-in and check-out date combination is invalid.");
        }

        if (checkinDate.until(checkoutDate).getDays() < 1) {
            throw new IllegalArgumentException("Cannot check-in and check-out on same day.");
        }

        if (checkinDate.isBefore(LocalDate.now(clock).plusDays(BOOKING_MIN_DAYS_FROM_TODAY))) {
            throw new IllegalArgumentException("Bookings must be made at least one day in advance.");
        }

        if (checkinDate.until(checkoutDate).getDays() > BOOKING_MAX_NIGHTS) {
            throw new IllegalArgumentException("Booking cannot exceed maximum number of nights allowed.");
        }

        if (checkinDate.isAfter(LocalDate.now(clock).plusMonths(1))) {
            throw new IllegalArgumentException("Bookings cannot be made more than a month in advance.");
        }
    }

    private Booking lockAndSaveOrUpdate(Booking bookingToSaveOrUpdate, @Nullable Collection<LocalDate> originalDates) {

        // lock and execute critical section
        lock.lock();
        try {

            // no-one else writing at this instant, get latest availability
            var availableDates = findAvailability(bookingToSaveOrUpdate.getCheckinDate(), bookingToSaveOrUpdate.getCheckoutDate());

            // add back original dates to pass validation below
            if (originalDates != null) {
                availableDates.addAll(originalDates);
            }

            // ensure all dates for stay are available
            if (!bookingToSaveOrUpdate.getCheckinDate().datesUntil(bookingToSaveOrUpdate.getCheckoutDate())
                    .allMatch(availableDates::contains)) {
                throw new IllegalArgumentException("The date(s) requested are no longer available.");
            }

            return bookingRepository.save(bookingToSaveOrUpdate);

        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieve an existing booking.
     * @param bookingId the {@link UUID} of the booking
     * @return the booking details
     * @throws java.util.NoSuchElementException if entity does not exist
     */
    public BookingDTO getBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .map(BookingDTO::new)
                .orElseThrow(() -> new NoSuchElementException(ERROR_BOOKING_NOT_FOUND));
    }

    /**
     * Cancel an existing booking.
     * @param bookingId the booking ID
     * @throws NoSuchElementException if booking does not exist
     */
    public void cancelBooking(UUID bookingId) {

        try {
            bookingRepository.deleteById(bookingId);
        } catch (EmptyResultDataAccessException e) {
            throw new NoSuchElementException(ERROR_BOOKING_NOT_FOUND);
        }
    }

    /**
     * Update an existing booking - non-null fields will replace existing values.
     * @param bookingId the booking ID
     * @param bookingDTO the booking details to replace existing with
     * @return the updated booking
     * @throws IllegalArgumentException if any validations fail
     * @throws java.util.NoSuchElementException if booking does not exist
     */
    public BookingDTO updateBooking(UUID bookingId, BookingDTO bookingDTO) {

        var existingBooking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException(ERROR_BOOKING_NOT_FOUND));

        var updatedBooking = new Booking(existingBooking);

        if (bookingDTO.getEmail() != null) {
            updatedBooking.setEmail(bookingDTO.getEmail());
        }

        if (bookingDTO.getFullName() != null) {
            updatedBooking.setFullName(bookingDTO.getFullName());
        }

        if (bookingDTO.getCheckinDate() != null || bookingDTO.getCheckoutDate() != null) {

            // change the dates
            var originalDates = existingBooking.getCheckinDate()
                    .datesUntil(existingBooking.getCheckoutDate())
                    .collect(Collectors.toList());
            if (bookingDTO.getCheckinDate() != null) {

                if (LocalDate.now(clock).compareTo(existingBooking.getCheckinDate()) > -1) {
                    throw new IllegalArgumentException("Stay is already in progress.");
                }

                updatedBooking.setCheckinDate(bookingDTO.getCheckinDate());
            }
            if (bookingDTO.getCheckoutDate() != null) {
                updatedBooking.setCheckoutDate(bookingDTO.getCheckoutDate());
            }

            validateDates(updatedBooking.getCheckinDate(), updatedBooking.getCheckoutDate());

            return new BookingDTO(lockAndSaveOrUpdate(updatedBooking, originalDates));
        }

        // update details other than dates
        return new BookingDTO(bookingRepository.save(updatedBooking));
    }
}
