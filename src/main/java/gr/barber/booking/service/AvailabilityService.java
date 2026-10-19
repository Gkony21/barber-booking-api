package gr.barber.booking.service;

import gr.barber.booking.config.BookingProperties;
import gr.barber.booking.model.Appointment;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.TimeOff;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Works out when a barber can take an appointment: inside working hours, not on a day off,
 * within the barber's booking window, and not overlapping another active appointment.
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

    /** Everything that decides one barber's free times over a period, loaded up front. */
    private record Schedule(List<WorkingHours> shifts, List<TimeOff> timeOff, List<Appointment> booked) {

        static final Schedule EMPTY = new Schedule(List.of(), List.of(), List.of());

        boolean isOff(LocalDate date) {
            return timeOff.stream().anyMatch(t -> !date.isBefore(t.getStartDate()) && !date.isAfter(t.getEndDate()));
        }

        List<WorkingHours> shiftsOn(LocalDate date) {
            return shifts.stream()
                    .filter(shift -> shift.getDayOfWeek() == date.getDayOfWeek())
                    .sorted(Comparator.comparing(WorkingHours::getStartTime))
                    .toList();
        }
    }

    @Transactional(readOnly = true)
    public List<LocalTime> findAvailableStartTimes(Barber barber, LocalDate date, int durationMinutes) {
        if (!isWithinBookingWindow(barber, date)) {
            return List.of();
        }
        Schedule schedule = load(List.of(barber.getId()), date, date).getOrDefault(barber.getId(), Schedule.EMPTY);
        return startTimes(barber, date, durationMinutes, schedule, clock.instant());
    }

    /**
     * The first free start of each barber for an appointment of the given length (e.g. their shortest
     * service), within their booking window; barbers with none are left out. Loads the data of all
     * barbers in a few queries, so the barber list stays fast however many barbers there are.
     */
    @Transactional(readOnly = true)
    public Map<Long, OffsetDateTime> nextAvailable(Collection<Barber> barbers, Map<Long, Integer> durationByBarber) {
        List<Barber> bookable = barbers.stream().filter(b -> durationByBarber.containsKey(b.getId())).toList();
        if (bookable.isEmpty()) {
            return Map.of();
        }
        ZoneId zone = bookingProperties.timeZone();
        Instant now = clock.instant();
        LocalDate today = today();
        int longestWindow = bookable.stream().mapToInt(Barber::getBookingWindowDays).max().orElse(0);
        Map<Long, Schedule> schedules = load(bookable.stream().map(Barber::getId).toList(), today, today.plusDays(longestWindow));

        Map<Long, OffsetDateTime> result = new HashMap<>();
        for (Barber barber : bookable) {
            Schedule schedule = schedules.getOrDefault(barber.getId(), Schedule.EMPTY);
            for (int day = 0; day <= barber.getBookingWindowDays(); day++) {
                LocalDate date = today.plusDays(day);
                List<LocalTime> starts = startTimes(barber, date, durationByBarber.get(barber.getId()), schedule, now);
                if (!starts.isEmpty()) {
                    result.put(barber.getId(), date.atTime(starts.getFirst()).atZone(zone).toOffsetDateTime());
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Checks everything except overlaps with other appointments, which the caller checks
     * (and the database enforces) separately.
     */
    @Transactional(readOnly = true)
    public boolean fitsSchedule(Barber barber, LocalDate date, LocalTime start, int durationMinutes) {

        if (!isWithinBookingWindow(barber, date) || timeOffRepository.isOff(barber.getId(), date)) {
            return false;
        }

        LocalTime end = start.plusMinutes(durationMinutes);
        if (!end.isAfter(start)) {
            return false; // would run past midnight
        }

        int step = barber.getSlotStepMinutes();
        Schedule schedule = new Schedule(workingHoursRepository.findAllByBarberId(barber.getId()), List.of(), List.of());
        // Starts must fall on the barber's step from the shift start, as offered to customers.
        return schedule.shiftsOn(date).stream()
                .anyMatch(shift -> !start.isBefore(shift.getStartTime())
                        && !end.isAfter(shift.getEndTime())
                        && Duration.between(shift.getStartTime(), start).toMinutes() % step == 0);
    }

    public boolean isWithinBookingWindow(Barber barber, LocalDate date) {
        LocalDate today = today();
        return !date.isBefore(today) && !date.isAfter(today.plusDays(barber.getBookingWindowDays()));
    }

    private List<LocalTime> startTimes(Barber barber, LocalDate date, int durationMinutes, Schedule schedule, Instant now) {
        if (schedule.isOff(date)) {
            return List.of();
        }
        ZoneId zone = bookingProperties.timeZone();
        int step = barber.getSlotStepMinutes();
        Instant earliest = now.plus(barber.bookingNotice());
        List<LocalTime> available = new ArrayList<>();

        for (WorkingHours shift : schedule.shiftsOn(date)) {
            LocalTime start = shift.getStartTime();
            while (!start.plusMinutes(durationMinutes).isAfter(shift.getEndTime())
                    && start.plusMinutes(durationMinutes).isAfter(start)) {

                Instant slotStart = date.atTime(start).atZone(zone).toInstant();
                Instant slotEnd = slotStart.plusSeconds(durationMinutes * 60L);

                if (!slotStart.isBefore(earliest) && schedule.booked().stream().noneMatch(a -> overlaps(a, slotStart, slotEnd))) {
                    available.add(start);
                }

                LocalTime next = start.plusMinutes(step);
                if (!next.isAfter(start)) {
                    break; // wrapped past midnight
                }
                start = next;
            }
        }

        return available;
    }

    // Three queries for any number of barbers and days.
    private Map<Long, Schedule> load(List<Long> barberIds, LocalDate from, LocalDate to) {
        ZoneId zone = bookingProperties.timeZone();
        Map<Long, List<WorkingHours>> shifts = workingHoursRepository.findAllByBarberIdIn(barberIds).stream()
                .collect(Collectors.groupingBy(w -> w.getBarber().getId()));
        Map<Long, List<TimeOff>> timeOff = timeOffRepository.findOverlapping(barberIds, from, to).stream()
                .collect(Collectors.groupingBy(t -> t.getBarber().getId()));
        Map<Long, List<Appointment>> booked = appointmentRepository.findActiveForBarbersOverlapping(barberIds,
                        from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant()).stream()
                .collect(Collectors.groupingBy(a -> a.getBarber().getId()));

        Map<Long, Schedule> schedules = new HashMap<>();
        for (Long id : barberIds) {
            schedules.put(id, new Schedule(shifts.getOrDefault(id, List.of()), timeOff.getOrDefault(id, List.of()),
                    booked.getOrDefault(id, List.of())));
        }
        return schedules;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(bookingProperties.timeZone()));
    }

    private static boolean overlaps(Appointment appointment, Instant start, Instant end) {
        return appointment.getStartAt().isBefore(end) && appointment.getEndAt().isAfter(start);
    }
}
