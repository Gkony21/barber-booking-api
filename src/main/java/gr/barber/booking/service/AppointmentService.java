package gr.barber.booking.service;

import gr.barber.booking.config.BookingProperties;
import gr.barber.booking.dto.AppointmentRequestDTO;
import gr.barber.booking.dto.AppointmentResponseDTO;
import gr.barber.booking.dto.ManualAppointmentRequestDTO;
import gr.barber.booking.dto.AvailabilityResponseDTO;
import gr.barber.booking.exception.BadRequestException;
import gr.barber.booking.exception.ConflictException;
import gr.barber.booking.exception.ResourceNotFoundException;
import gr.barber.booking.model.Appointment;
import gr.barber.booking.model.AppointmentStatus;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.CancelledBy;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
    public AvailabilityResponseDTO getAvailability(Long barberId, Long serviceId, LocalDate date) {

        Barber barber = barberService.getApprovedById(barberId);
        ServiceOffering service = getActiveService(barber, serviceId);

        List<String> startTimes = availabilityService
                .findAvailableStartTimes(barber, date, service.getDurationMinutes()).stream()
                .map(TIME_FORMAT::format)
                .toList();

        return new AvailabilityResponseDTO(barber.getId(), service.getId(), date, service.getDurationMinutes(), startTimes);
    }

    @Transactional
    public AppointmentResponseDTO book(String customerEmail, AppointmentRequestDTO request) {

        User customer = userRepository.findByEmailForUpdate(customerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Barber barber = barberService.getApprovedById(request.barberId());
        ServiceOffering service = getActiveService(barber, request.serviceId());

        ZoneId zone = zone();
        int duration = service.getDurationMinutes();
        Instant startAt = request.startTime().atZone(zone).toInstant();
        Instant endAt = startAt.plusSeconds(duration * 60L);

        if (!startAt.isAfter(clock.instant())) {
            throw new BadRequestException("APPOINTMENT_IN_PAST", "The appointment must be in the future");
        }
        if (!availabilityService.isWithinBookingWindow(barber, request.startTime().toLocalDate())) {
            throw new BadRequestException("TOO_FAR_AHEAD",
                    "This barber takes bookings up to " + barber.getBookingWindowDays() + " days ahead");
        }
        if (!availabilityService.fitsSchedule(barber, request.startTime().toLocalDate(),
                request.startTime().toLocalTime(), duration)) {
            throw new BadRequestException("BARBER_NOT_WORKING", "The barber is not working at this time");
        }
        if (!appointmentRepository.findActiveForBarberOverlapping(barber.getId(), startAt, endAt).isEmpty()) {
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

        Appointment appointment = new Appointment(customer, barber, service, startAt, endAt, status, request.note());

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
        ServiceOffering service = serviceOfferingRepository.findByIdAndBarberId(request.serviceId(), barber.getId())
                .filter(ServiceOffering::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        ZoneId zone = zone();
        Instant startAt = request.startTime().atZone(zone).toInstant();
        Instant endAt = startAt.plusSeconds(service.getDurationMinutes() * 60L);
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

        Appointment appointment = Appointment.manual(barber, service, startAt, endAt,
                request.customerName().strip(), blankToNull(request.customerPhone()), blankToNull(request.note()));
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
        User customer = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return appointmentRepository.findAllByCustomerIdOrderByStartAtDesc(customer.getId()).stream()
                .map(a -> AppointmentResponseDTO.from(a, zone()))
                .toList();
    }

    @Transactional
    public AppointmentResponseDTO cancelByCustomer(String customerEmail, Long appointmentId) {

        User customer = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Appointment appointment = appointmentRepository.findByIdAndCustomerId(appointmentId, customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        requireActiveAndUpcoming(appointment);

        // A pending request was never accepted, so it can be withdrawn at any time before it starts.
        Instant cutoff = appointment.getStartAt().minus(bookingProperties.cancellationCutoff());
        if (appointment.getStatus() == AppointmentStatus.CONFIRMED && clock.instant().isAfter(cutoff)) {
            throw new BadRequestException("CANCEL_TOO_LATE", "Appointments can be cancelled up to "
                    + bookingProperties.cancellationCutoff().toHours() + " hours before they start");
        }

        appointment.cancel(CancelledBy.CUSTOMER);
        publish(AppointmentEventType.CANCELLED_BY_CUSTOMER, appointment);
        return AppointmentResponseDTO.from(appointment, zone());
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

        return appointmentRepository.findAllByBarberIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAt(
                        barber.getId(),
                        start.atStartOfDay(zone).toInstant(),
                        end.plusDays(1).atStartOfDay(zone).toInstant()).stream()
                .map(a -> AppointmentResponseDTO.from(a, zone))
                .toList();
    }

    @Transactional
    public AppointmentResponseDTO confirmByBarber(String barberEmail, Long appointmentId) {
        Appointment appointment = getBarberAppointment(barberEmail, appointmentId);
        requirePendingAndUpcoming(appointment);
        appointment.confirm();
        publish(AppointmentEventType.CONFIRMED_BY_BARBER, appointment);
        return AppointmentResponseDTO.from(appointment, zone());
    }

    @Transactional
    public AppointmentResponseDTO rejectByBarber(String barberEmail, Long appointmentId) {
        Appointment appointment = getBarberAppointment(barberEmail, appointmentId);
        requirePendingAndUpcoming(appointment);
        appointment.reject();
        publish(AppointmentEventType.REJECTED_BY_BARBER, appointment);
        return AppointmentResponseDTO.from(appointment, zone());
    }

    @Transactional
    public AppointmentResponseDTO cancelByBarber(String barberEmail, Long appointmentId) {
        Appointment appointment = getBarberAppointment(barberEmail, appointmentId);
        requireActiveAndUpcoming(appointment);
        appointment.cancel(CancelledBy.BARBER);
        publish(AppointmentEventType.CANCELLED_BY_BARBER, appointment);
        return AppointmentResponseDTO.from(appointment, zone());
    }

    private Appointment getBarberAppointment(String barberEmail, Long appointmentId) {
        Barber barber = barberService.getByEmail(barberEmail);
        return appointmentRepository.findByIdAndBarberId(appointmentId, barber.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
    }

    private ServiceOffering getActiveService(Barber barber, Long serviceId) {
        return serviceOfferingRepository.findByIdAndBarberId(serviceId, barber.getId())
                .filter(ServiceOffering::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
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
