package gr.barber.booking.service;

import gr.barber.booking.config.BookingProperties;
import gr.barber.booking.dto.AppointmentRequestDTO;
import gr.barber.booking.dto.AppointmentResponseDTO;
import gr.barber.booking.dto.ManualAppointmentRequestDTO;
import gr.barber.booking.dto.AvailabilityResponseDTO;
import gr.barber.booking.dto.DayStatusDTO;
import gr.barber.booking.exception.BadRequestException;
import gr.barber.booking.exception.ConflictException;
import gr.barber.booking.exception.ResourceNotFoundException;
import gr.barber.booking.model.Appointment;
import gr.barber.booking.model.AppointmentStatus;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.CancelledBy;
import gr.barber.booking.model.DayStatus;
import gr.barber.booking.model.ServiceOffering;
import gr.barber.booking.model.User;
import gr.barber.booking.repository.AppointmentRepository;
import gr.barber.booking.repository.ServiceOfferingRepository;
import gr.barber.booking.repository.UserRepository;
import gr.barber.booking.notification.AppointmentEvent;
import gr.barber.booking.notification.AppointmentEventType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Duration MANUAL_BOOKING_WINDOW = Duration.ofDays(90);

    private final AppointmentRepository appointmentRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final UserRepository userRepository;
    private final BarberService barberService;
    private final AvailabilityService availabilityService;
    private final BookingProperties bookingProperties;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              ServiceOfferingRepository serviceOfferingRepository,
                              UserRepository userRepository,
                              BarberService barberService,
                              AvailabilityService availabilityService,
                              BookingProperties bookingProperties,
                              Clock clock,
                              ApplicationEventPublisher eventPublisher) {
        this.appointmentRepository = appointmentRepository;
        this.serviceOfferingRepository = serviceOfferingRepository;
        this.userRepository = userRepository;
        this.barberService = barberService;
        this.availabilityService = availabilityService;
        this.bookingProperties = bookingProperties;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponseDTO getAvailability(Long barberId, Long serviceId, List<Long> extraIds, LocalDate date) {

        Barber barber = barberService.getApprovedById(barberId);
        Selection selection = select(barber, serviceId, extraIds);

        List<String> startTimes = availabilityService
                .findAvailableStartTimes(barber, date, selection.durationMinutes()).stream()
                .map(TIME_FORMAT::format)
                .toList();

        return new AvailabilityResponseDTO(barber.getId(), serviceId, date, selection.durationMinutes(), startTimes);
    }

    /**
     * Open, full or closed for each day from..to, for the chosen services (or a length, when moving an
     * appointment). At most about two months at a time.
     */
    @Transactional(readOnly = true)
    public List<DayStatusDTO> getDays(Long barberId, Long serviceId, List<Long> extraIds, Integer minutes,
                                      LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(62).isBefore(to)) {
            throw new BadRequestException("INVALID_DATE_RANGE", "Ask for up to 62 days, 'from' before 'to'");
        }
        Barber barber = barberService.getApprovedById(barberId);
        int length = minutes != null ? checkedLength(minutes) : select(barber, serviceId, extraIds).durationMinutes();
        return toDays(availabilityService.dayStatuses(barber, from, to, length, List.of()));
    }

    /** Open, full or closed days for moving the customer's appointment; its own time counts as free. */
    @Transactional(readOnly = true)
    public List<DayStatusDTO> getMoveDays(String customerEmail, Long appointmentId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(62).isBefore(to)) {
            throw new BadRequestException("INVALID_DATE_RANGE", "Ask for up to 62 days, 'from' before 'to'");
        }
        Appointment appointment = customerAppointment(findCustomer(customerEmail), appointmentId);
        return toDays(availabilityService.dayStatuses(appointment.getBarber(), from, to, lengthOf(appointment),
                withChange(appointment)));
    }

    private static List<DayStatusDTO> toDays(Map<LocalDate, DayStatus> statuses) {
        return statuses.entrySet().stream()
                .map(day -> new DayStatusDTO(day.getKey(), day.getValue()))
                .toList();
    }

    /** The barber's own free quarter hours within working hours, for entering a phone or walk-in booking. */
    @Transactional(readOnly = true)
    public AvailabilityResponseDTO getOwnAvailability(String barberEmail, int minutes, LocalDate date) {
        Barber barber = barberService.getByEmail(barberEmail);
        List<String> startTimes = availabilityService.freeStartsForBarber(barber, date, checkedLength(minutes)).stream()
                .map(TIME_FORMAT::format)
                .toList();
        return new AvailabilityResponseDTO(barber.getId(), null, date, minutes, startTimes);
    }

    private static int checkedLength(int minutes) {
        if (minutes < 5 || minutes > 600) {
            throw new BadRequestException("VALIDATION_FAILED", "Length must be between 5 and 600 minutes");
        }
        return minutes;
    }

    /** Free starts for an appointment of a given length, e.g. one being moved to another time. */
    @Transactional(readOnly = true)
    public AvailabilityResponseDTO getAvailabilityFor(Long barberId, int minutes, LocalDate date) {
        Barber barber = barberService.getApprovedById(barberId);
        List<String> startTimes = availabilityService.findAvailableStartTimes(barber, date, checkedLength(minutes)).stream()
                .map(TIME_FORMAT::format)
                .toList();
        return new AvailabilityResponseDTO(barber.getId(), null, date, minutes, startTimes);
    }

    /** Free starts for moving the customer's appointment; its own time (and any change of it) counts as free. */
    @Transactional(readOnly = true)
    public AvailabilityResponseDTO getMoveAvailability(String customerEmail, Long appointmentId, LocalDate date) {
        Appointment appointment = customerAppointment(findCustomer(customerEmail), appointmentId);
        Barber barber = appointment.getBarber();
        int minutes = lengthOf(appointment);
        List<String> startTimes = availabilityService.findAvailableStartTimes(barber, date, minutes, withChange(appointment))
                .stream()
                .map(TIME_FORMAT::format)
                .toList();
        return new AvailabilityResponseDTO(barber.getId(), null, date, minutes, startTimes);
    }

    @Transactional
    public AppointmentResponseDTO book(String customerEmail, AppointmentRequestDTO request) {

        User customer = userRepository.findByEmailForUpdate(customerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (customer.getPhoneNumber() == null || customer.getPhoneNumber().isBlank()) {
            // The barber must be able to reach whoever booked; older accounts may not have a number.
            throw new BadRequestException("PHONE_REQUIRED", "Add a mobile number to your account before booking");
        }
        Barber barber = barberService.getApprovedById(request.barberId());
        Selection selection = select(barber, request.serviceId(), request.extraIds());

        ZoneId zone = zone();
        int duration = selection.durationMinutes();
        Instant startAt = request.startTime().atZone(zone).toInstant();
        Instant endAt = startAt.plusSeconds(duration * 60L);

        if (!startAt.isAfter(clock.instant())) {
            throw new BadRequestException("APPOINTMENT_IN_PAST", "The appointment must be in the future");
        }
        if (startAt.isBefore(clock.instant().plus(barber.bookingNotice()))) {
            throw new BadRequestException("TOO_SOON",
                    "This barber takes bookings at least " + barber.bookingNotice().toMinutes() + " minutes ahead");
        }
        if (!availabilityService.isWithinBookingWindow(barber, request.startTime().toLocalDate())) {
            throw new BadRequestException("TOO_FAR_AHEAD",
                    "This barber takes bookings up to " + barber.getBookingWindowDays() + " days ahead");
        }
        if (!availabilityService.fitsSchedule(barber, request.startTime().toLocalDate(),
                request.startTime().toLocalTime(), duration)) {
            throw new BadRequestException("BARBER_NOT_WORKING", "The barber is not working at this time");
        }
        // The barber's break between appointments counts as taken.
        if (!appointmentRepository.findActiveForBarberOverlapping(barber.getId(),
                startAt.minus(barber.buffer()), endAt.plus(barber.buffer())).isEmpty()) {
            throw new ConflictException("SLOT_TAKEN", "This time is no longer available");
        }
        if (appointmentRepository.customerHasActiveOverlapping(customer.getId(), startAt, endAt)) {
            throw new ConflictException("CUSTOMER_OVERLAP", "You already have an appointment at this time");
        }
        // Once an appointment has started it no longer counts, so a new one can be booked.
        if (appointmentRepository.countUpcomingActiveForCustomer(customer.getId(), clock.instant())
                >= bookingProperties.maxActivePerCustomer()) {
            throw new ConflictException("BOOKING_LIMIT",
                    "You can have up to " + bookingProperties.maxActivePerCustomer() + " upcoming appointments");
        }

        AppointmentStatus status = barber.isAutoConfirmBookings()
                ? AppointmentStatus.CONFIRMED
                : AppointmentStatus.PENDING;

        Appointment appointment = new Appointment(customer, barber, selection.main(), startAt, endAt, status, request.note());
        appointment.addExtras(selection.extras());

        try {
            appointment = appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            // Another customer booked an overlapping slot between our check and the insert.
            throw new ConflictException("SLOT_TAKEN", "This time is no longer available");
        }

        publish(status == AppointmentStatus.CONFIRMED
                ? AppointmentEventType.BOOKED_CONFIRMED
                : AppointmentEventType.BOOKED_PENDING, appointment);

        return AppointmentResponseDTO.from(appointment, zone);
    }

    /**
     * The barber's own entry for a phone or walk-in customer. Working hours, days off and the
     * start-time step are the barber's call here; only overlaps with other appointments are refused.
     */
    @Transactional
    public AppointmentResponseDTO bookManually(String barberEmail, ManualAppointmentRequestDTO request) {

        Barber barber = barberService.getByEmail(barberEmail);
        Selection selection = select(barber, request.serviceId(), request.extraIds());

        ZoneId zone = zone();
        Instant startAt = request.startTime().atZone(zone).toInstant();
        Instant endAt = startAt.plusSeconds(selection.durationMinutes() * 60L);
        Instant now = clock.instant();

        // A walk-in may be logged just after they sat down, but not long after.
        if (startAt.isBefore(now.minus(Duration.ofMinutes(30)))) {
            throw new BadRequestException("APPOINTMENT_IN_PAST", "The appointment must not be in the past");
        }
        if (startAt.isAfter(now.plus(MANUAL_BOOKING_WINDOW))) {
            throw new BadRequestException("TOO_FAR_AHEAD", "Appointments can be entered up to 90 days ahead");
        }
        if (!appointmentRepository.findActiveForBarberOverlapping(barber.getId(), startAt, endAt).isEmpty()) {
            throw new ConflictException("OVERLAPS_APPOINTMENT", "This time overlaps another appointment");
        }

        Appointment appointment = Appointment.manual(barber, selection.main(), startAt, endAt,
                request.customerName().strip(), blankToNull(request.customerPhone()), blankToNull(request.note()));
        appointment.addExtras(selection.extras());
        try {
            appointment = appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("OVERLAPS_APPOINTMENT", "This time overlaps another appointment");
        }
        return AppointmentResponseDTO.from(appointment, zone);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponseDTO> getCustomerAppointments(String customerEmail) {
        User customer = findCustomer(customerEmail);
        List<Appointment> appointments = appointmentRepository.findAllByCustomerIdOrderByStartAtDesc(customer.getId());
        // A change of time shows on the appointment it would move, not as an appointment of its own.
        Map<Long, Appointment> changes = appointments.stream().filter(Appointment::isChange)
                .collect(Collectors.toMap(a -> a.getReplaces().getId(), a -> a));
        return appointments.stream()
                .filter(a -> !a.isChange())
                .map(a -> AppointmentResponseDTO.from(a, zone(), 0, changes.get(a.getId())))
                .toList();
    }

    @Transactional
    public AppointmentResponseDTO cancelByCustomer(String customerEmail, Long appointmentId) {

        Appointment appointment = customerAppointment(findCustomer(customerEmail), appointmentId);
        requireActiveAndUpcoming(appointment);

        // A pending request was never accepted, so it can be withdrawn at any time before it starts.
        Instant cutoff = appointment.getStartAt().minus(bookingProperties.cancellationCutoff());
        if (appointment.getStatus() == AppointmentStatus.CONFIRMED && clock.instant().isAfter(cutoff)) {
            throw new BadRequestException("CANCEL_TOO_LATE", "Appointments can be cancelled up to "
                    + bookingProperties.cancellationCutoff().toHours() + " hours before they start");
        }

        dropChange(appointment); // the cancellation email covers it
        appointment.cancel(CancelledBy.CUSTOMER);
        publish(AppointmentEventType.CANCELLED_BY_CUSTOMER, appointment);
        return AppointmentResponseDTO.from(appointment, zone());
    }

    /**
     * Moves the customer's appointment to another time of the same barber, with the same services,
     * while it could still be cancelled. A request simply moves, and so does a confirmed appointment
     * of a barber who confirms automatically. Otherwise the confirmed appointment keeps its time and
     * the new one waits for the barber as a change; asking again moves that change.
     */
    @Transactional
    public AppointmentResponseDTO rescheduleByCustomer(String customerEmail, Long appointmentId, LocalDateTime newStart) {

        User customer = userRepository.findByEmailForUpdate(customerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Appointment appointment = customerAppointment(customer, appointmentId);
        requireActiveAndUpcoming(appointment);

        Instant now = clock.instant();
        if (appointment.getStatus() == AppointmentStatus.CONFIRMED
                && now.isAfter(appointment.getStartAt().minus(bookingProperties.cancellationCutoff()))) {
            throw new BadRequestException("CHANGE_TOO_LATE", "Appointments can be changed up to "
                    + bookingProperties.cancellationCutoff().toHours() + " hours before they start");
        }

        Barber barber = appointment.getBarber();
        ZoneId zone = zone();
        Duration length = Duration.between(appointment.getStartAt(), appointment.getEndAt());
        Instant startAt = newStart.atZone(zone).toInstant();
        Instant endAt = startAt.plus(length);
        Appointment change = appointmentRepository.findByReplacesId(appointment.getId()).orElse(null);

        if (startAt.equals(appointment.getStartAt())) {
            // Back to the time it already has: nothing left to ask the barber.
            if (change != null) {
                withdraw(change);
            }
            return AppointmentResponseDTO.from(appointment, zone);
        }
        if (startAt.isBefore(now.plus(barber.bookingNotice()))) {
            throw new BadRequestException("TOO_SOON",
                    "This barber takes bookings at least " + barber.bookingNotice().toMinutes() + " minutes ahead");
        }
        if (!availabilityService.isWithinBookingWindow(barber, newStart.toLocalDate())) {
            throw new BadRequestException("TOO_FAR_AHEAD",
                    "This barber takes bookings up to " + barber.getBookingWindowDays() + " days ahead");
        }
        if (!availabilityService.fitsSchedule(barber, newStart.toLocalDate(), newStart.toLocalTime(), (int) length.toMinutes())) {
            throw new BadRequestException("BARBER_NOT_WORKING", "The barber is not working at this time");
        }
        List<Long> own = withChange(appointment);
        boolean taken = appointmentRepository.findActiveForBarberOverlapping(barber.getId(),
                        startAt.minus(barber.buffer()), endAt.plus(barber.buffer())).stream()
                .anyMatch(other -> !own.contains(other.getId()));
        if (taken) {
            throw new ConflictException("SLOT_TAKEN", "This time is no longer available");
        }
        if (appointmentRepository.customerHasOtherActiveOverlapping(customer.getId(), appointment.getId(), startAt, endAt)) {
            throw new ConflictException("CUSTOMER_OVERLAP", "You already have an appointment at this time");
        }

        if (appointment.getStatus() == AppointmentStatus.CONFIRMED && !barber.isAutoConfirmBookings()) {
            if (change == null) {
                change = Appointment.changeOf(appointment, startAt, endAt);
            } else {
                change.moveTo(startAt, endAt, AppointmentStatus.PENDING);
            }
            change = saveOrSlotTaken(change);
            publish(AppointmentEventType.CHANGE_REQUESTED, change);
            return AppointmentResponseDTO.from(appointment, zone, 0, change);
        }

        if (change != null) {
            // Asked before the barber switched to confirming automatically: the move below replaces it.
            appointmentRepository.delete(change);
            appointmentRepository.flush();
        }
        AppointmentStatus status = barber.isAutoConfirmBookings() ? AppointmentStatus.CONFIRMED : AppointmentStatus.PENDING;
        appointment.moveTo(startAt, endAt, status);
        saveOrSlotTaken(appointment);
        publish(AppointmentEventType.RESCHEDULED_BY_CUSTOMER, appointment);
        return AppointmentResponseDTO.from(appointment, zone);
    }

    /** Takes back a change of time the barber has not answered yet; the appointment keeps its time. */
    @Transactional
    public AppointmentResponseDTO withdrawChange(String customerEmail, Long appointmentId) {
        Appointment appointment = customerAppointment(findCustomer(customerEmail), appointmentId);
        Appointment change = appointmentRepository.findByReplacesId(appointment.getId())
                .orElseThrow(() -> new BadRequestException("NO_CHANGE", "There is no change of time to withdraw"));
        withdraw(change);
        return AppointmentResponseDTO.from(appointment, zone());
    }

    private void withdraw(Appointment change) {
        publish(AppointmentEventType.CHANGE_WITHDRAWN, change);
        appointmentRepository.delete(change);
    }

    private Appointment saveOrSlotTaken(Appointment appointment) {
        try {
            return appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("SLOT_TAKEN", "This time is no longer available");
        }
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponseDTO> getBarberAppointments(String barberEmail, LocalDate from, LocalDate to) {

        Barber barber = barberService.getByEmail(barberEmail);
        ZoneId zone = zone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate start = from != null ? from : today;
        LocalDate end = to != null ? to : today.plusDays(barber.getBookingWindowDays());

        if (end.isBefore(start)) {
            throw new BadRequestException("INVALID_DATE_RANGE", "'to' must not be before 'from'");
        }

        List<Appointment> appointments = appointmentRepository.findAllByBarberIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAt(
                barber.getId(),
                start.atStartOfDay(zone).toInstant(),
                end.plusDays(1).atStartOfDay(zone).toInstant());

        // How often each customer did not turn up anywhere, so the barber can call to confirm.
        List<Long> customerIds = appointments.stream()
                .filter(a -> a.getCustomer() != null).map(a -> a.getCustomer().getId()).distinct().toList();
        Map<Long, Long> noShows = customerIds.isEmpty() ? Map.of() : appointmentRepository.countNoShows(customerIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        // Changes of time asked for these appointments, which may fall on days outside the range.
        List<Long> originalIds = appointments.stream().filter(a -> !a.isChange()).map(Appointment::getId).toList();
        Map<Long, Appointment> changes = originalIds.isEmpty() ? Map.of() : appointmentRepository.findAllByReplacesIdIn(originalIds)
                .stream().collect(Collectors.toMap(c -> c.getReplaces().getId(), c -> c));

        return appointments.stream()
                .map(a -> AppointmentResponseDTO.from(a, zone,
                        a.getCustomer() == null ? 0 : noShows.getOrDefault(a.getCustomer().getId(), 0L).intValue(),
                        changes.get(a.getId())))
                .toList();
    }

    @Transactional
    public AppointmentResponseDTO confirmByBarber(String barberEmail, Long appointmentId) {
        Appointment appointment = getBarberAppointment(barberEmail, appointmentId);
        requirePendingAndUpcoming(appointment);
        if (appointment.isChange()) {
            return acceptChange(appointment);
        }
        appointment.confirm();
        publish(AppointmentEventType.CONFIRMED_BY_BARBER, appointment);
        return AppointmentResponseDTO.from(appointment, zone());
    }

    // The confirmed appointment moves to the new time, and the change is no longer needed.
    private AppointmentResponseDTO acceptChange(Appointment change) {
        Appointment original = change.getReplaces();
        requireActiveAndUpcoming(original);
        publish(AppointmentEventType.CHANGE_ACCEPTED, change);
        Instant startAt = change.getStartAt();
        Instant endAt = change.getEndAt();
        appointmentRepository.delete(change);
        appointmentRepository.flush();
        original.moveTo(startAt, endAt, AppointmentStatus.CONFIRMED);
        return AppointmentResponseDTO.from(original, zone());
    }

    // The confirmed appointment keeps its time.
    private AppointmentResponseDTO declineChange(Appointment change) {
        Appointment original = change.getReplaces();
        publish(AppointmentEventType.CHANGE_DECLINED, change);
        appointmentRepository.delete(change);
        return AppointmentResponseDTO.from(original, zone());
    }

    @Transactional
    public AppointmentResponseDTO rejectByBarber(String barberEmail, Long appointmentId) {
        Appointment appointment = getBarberAppointment(barberEmail, appointmentId);
        requirePendingAndUpcoming(appointment);
        if (appointment.isChange()) {
            return declineChange(appointment);
        }
        appointment.reject();
        publish(AppointmentEventType.REJECTED_BY_BARBER, appointment);
        return AppointmentResponseDTO.from(appointment, zone());
    }

    // Only once the appointment has started: before that the customer may still come.
    @Transactional
    public AppointmentResponseDTO markNoShow(String barberEmail, Long appointmentId) {
        Appointment appointment = getBarberAppointment(barberEmail, appointmentId);
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED || appointment.isManual()) {
            throw new BadRequestException("APPOINTMENT_NOT_ACTIVE", "Only confirmed online bookings can be marked");
        }
        if (appointment.getStartAt().isAfter(clock.instant())) {
            throw new BadRequestException("NOT_STARTED_YET", "The appointment has not started yet");
        }
        appointment.markNoShow();
        return AppointmentResponseDTO.from(appointment, zone());
    }

    @Transactional
    public AppointmentResponseDTO cancelByBarber(String barberEmail, Long appointmentId) {
        Appointment appointment = getBarberAppointment(barberEmail, appointmentId);
        requireActiveAndUpcoming(appointment);
        if (appointment.isChange()) {
            return declineChange(appointment);
        }
        dropChange(appointment);
        appointment.cancel(CancelledBy.BARBER);
        publish(AppointmentEventType.CANCELLED_BY_BARBER, appointment);
        return AppointmentResponseDTO.from(appointment, zone());
    }

    private Appointment getBarberAppointment(String barberEmail, Long appointmentId) {
        Barber barber = barberService.getByEmail(barberEmail);
        return appointmentRepository.findByIdAndBarberId(appointmentId, barber.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
    }

    private User findCustomer(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    // The customer's appointment; a change of time stands for the appointment it would move.
    private Appointment customerAppointment(User customer, Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdAndCustomerId(appointmentId, customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
        return appointment.isChange() ? appointment.getReplaces() : appointment;
    }

    // The appointment and its change of time, if one is waiting.
    private List<Long> withChange(Appointment appointment) {
        return appointmentRepository.findByReplacesId(appointment.getId())
                .map(change -> List.of(appointment.getId(), change.getId()))
                .orElse(List.of(appointment.getId()));
    }

    private void dropChange(Appointment appointment) {
        appointmentRepository.findByReplacesId(appointment.getId()).ifPresent(appointmentRepository::delete);
    }

    private static int lengthOf(Appointment appointment) {
        return (int) Duration.between(appointment.getStartAt(), appointment.getEndAt()).toMinutes();
    }

    /** One main service plus any extras; the appointment lasts as long as all of them together. */
    private record Selection(ServiceOffering main, List<ServiceOffering> extras) {
        int durationMinutes() {
            return main.getDurationMinutes() + extras.stream().mapToInt(ServiceOffering::getDurationMinutes).sum();
        }
    }

    // Both must be the barber's own active services, of the right kind; extras keep the given order.
    private Selection select(Barber barber, Long serviceId, List<Long> extraIds) {
        if (serviceId == null) {
            throw new BadRequestException("VALIDATION_FAILED", "Choose a service (serviceId) or a length (minutes)");
        }
        ServiceOffering main = serviceOfferingRepository.findByIdAndBarberId(serviceId, barber.getId())
                .filter(ServiceOffering::isActive)
                .filter(ServiceOffering::isMain)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        List<Long> ids = extraIds == null ? List.of() : extraIds;
        Map<Long, ServiceOffering> available = serviceOfferingRepository.findAllById(ids).stream()
                .filter(s -> s.getBarber().getId().equals(barber.getId()) && s.isActive() && !s.isMain())
                .collect(Collectors.toMap(ServiceOffering::getId, s -> s));
        if (available.size() != ids.size() || ids.stream().distinct().count() != ids.size()) {
            throw new BadRequestException("INVALID_EXTRA", "One of the extras is not available");
        }
        return new Selection(main, ids.stream().map(available::get).toList());
    }

    private void requireActiveAndUpcoming(Appointment appointment) {
        if (!appointment.getStatus().isActive()) {
            throw new BadRequestException("APPOINTMENT_NOT_ACTIVE", "Appointment is already " + appointment.getStatus());
        }
        if (!appointment.getStartAt().isAfter(clock.instant())) {
            throw new BadRequestException("APPOINTMENT_STARTED", "Appointment has already started");
        }
    }

    private void requirePendingAndUpcoming(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new BadRequestException("APPOINTMENT_NOT_PENDING", "Only pending appointments can be confirmed or rejected");
        }
        if (!appointment.getStartAt().isAfter(clock.instant())) {
            throw new BadRequestException("APPOINTMENT_STARTED", "Appointment has already started");
        }
    }

    private void publish(AppointmentEventType type, Appointment appointment) {
        eventPublisher.publishEvent(AppointmentEvent.of(type, appointment, zone()));
    }

    private ZoneId zone() {
        return bookingProperties.timeZone();
    }
}
