package gr.barber.booking.service;

import gr.barber.booking.config.BookingProperties;
import gr.barber.booking.model.Appointment;
import gr.barber.booking.model.WorkingHours;
import gr.barber.booking.repository.AppointmentRepository;
import gr.barber.booking.repository.TimeOffRepository;
import gr.barber.booking.repository.WorkingHoursRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Works out when a barber can take an appointment: inside working hours, not on a day off,
 * within the booking window, and not overlapping another active appointment.
 */
@Service
public class AvailabilityService {

    private final WorkingHoursRepository workingHoursRepository;
    private final TimeOffRepository timeOffRepository;
    private final AppointmentRepository appointmentRepository;
    private final BookingProperties bookingProperties;
    private final Clock clock;

    public AvailabilityService(WorkingHoursRepository workingHoursRepository,
                               TimeOffRepository timeOffRepository,
                               AppointmentRepository appointmentRepository,
                               BookingProperties bookingProperties,
                               Clock clock) {
        this.workingHoursRepository = workingHoursRepository;
        this.timeOffRepository = timeOffRepository;
        this.appointmentRepository = appointmentRepository;
        this.bookingProperties = bookingProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<LocalTime> findAvailableStartTimes(Long barberId, LocalDate date, int durationMinutes, int stepMinutes) {

        if (!isWithinBookingWindow(date) || timeOffRepository.isOff(barberId, date)) {
            return List.of();
        }

        ZoneId zone = bookingProperties.timeZone();
        Instant now = clock.instant();
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<Appointment> booked = appointmentRepository.findActiveForBarberOverlapping(barberId, dayStart, dayEnd);

        List<LocalTime> available = new ArrayList<>();

        for (WorkingHours shift : shiftsOn(barberId, date)) {
            LocalTime start = shift.getStartTime();
            while (!start.plusMinutes(durationMinutes).isAfter(shift.getEndTime())
                    && start.plusMinutes(durationMinutes).isAfter(start)) {

                Instant slotStart = date.atTime(start).atZone(zone).toInstant();
                Instant slotEnd = slotStart.plusSeconds(durationMinutes * 60L);

                if (slotStart.isAfter(now) && booked.stream().noneMatch(a -> overlaps(a, slotStart, slotEnd))) {
                    available.add(start);
                }

                LocalTime next = start.plusMinutes(stepMinutes);
                if (!next.isAfter(start)) {
                    break; // wrapped past midnight
                }
                start = next;
            }
        }

        return available;
    }

    /**
     * Checks everything except overlaps with other appointments, which the caller checks
     * (and the database enforces) separately.
     */
    @Transactional(readOnly = true)
    public boolean fitsSchedule(Long barberId, LocalDate date, LocalTime start, int durationMinutes, int stepMinutes) {

        if (!isWithinBookingWindow(date) || timeOffRepository.isOff(barberId, date)) {
            return false;
        }

        LocalTime end = start.plusMinutes(durationMinutes);
        if (!end.isAfter(start)) {
            return false; // would run past midnight
        }

        // Starts must fall on the barber's step from the shift start, as offered to customers.
        return shiftsOn(barberId, date).stream()
                .anyMatch(shift -> !start.isBefore(shift.getStartTime())
                        && !end.isAfter(shift.getEndTime())
                        && Duration.between(shift.getStartTime(), start).toMinutes() % stepMinutes == 0);
    }

    public boolean isWithinBookingWindow(LocalDate date) {
        LocalDate today = LocalDate.now(clock.withZone(bookingProperties.timeZone()));
        return !date.isBefore(today) && !date.isAfter(today.plusDays(bookingProperties.maxDaysAhead()));
    }

    private List<WorkingHours> shiftsOn(Long barberId, LocalDate date) {
        return workingHoursRepository.findAllByBarberId(barberId).stream()
                .filter(shift -> shift.getDayOfWeek() == date.getDayOfWeek())
                .sorted(Comparator.comparing(WorkingHours::getStartTime))
                .toList();
    }

    private static boolean overlaps(Appointment appointment, Instant start, Instant end) {
        return appointment.getStartAt().isBefore(end) && appointment.getEndAt().isAfter(start);
    }
}
