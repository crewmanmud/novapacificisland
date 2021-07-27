package ca.andrewmccallum.novapacificisland.booking.dto;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import ca.andrewmccallum.novapacificisland.booking.model.Booking;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class BookingDTO {

    @NotNull
    LocalDate checkinDate;
    @NotNull
    LocalDate checkoutDate;
    @Email
    String email;
    @NotNull
    @NotBlank
    String fullName;

    public BookingDTO(Booking booking) {
        this.checkinDate = booking.getCheckinDate();
        this.checkoutDate = booking.getCheckoutDate();
        this.email = booking.getEmail();
        this.fullName = booking.getFullName();
    }
}
