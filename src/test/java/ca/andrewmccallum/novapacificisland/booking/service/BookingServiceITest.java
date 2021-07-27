package ca.andrewmccallum.novapacificisland.booking.service;

import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import ca.andrewmccallum.novapacificisland.booking.dto.BookingDTO;
import ca.andrewmccallum.novapacificisland.booking.model.Booking;
import ca.andrewmccallum.novapacificisland.booking.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BookingServiceITest {

    @Autowired
    private BookingService bookingService;
    @Autowired
    private BookingRepository bookingRepository;

    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    @Test
    void createBooking_onlyOneBookingForDate() throws InterruptedException, ExecutionException {

        Set<Callable<String>> tasks = Set.of(
                attemptBooking(1),
                attemptBooking(2),
                attemptBooking(3),
                attemptBooking(4),
                attemptBooking(5)
        );

        var futures = executorService.invokeAll(tasks);

        executorService.shutdown();
        if (!executorService.awaitTermination(10L, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }

        for (Future<String> future : futures) {
            System.out.println(future.get());
        }

        assertEquals(1, bookingRepository.count());
    }

    private Callable<String> attemptBooking(long userNumber) {

        return () -> {
            var checkinDate = LocalDate.now().plusDays(1);
            var bookingDto = new BookingDTO(checkinDate, checkinDate.plusDays(1), "a@a.com", "a a");

            try {
                bookingService.createBooking(bookingDto);
            } catch (IllegalArgumentException e) {
                return "User " + userNumber + ", failed - " + e.getMessage();
            }

            return "User " + userNumber + ", success - check-in " + checkinDate;
        };
    }
}