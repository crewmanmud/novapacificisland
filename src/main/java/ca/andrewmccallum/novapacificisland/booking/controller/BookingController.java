package ca.andrewmccallum.novapacificisland.booking.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import ca.andrewmccallum.novapacificisland.booking.dto.BookingDTO;
import ca.andrewmccallum.novapacificisland.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/booking",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class BookingController {

    private final BookingService bookingService;

    @Autowired
    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Operation(description = "")
    @ApiResponse(responseCode = "400", description = "Bad request")
    //@ApiResponse(responseCode = "200", content = @Content(examples = @ExampleObject(value = )))
    @GetMapping(value = "/availability")
    public ResponseEntity<List<LocalDate>> availability(@RequestParam LocalDate fromDate, @RequestParam(required = false) LocalDate toDate) {
        return ResponseEntity.ok(bookingService.findAvailability(fromDate, toDate));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UUID> create(@Validated @RequestBody BookingDTO booking) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(bookingService.createBooking(booking));
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingDTO> retrieve(@PathVariable("bookingId") String bookingId) {

        UUID uuid = parseUUID(bookingId);

        return ResponseEntity.ok(bookingService.getBooking(uuid));
    }

    @DeleteMapping("/{bookingId}")
    public ResponseEntity<BookingDTO> cancel(@PathVariable("bookingId") String bookingId) {

        UUID uuid = parseUUID(bookingId);

        bookingService.cancelBooking(uuid);
        return ResponseEntity.noContent()
                .build();
    }

    @PatchMapping("/{bookingId}")
    public ResponseEntity<BookingDTO> modify(@PathVariable("bookingId") String bookingId, @RequestBody BookingDTO bookingDTO) {

        UUID uuid = parseUUID(bookingId);

        return ResponseEntity.ok(bookingService.updateBooking(uuid, bookingDTO));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    private ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {

        return ResponseEntity.badRequest()
                .contentType(MediaType.TEXT_PLAIN)
                .body(e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    private ResponseEntity<String> handleNoSuchElementException(NoSuchElementException e) {

        return ResponseEntity.notFound()
                .build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    private ResponseEntity<String> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {

        FieldError fieldError = e.getFieldError();
        if (fieldError != null) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Invalid value for '" + fieldError.getField() + "' value.");
        }

        return ResponseEntity.badRequest()
                .contentType(MediaType.TEXT_PLAIN)
                .body(e.getMessage());
    }

    private static UUID parseUUID(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid ID provided.", e);
        }
    }
}
