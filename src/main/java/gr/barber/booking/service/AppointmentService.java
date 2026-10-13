package gr.barber.booking.service;

import gr.barber.booking.config.BookingProperties;
import gr.barber.booking.dto.AppointmentRequestDTO;
import gr.barber.booking.dto.AppointmentResponseDTO;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AppointmentService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentRepository appointmentRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final UserRepository userRepository;
    private final BarberService barberService;
    private final AvailabilityService availabilityService;
    private final BookingProperties bookingProperties;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              ServiceOfferingRepository serviceOfferingRepository,
                              UserRepository userRepository,
                              BarberService barberService,
                              AvailabilityService availabilityService,
                              BookingProperties bookingProperties,
                              Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.serviceOfferingRepository = serviceOfferingRepository;
        this.userRepository = userRepository;
        this.barberService = barberService;
        this.availabilityService = availabilityService;
        this.bookingProperties = bookingProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponseDTO getAvailability(Long barberId, Long serviceId, LocalDate date) {

        Barber barber = barberService.getApprovedById(barberId);
        ServiceOffering service = getActiveService(barber, serviceId);

        List<String> startTimes = availabilityService
                .findAvailableStartTimes(barber.getId(), date, service.getDurationMinutes()).stream()
                .map(TIME_FORMAT::format)
                .toList();

        return new AvailabilityResponseDTO(barber.getId(), service.getId(), date, service.getDurationMinutes(), startTimes);
    }

    @Transactional
    public AppointmentResponseDTO book(String customerEmail, AppointmentRequestDTO request) {

        User customer = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Barber barber = barberService.getApprovedById(request.barberId());
        ServiceOffering service = getActiveService(barber, request.serviceId());

        ZoneId zone = zone();
        int duration = service.getDurationMinutes();
        Instant startAt = request.startTime().atZone(zone).toInstant();
        Instant endAt = startAt.plusSeconds(duration * 60L);

        if (!startAt.isAfter(clock.instant())) {
            throw new BadRequestException("The appointment must be in the future");
        }
        if (!availabilityService.isWithinBookingWindow(request.startTime().toLocalDate())) {
            throw new BadRequestException(
                    "Appointments can be booked up to " + bookingProperties.maxDaysAhead() + " days ahead");
        }
        if (!availabilityService.fitsSchedule(barber.getId(), request.startTime().toLocalDate(),
                request.startTime().toLocalTime(), duration)) {
            throw new BadRequestException("The barber is not working at this time");
        }
        if (!appointmentRepository.findActiveForBarberOverlapping(barber.getId(), startAt, endAt).isEmpty()) {
            throw new ConflictException("This time is no longer available");
        }
        if (appointmentRepository.customerHasActiveOverlapping(customer.getId(), startAt, endAt)) {
            throw new ConflictException("You already have an appointment at this time");
        }

        AppointmentStatus status = barber.isAutoConfirmBookings()
                ? AppointmentStatus.CONFIRMED
                : AppointmentStatus.PENDING;

        Appointment appointment = new Appointment(customer, barber, service, startAt, endAt, status, request.note());

        try {
            appointment = appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            // Another customer booked an overlapping slot between our check and the insert.
            throw new ConflictException("This time is no longer available");
        }

        return AppointmentResponseDTO.from(appointment, zone);
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
            throw new BadRequestException("Appointments can be cancelled up to "
                    + bookingProperties.cancellationCutoff().toHours() + " hours before they start");
        }

        appointment.cancel(CancelledBy.CUSTOMER);
        return AppointmentResponseDTO.from(appointment, zone());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponseDTO> getBarberAppointments(String barberEmail, LocalDate from, LocalDate to) {

        Barber barber = barberService.getByEmail(barberEmail);
        ZoneId zone = zone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate start = from != null ? from : today;
        LocalDate end = to != null ? to : today.plusDays(bookingProperties.maxDaysAhead());

        if (end.isBefore(start)) {
            throw new BadRequestException("'to' must not be before 'from'");
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
        return AppointmentResponseDTO.from(appointment, zone());
    }

    @Transactional
    public AppointmentResponseDTO rejectByBarber(String barberEmail, Long appointmentId) {
        Appointment appointment = getBarberAppointment(barberEmail, appointmentId);
        requirePendingAndUpcoming(appointment);
        appointment.reject();
        return AppointmentResponseDTO.from(appointment, zone());
    }

    @Transactional
    public AppointmentResponseDTO cancelByBarber(String barberEmail, Long appointmentId) {
        Appointment appointment = getBarberAppointment(barberEmail, appointmentId);
        requireActiveAndUpcoming(appointment);
        appointment.cancel(CancelledBy.BARBER);
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
            throw new BadRequestException("Appointment is already " + appointment.getStatus());
        }
        if (!appointment.getStartAt().isAfter(clock.instant())) {
            throw new BadRequestException("Appointment has already started");
        }
    }

    private void requirePendingAndUpcoming(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new BadRequestException("Only pending appointments can be confirmed or rejected");
        }
        if (!appointment.getStartAt().isAfter(clock.instant())) {
            throw new BadRequestException("Appointment has already started");
        }
    }

    private ZoneId zone() {
        return bookingProperties.timeZone();
    }
}
