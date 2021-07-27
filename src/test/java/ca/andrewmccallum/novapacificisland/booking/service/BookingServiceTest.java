package ca.andrewmccallum.novapacificisland.booking.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import ca.andrewmccallum.novapacificisland.booking.dto.BookingDTO;
import ca.andrewmccallum.novapacificisland.booking.model.Booking;
import ca.andrewmccallum.novapacificisland.booking.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final Instant INSTANT_TODAY = Instant.ofEpochSecond(1627300556L);
    private static final Instant INSTANT_YESTERDAY = Instant.ofEpochSecond(1627214173L);
    private static final LocalDate DATE_TODAY = LocalDate.of(2021, 7, 26);
    private static final LocalDate DATE_TOMORROW = LocalDate.of(2021, 7, 27);
    private static final LocalDate TO_DATE = LocalDate.of(2021, 7, 29);
    private static final LocalDate DATE_NEXT_MONTH = LocalDate.of(2021, 8, 29);
    private static final String EMAIL = "email@booking.test";
    private static final String FULL_NAME = "Booking User";
    private static final UUID ID = UUID.fromString("03c09a44-1168-4080-8478-6d8d258a4ea0");

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private Clock clock;

    @InjectMocks
    private BookingService bookingService;

    @Test
    void findAvailability_success() {

        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(INSTANT_TODAY);
        when(bookingRepository.findBookingsByCheckinDateBetween(DATE_TODAY, TO_DATE))
                .thenReturn(List.of());

        var availableDates = bookingService.findAvailability(DATE_TODAY, TO_DATE);
        assertEquals(3, availableDates.size());
        assertEquals(LocalDate.of(2021, 7, 26), availableDates.get(0));
        assertEquals(LocalDate.of(2021, 7, 27), availableDates.get(1));
        assertEquals(LocalDate.of(2021, 7, 28), availableDates.get(2));
    }

    @Test
    void findAvailability_success_whenNoEndDate() {

        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(INSTANT_TODAY);
        var dateInAMonth = DATE_TODAY.plusMonths(1);
        when(bookingRepository.findBookingsByCheckinDateBetween(DATE_TODAY, dateInAMonth))
                .thenReturn(List.of());

        var availableDates = bookingService.findAvailability(DATE_TODAY, null);
        assertEquals(31, availableDates.size());
        assertEquals(LocalDate.of(2021, 7, 26), availableDates.get(0));
        assertEquals(LocalDate.of(2021, 8, 25), availableDates.get(30));
    }

    @Test
    void findAvailability_fails_whenEndAfterStart() {

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> bookingService.findAvailability(TO_DATE, DATE_TODAY));

        assertEquals("The `from` date must be before the `to` date.", exception.getMessage());
    }

    @Test
    void findAvailability_fails_whenQueryingPast() {

        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(INSTANT_TODAY);

        var dateInPast = DATE_TODAY.minusDays(1);
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> bookingService.findAvailability(dateInPast, TO_DATE));

        assertEquals("The `from` date must not be in the past.", exception.getMessage());
    }

    @Test
    void createBooking_success() {

        Booking booking = new Booking(ID, DATE_TODAY, TO_DATE, EMAIL, FULL_NAME);

        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(INSTANT_YESTERDAY);
        when(bookingRepository.findBookingsByCheckinDateBetween(DATE_TODAY, TO_DATE)).thenReturn(List.of());
        when(bookingRepository.save(notNull())).thenReturn(booking);

        var bookingRequest = new BookingDTO(DATE_TODAY, TO_DATE, EMAIL, FULL_NAME);
        UUID uuid = bookingService.createBooking(bookingRequest);
        assertEquals(ID, uuid);
    }

    @Test
    void createBooking_fails_whenDatesBookedAlready() {

        Booking booking = new Booking(ID, DATE_TODAY, DATE_TOMORROW, EMAIL, FULL_NAME);

        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(INSTANT_YESTERDAY);
        when(bookingRepository.findBookingsByCheckinDateBetween(DATE_TODAY, TO_DATE)).thenReturn(List.of(booking));

        var bookingRequest = new BookingDTO(DATE_TODAY, TO_DATE, EMAIL, FULL_NAME);
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> bookingService.createBooking(bookingRequest));
        assertEquals("The date(s) requested are no longer available.", exception.getMessage());
    }

    @Test
    void createBooking_fails_whenCheckinBeforeCheckout() {

        var booking = new BookingDTO(TO_DATE, DATE_TODAY, EMAIL, FULL_NAME);
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> bookingService.createBooking(booking));
        assertEquals("Check-in and check-out date combination is invalid.", exception.getMessage());
    }

    @Test
    void createBooking_fails_whenCheckoutSameDay() {

        var booking = new BookingDTO(DATE_TODAY, DATE_TODAY, EMAIL, FULL_NAME);
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> bookingService.createBooking(booking));
        assertEquals("Cannot check-in and check-out on same day.", exception.getMessage());
    }

    @Test
    void createBooking_fails_whenBookingSameDay() {

        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(INSTANT_TODAY);

        var booking = new BookingDTO(DATE_TODAY, TO_DATE, EMAIL, FULL_NAME);
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> bookingService.createBooking(booking));
        assertEquals("Bookings must be made at least one day in advance.", exception.getMessage());
    }

    @Test
    void createBooking_fails_whenBookingMoreThanMaxStay() {

        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(INSTANT_YESTERDAY);

        var booking = new BookingDTO(DATE_TODAY, DATE_TODAY.plusDays(4), EMAIL, FULL_NAME);
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> bookingService.createBooking(booking));
        assertEquals("Booking cannot exceed maximum number of nights allowed.", exception.getMessage());
    }

    @Test
    void createBooking_fails_whenBookingMoreThanMonthInAdvance() {

        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(INSTANT_TODAY);

        var booking = new BookingDTO(DATE_NEXT_MONTH, DATE_NEXT_MONTH.plusDays(1), EMAIL, FULL_NAME);
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> bookingService.createBooking(booking));
        assertEquals("Bookings cannot be made more than a month in advance.", exception.getMessage());
    }

    @Test
    void getBooking_success() {

        Booking booking = new Booking(ID, DATE_TODAY, DATE_TOMORROW, EMAIL, FULL_NAME);
        when(bookingRepository.findById(ID)).thenReturn(Optional.of(booking));

        var result = bookingService.getBooking(ID);
        assertNotNull(result);
        assertEquals(DATE_TODAY, result.getCheckinDate());
        assertEquals(DATE_TOMORROW, result.getCheckoutDate());
        assertEquals(EMAIL, result.getEmail());
        assertEquals(FULL_NAME, result.getFullName());
    }

    @Test
    void getBooking_fails_whenNotFound() {

        when(bookingRepository.findById(ID)).thenReturn(Optional.empty());

        NoSuchElementException exception =
                assertThrows(NoSuchElementException.class, () -> bookingService.getBooking(ID));
        assertEquals("Cannot find booking with specified ID.", exception.getMessage());
    }

    @Test
    void cancelBooking_success() {

        assertDoesNotThrow(() -> bookingService.cancelBooking(ID));
    }

    @Test
    void cancelBooking_fails_whenNotFound() {

        doThrow(new EmptyResultDataAccessException(1)).when(bookingRepository).deleteById(ID);

        NoSuchElementException exception = assertThrows(NoSuchElementException.class, () -> bookingService.cancelBooking(ID));
        assertEquals("Cannot find booking with specified ID.", exception.getMessage());
    }

    @Test
    void updateBooking_success_whenNameUpdated() {

        Booking booking = new Booking(ID, DATE_TODAY, DATE_TOMORROW, EMAIL, FULL_NAME);
        when(bookingRepository.findById(ID)).thenReturn(Optional.of(booking));

        var updatedValue = "New Name";
        var updateRequest = new BookingDTO(null, null, null, updatedValue);
        var updatedBooking = new Booking(ID, DATE_TODAY, DATE_TOMORROW, EMAIL, updatedValue);
        when(bookingRepository.save(updatedBooking)).thenReturn(updatedBooking);

        var result = bookingService.updateBooking(ID, updateRequest);

        assertNotNull(result);
        assertEquals(DATE_TODAY, result.getCheckinDate());
        assertEquals(DATE_TOMORROW, result.getCheckoutDate());
        assertEquals(EMAIL, result.getEmail());
        assertEquals(updatedValue, result.getFullName());
        verify(bookingRepository, never()).findBookingsByCheckinDateBetween(any(), any());
    }

    @Test
    void updateBooking_success_whenEmailUpdated() {

        Booking booking = new Booking(ID, DATE_TODAY, DATE_TOMORROW, EMAIL, FULL_NAME);
        when(bookingRepository.findById(ID)).thenReturn(Optional.of(booking));

        var updatedValue = "b@domain.test";
        var updateRequest = new BookingDTO(null, null, updatedValue, null);
        var updatedBooking = new Booking(ID, DATE_TODAY, DATE_TOMORROW, updatedValue, FULL_NAME);
        when(bookingRepository.save(updatedBooking)).thenReturn(updatedBooking);

        var result = bookingService.updateBooking(ID, updateRequest);

        assertNotNull(result);
        assertEquals(DATE_TODAY, result.getCheckinDate());
        assertEquals(DATE_TOMORROW, result.getCheckoutDate());
        assertEquals(updatedValue, result.getEmail());
        assertEquals(FULL_NAME, result.getFullName());
        verify(bookingRepository, never()).findBookingsByCheckinDateBetween(any(), any());
    }

    @Test
    void updateBooking_success_whenCheckinDateUpdated() {

        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(INSTANT_YESTERDAY);

        var checkoutDate = DATE_TOMORROW.plusDays(1);
        Booking booking = new Booking(ID, DATE_TODAY, checkoutDate, EMAIL, FULL_NAME);
        when(bookingRepository.findById(ID)).thenReturn(Optional.of(booking));

        var updatedValue = DATE_TOMORROW;
        var updateRequest = new BookingDTO(updatedValue, null, null, null);
        var updatedBooking = new Booking(ID, updatedValue, checkoutDate, EMAIL, FULL_NAME);
        when(bookingRepository.save(updatedBooking)).thenReturn(updatedBooking);

        var result = bookingService.updateBooking(ID, updateRequest);

        assertNotNull(result);
        assertEquals(updatedValue, result.getCheckinDate());
        assertEquals(checkoutDate, result.getCheckoutDate());
        assertEquals(EMAIL, result.getEmail());
        assertEquals(FULL_NAME, result.getFullName());
    }

    @Test
    void updateBooking_success_whenCheckoutDateUpdated() {

        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(INSTANT_YESTERDAY);

        Booking booking = new Booking(ID, DATE_TODAY, DATE_TOMORROW, EMAIL, FULL_NAME);
        when(bookingRepository.findById(ID)).thenReturn(Optional.of(booking));

        var updatedValue = DATE_TOMORROW.plusDays(1);
        var updateRequest = new BookingDTO(null, updatedValue, null, null);
        var updatedBooking = new Booking(ID, DATE_TODAY, updatedValue, EMAIL, FULL_NAME);
        when(bookingRepository.save(updatedBooking)).thenReturn(updatedBooking);

        var result = bookingService.updateBooking(ID, updateRequest);

        assertNotNull(result);
        assertEquals(DATE_TODAY, result.getCheckinDate());
        assertEquals(updatedValue, result.getCheckoutDate());
        assertEquals(EMAIL, result.getEmail());
        assertEquals(FULL_NAME, result.getFullName());
    }

    @Test
    void updateBooking_fails_whenCheckinDateUpdated_andStayInProgress() {

        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(INSTANT_YESTERDAY);

        Booking booking = new Booking(ID, DATE_TODAY.minusDays(1), DATE_TOMORROW, EMAIL, FULL_NAME);
        when(bookingRepository.findById(ID)).thenReturn(Optional.of(booking));

        var updateRequest = new BookingDTO(DATE_TODAY, null, null, null);

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> bookingService.updateBooking(ID, updateRequest));
        assertEquals("Stay is already in progress.", exception.getMessage());
    }
}