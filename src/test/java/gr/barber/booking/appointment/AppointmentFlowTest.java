package gr.barber.booking.appointment;

import gr.barber.booking.dto.AppointmentRequestDTO;
import gr.barber.booking.exception.ConflictException;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.Role;
import gr.barber.booking.model.ServiceOffering;
import gr.barber.booking.model.User;
import gr.barber.booking.service.AppointmentService;
import gr.barber.booking.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Now" is Monday 7 January 2030, 10:00 Athens time. The barber works
 * Monday 10:00-14:00 and 17:00-21:00, Tuesday 10:00-14:00.
 */
class AppointmentFlowTest extends IntegrationTest {

    private static final String TUESDAY = "2030-01-08";

    @Autowired
    private AppointmentService appointmentService;

    private User customer;
    private User otherCustomer;
    private User barberUser;
    private Barber barber;
    private ServiceOffering haircut;

    @BeforeEach
    void setUp() {
        customer = createUser("Maria", "maria@test.gr", Role.CUSTOMER);
        otherCustomer = createUser("Eleni", "eleni@test.gr", Role.CUSTOMER);
        barberUser = createUser("Nikos", "nikos@test.gr", Role.BARBER);
        barber = createApprovedBarber(barberUser, true);
        // These scenarios use quarter-hour starts; the 30-minute default is covered in SlotStepTest.
        barber.setSlotStepMinutes(15);
        barberRepository.save(barber);
        haircut = createService(barber, "Haircut", 30, "12.00");
        addShift(barber, DayOfWeek.MONDAY, "10:00", "14:00");
        addShift(barber, DayOfWeek.MONDAY, "17:00", "21:00");
        addShift(barber, DayOfWeek.TUESDAY, "10:00", "14:00");
    }

    // ---- availability ----

    @Test
    void availabilityCoversSplitShiftsAndSkipsThePast() throws Exception {
        // 10:00 is "now", so today starts at 10:15; last morning slot is 13:30, last evening slot 20:30.
        availability("2030-01-07")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.startTimes", hasSize(14 + 15)))
                .andExpect(jsonPath("$.startTimes[0]").value("10:15"))
                .andExpect(jsonPath("$.startTimes[13]").value("13:30"))
                .andExpect(jsonPath("$.startTimes[14]").value("17:00"))
                .andExpect(jsonPath("$.startTimes[28]").value("20:30"));
    }

    @Test
    void bookedTimeDisappearsFromAvailability() throws Exception {
        book(customer, TUESDAY + "T11:00").andExpect(status().isCreated());

        // 10:45 would end at 11:15 and 11:15 starts inside the booking, so 10:45-11:15 are gone.
        availability(TUESDAY)
                .andExpect(jsonPath("$.startTimes", hasSize(15 - 3)))
                .andExpect(jsonPath("$.startTimes[2]").value("10:30"))
                .andExpect(jsonPath("$.startTimes[3]").value("11:30"));
    }

    @Test
    void noAvailabilityBeyondTheBookingWindowOrOnDaysOff() throws Exception {
        availability("2030-01-22").andExpect(jsonPath("$.startTimes", hasSize(0))); // 15 days ahead

        mockMvc.perform(post("/barbers/me/time-off")
                        .header("Authorization", bearer(barberUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"%s","endDate":"%s","reason":"Holiday"}""".formatted(TUESDAY, TUESDAY)))
                .andExpect(status().isCreated());

        availability(TUESDAY).andExpect(jsonPath("$.startTimes", hasSize(0)));
        book(customer, TUESDAY + "T10:00").andExpect(status().isBadRequest());
    }

    @Test
    void timeOffReportsAppointmentsTheBarberStillHasToHandle() throws Exception {
        book(customer, TUESDAY + "T10:00").andExpect(status().isCreated());

        mockMvc.perform(post("/barbers/me/time-off")
                        .header("Authorization", bearer(barberUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"%s","endDate":"%s"}""".formatted(TUESDAY, TUESDAY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activeAppointments").value(1));
    }

    // ---- booking ----

    @Test
    void bookingIsConfirmedImmediatelyWhenBarberAutoConfirms() throws Exception {
        book(customer, TUESDAY + "T10:00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.serviceName").value("Haircut"))
                .andExpect(jsonPath("$.price").value(12.00))
                .andExpect(jsonPath("$.startTime").value("2030-01-08T10:00:00+02:00"))
                .andExpect(jsonPath("$.endTime").value("2030-01-08T10:30:00+02:00"));
    }

    @Test
    void bookingWaitsForBarberWhenAutoConfirmIsOff() throws Exception {
        barber.setAutoConfirmBookings(false);
        barberRepository.save(barber);

        long id = idOf(book(customer, TUESDAY + "T10:00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING")));

        // A pending request already blocks the slot for others.
        book(otherCustomer, TUESDAY + "T10:00").andExpect(status().isConflict());

        barberAction(id, "confirm").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        barberAction(id, "confirm").andExpect(status().isBadRequest());
    }

    @Test
    void rejectedRequestFreesTheSlot() throws Exception {
        barber.setAutoConfirmBookings(false);
        barberRepository.save(barber);

        long id = idOf(book(customer, TUESDAY + "T10:00"));
        barberAction(id, "reject").andExpect(jsonPath("$.status").value("REJECTED"));

        book(otherCustomer, TUESDAY + "T10:00").andExpect(status().isCreated());
    }

    @Test
    void overlappingBookingIsRejected() throws Exception {
        book(customer, TUESDAY + "T10:00").andExpect(status().isCreated());

        book(otherCustomer, TUESDAY + "T10:00").andExpect(status().isConflict());
        book(otherCustomer, TUESDAY + "T10:15").andExpect(status().isConflict());
        book(otherCustomer, TUESDAY + "T10:30").andExpect(status().isCreated()); // back-to-back is fine
    }

    @Test
    void customerCannotHoldTwoAppointmentsAtTheSameTime() throws Exception {
        User secondBarberUser = createUser("Kostas", "kostas@test.gr", Role.BARBER);
        Barber secondBarber = createApprovedBarber(secondBarberUser, true);
        secondBarber.setSlotStepMinutes(15);
        barberRepository.save(secondBarber);
        ServiceOffering beard = createService(secondBarber, "Beard", 30, "8.00");
        addShift(secondBarber, DayOfWeek.TUESDAY, "10:00", "14:00");

        book(customer, TUESDAY + "T10:00").andExpect(status().isCreated());
        book(customer, secondBarber.getId(), beard.getId(), TUESDAY + "T10:15").andExpect(status().isConflict());
    }

    @Test
    void bookingOutsideWorkingHoursOrWindowIsRejected() throws Exception {
        book(customer, TUESDAY + "T09:30").andExpect(status().isBadRequest());   // before opening
        book(customer, TUESDAY + "T13:45").andExpect(status().isBadRequest());   // would end after 14:00
        book(customer, "2030-01-09T10:00").andExpect(status().isBadRequest());   // Wednesday, not working
        book(customer, "2030-01-07T09:00").andExpect(status().isBadRequest());   // in the past
        book(customer, "2030-01-22T10:00").andExpect(status().isBadRequest());   // 15 days ahead
    }

    @Test
    void onlyCustomersCanBookAndUnknownServicesAreRejected() throws Exception {
        book(barberUser, TUESDAY + "T10:00").andExpect(status().isForbidden());
        book(customer, barber.getId(), 9999L, TUESDAY + "T10:00").andExpect(status().isNotFound());
        mockMvc.perform(post("/appointments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()); // anonymous
    }

    @Test
    void deactivatedServiceCannotBeBookedButExistingBookingsKeepItsDetails() throws Exception {
        book(customer, TUESDAY + "T10:00").andExpect(status().isCreated());

        mockMvc.perform(delete("/barbers/me/services/" + haircut.getId()).header("Authorization", bearer(barberUser)))
                .andExpect(status().isNoContent());

        book(otherCustomer, TUESDAY + "T11:00").andExpect(status().isNotFound());
        mockMvc.perform(get("/appointments/me").header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$[0].serviceName").value("Haircut"))
                .andExpect(jsonPath("$[0].price").value(12.00));
    }

    @Test
    void simultaneousBookingsOfTheSameSlotLetExactlyOneThrough() throws Exception {
        int attempts = 6;
        List<User> customers = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            customers.add(createUser("Customer " + i, "c" + i + "@test.gr", Role.CUSTOMER));
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        List<Future<Boolean>> results = new ArrayList<>();

        for (User c : customers) {
            Callable<Boolean> attempt = () -> {
                start.await();
                try {
                    appointmentService.book(c.getEmail(), new AppointmentRequestDTO(
                            barber.getId(), haircut.getId(), LocalDateTime.parse(TUESDAY + "T10:00"), null));
                    return true;
                } catch (ConflictException e) {
                    return false;
                }
            };
            results.add(pool.submit(attempt));
        }

        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                succeeded++;
            }
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(1);
    }

    // A customer holds at most two upcoming appointments, across all barbers, so nobody can block
    // a barber's week. One that has started, or was cancelled, frees a place.
    @Test
    void customersHoldAtMostTwoUpcomingAppointments() throws Exception {
        User otherBarberUser = createUser("Kostas", "kostas@test.gr", Role.BARBER);
        Barber otherBarber = createApprovedBarber(otherBarberUser, true);
        ServiceOffering beard = createService(otherBarber, "Beard", 30, "8.00");
        addShift(otherBarber, DayOfWeek.TUESDAY, "10:00", "14:00");

        long first = idOf(book(customer, TUESDAY + "T11:00"));
        book(customer, TUESDAY + "T10:00").andExpect(status().isCreated());

        book(customer, otherBarber.getId(), beard.getId(), TUESDAY + "T12:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_LIMIT"));

        customerCancel(customer, first).andExpect(status().isOk());
        book(customer, otherBarber.getId(), beard.getId(), TUESDAY + "T12:00").andExpect(status().isCreated());
        book(customer, TUESDAY + "T13:00").andExpect(status().isConflict());

        clock.set(java.time.Instant.parse("2030-01-08T08:01:00Z")); // Tuesday 10:01 Athens: 10:00 has started
        book(customer, TUESDAY + "T13:00").andExpect(status().isCreated());
    }

    @Test
    void errorsCarryACodeTheAppCanTranslate() throws Exception {
        book(customer, TUESDAY + "T10:00").andExpect(status().isCreated());
        book(otherCustomer, TUESDAY + "T10:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_TAKEN"));
        book(otherCustomer, TUESDAY + "T15:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BARBER_NOT_WORKING"));
    }

    // Each barber chooses how far ahead customers can book: 14 days (default), a month or two months.
    @Test
    void customersBookWithinTheBarbersBookingWindow() throws Exception {
        String inThreeWeeks = "2030-01-28"; // a Monday, 21 days from now

        availability(inThreeWeeks).andExpect(jsonPath("$.startTimes", hasSize(0)));
        book(customer, inThreeWeeks + "T10:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_FAR_AHEAD"));

        setBookingWindow(45).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_BOOKING_WINDOW"));
        setBookingWindow(30).andExpect(status().isOk()).andExpect(jsonPath("$.bookingWindowDays").value(30));

        availability(inThreeWeeks).andExpect(jsonPath("$.startTimes[0]").value("10:00"));
        book(customer, inThreeWeeks + "T10:00").andExpect(status().isCreated());
        book(otherCustomer, "2030-02-07T10:00").andExpect(status().isBadRequest()); // a month and a bit
    }

    private ResultActions setBookingWindow(int days) throws Exception {
        return mockMvc.perform(put("/barbers/me")
                .header("Authorization", bearer(barberUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bookingWindowDays":%d}""".formatted(days)));
    }

    // ---- cancellation ----

    @Test
    void customerCanCancelUpToTwoHoursBefore() throws Exception {
        long id = idOf(book(customer, TUESDAY + "T10:00"));

        clock.set(java.time.Instant.parse("2030-01-08T05:59:00Z")); // 07:59 Athens, 2h01m before
        customerCancel(customer, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledBy").value("CUSTOMER"));

        customerCancel(customer, id).andExpect(status().isBadRequest()); // already cancelled
    }

    @Test
    void customerCannotCancelAConfirmedAppointmentWithinTwoHours() throws Exception {
        long id = idOf(book(customer, TUESDAY + "T10:00"));

        clock.set(java.time.Instant.parse("2030-01-08T06:30:00Z")); // 08:30 Athens
        customerCancel(customer, id).andExpect(status().isBadRequest());

        // The barber can still cancel.
        barberAction(id, "cancel").andExpect(jsonPath("$.cancelledBy").value("BARBER"));
    }

    @Test
    void pendingRequestCanBeWithdrawnAnyTimeBeforeItStarts() throws Exception {
        barber.setAutoConfirmBookings(false);
        barberRepository.save(barber);
        long id = idOf(book(customer, TUESDAY + "T10:00"));

        clock.advance(Duration.ofHours(23)); // Tuesday 09:00 Athens
        customerCancel(customer, id).andExpect(status().isOk());
    }

    @Test
    void customersCannotSeeOrCancelEachOthersAppointments() throws Exception {
        long id = idOf(book(customer, TUESDAY + "T10:00"));

        customerCancel(otherCustomer, id).andExpect(status().isNotFound());
        mockMvc.perform(get("/appointments/me").header("Authorization", bearer(otherCustomer)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void barberSeesTheirScheduleWithCustomerContact() throws Exception {
        book(customer, TUESDAY + "T10:00");
        book(otherCustomer, TUESDAY + "T11:00");

        mockMvc.perform(get("/barbers/me/appointments").header("Authorization", bearer(barberUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].customerName").value("Maria"))
                .andExpect(jsonPath("$[0].customerPhone").value("6900000000"))
                .andExpect(jsonPath("$[1].customerName").value("Eleni"));
    }

    // ---- helpers ----

    private ResultActions availability(String date) throws Exception {
        return mockMvc.perform(get("/barbers/{id}/availability", barber.getId())
                .param("serviceId", haircut.getId().toString())
                .param("date", date));
    }

    private ResultActions book(User who, String startTime) throws Exception {
        return book(who, barber.getId(), haircut.getId(), startTime);
    }

    private ResultActions book(User who, Long barberId, Long serviceId, String startTime) throws Exception {
        return mockMvc.perform(post("/appointments")
                .header("Authorization", bearer(who))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"barberId":%d,"serviceId":%d,"startTime":"%s"}""".formatted(barberId, serviceId, startTime)));
    }

    private ResultActions customerCancel(User who, long id) throws Exception {
        return mockMvc.perform(post("/appointments/{id}/cancel", id).header("Authorization", bearer(who)));
    }

    private ResultActions barberAction(long id, String action) throws Exception {
        return mockMvc.perform(post("/barbers/me/appointments/{id}/" + action, id)
                .header("Authorization", bearer(barberUser)));
    }

    private static long idOf(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("(?s)^\\{\"id\":(\\d+).*", "$1"));
    }
}
