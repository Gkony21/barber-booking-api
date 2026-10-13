package gr.barber.booking.service;

import gr.barber.booking.config.BookingProperties;
import gr.barber.booking.dto.TimeOffRequestDTO;
import gr.barber.booking.dto.TimeOffResponseDTO;
import gr.barber.booking.exception.BadRequestException;
import gr.barber.booking.exception.ResourceNotFoundException;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.TimeOff;
import gr.barber.booking.repository.AppointmentRepository;
import gr.barber.booking.repository.TimeOffRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class TimeOffService {

    private final TimeOffRepository timeOffRepository;
    private final AppointmentRepository appointmentRepository;
    private final BarberService barberService;
    private final BookingProperties bookingProperties;
    private final Clock clock;

    public TimeOffService(TimeOffRepository timeOffRepository,
                          AppointmentRepository appointmentRepository,
                          BarberService barberService,
                          BookingProperties bookingProperties,
                          Clock clock) {
        this.timeOffRepository = timeOffRepository;
        this.appointmentRepository = appointmentRepository;
        this.barberService = barberService;
        this.bookingProperties = bookingProperties;
        this.clock = clock;
    }

    // Past periods are left out; they no longer affect bookings.
    @Transactional(readOnly = true)
    public List<TimeOffResponseDTO> getMyTimeOff(String email) {
        Barber barber = barberService.getByEmail(email);
        LocalDate today = LocalDate.now(clock.withZone(bookingProperties.timeZone()));
        return timeOffRepository.findAllByBarberIdAndEndDateGreaterThanEqualOrderByStartDate(barber.getId(), today)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Blocks new bookings in the period. Existing appointments are kept and reported back,
    // so the barber decides whom to cancel instead of customers being dropped silently.
    @Transactional
    public TimeOffResponseDTO addTimeOff(String email, TimeOffRequestDTO request) {

        if (request.endDate().isBefore(request.startDate())) {
            throw new BadRequestException("End date must not be before start date");
        }

        Barber barber = barberService.getByEmail(email);
        TimeOff timeOff = timeOffRepository.save(
                new TimeOff(barber, request.startDate(), request.endDate(), request.reason()));

        return toResponse(timeOff);
    }

    @Transactional
    public void deleteTimeOff(String email, Long timeOffId) {
        Barber barber = barberService.getByEmail(email);
        TimeOff timeOff = timeOffRepository.findByIdAndBarberId(timeOffId, barber.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Time off not found"));
        timeOffRepository.delete(timeOff);
    }

    private TimeOffResponseDTO toResponse(TimeOff timeOff) {
        ZoneId zone = bookingProperties.timeZone();
        int active = appointmentRepository.findActiveForBarberOverlapping(
                timeOff.getBarber().getId(),
                timeOff.getStartDate().atStartOfDay(zone).toInstant(),
                timeOff.getEndDate().plusDays(1).atStartOfDay(zone).toInstant()).size();

        return new TimeOffResponseDTO(timeOff.getId(), timeOff.getStartDate(), timeOff.getEndDate(),
                timeOff.getReason(), active);
    }
}
