package ca.andrewmccallum.novapacificisland.booking.model;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Entity
public class Booking {

    public Booking() {
        // for Jackson
    }

    public Booking(Booking copyFrom) {
        this.id = copyFrom.getId();
        this.checkinDate = copyFrom.getCheckinDate();
        this.checkoutDate = copyFrom.getCheckoutDate();
        this.email = copyFrom.getEmail();
        this.fullName = copyFrom.getFullName();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull
    private LocalDate checkinDate;

    @NotNull
    private LocalDate checkoutDate;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String fullName;

}
