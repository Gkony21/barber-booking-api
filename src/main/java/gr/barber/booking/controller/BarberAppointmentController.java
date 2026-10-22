package gr.barber.booking.controller;

import gr.barber.booking.dto.AppointmentResponseDTO;
import gr.barber.booking.dto.AvailabilityResponseDTO;
import gr.barber.booking.dto.DayStatusDTO;
import gr.barber.booking.dto.ManualAppointmentRequestDTO;
import gr.barber.booking.dto.TimeOffRequestDTO;
import gr.barber.booking.dto.TimeOffResponseDTO;
import gr.barber.booking.service.AppointmentService;
import gr.barber.booking.service.TimeOffService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/barbers")
public class BarberAppointmentController {

    private final AppointmentService appointmentService;
    private final TimeOffService timeOffService;

    public BarberAppointmentController(AppointmentService appointmentService, TimeOffService timeOffService) {
        this.appointmentService = appointmentService;
        this.timeOffService = timeOffService;
    }

    @GetMapping("/{barberId}/availability")
    public AvailabilityResponseDTO getAvailability(@PathVariable Long barberId,
                                                   @RequestParam(required = false) Long serviceId,
                                                   @RequestParam(required = false) List<Long> extraIds,
                                                   // Instead of services: an existing appointment's length, when moving it.
                                                   @RequestParam(required = false) Integer minutes,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return minutes != null
                ? appointmentService.getAvailabilityFor(barberId, minutes, date)
                : appointmentService.getAvailability(barberId, serviceId, extraIds, date);
    }

    // Which days are open, full or closed, so the app can grey out closed days.
    @GetMapping("/{barberId}/days")
    public List<DayStatusDTO> getDays(@PathVariable Long barberId,
                                      @RequestParam(required = false) Long serviceId,
                                      @RequestParam(required = false) List<Long> extraIds,
                                      @RequestParam(required = false) Integer minutes,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return appointmentService.getDays(barberId, serviceId, extraIds, minutes, from, to);
    }

    // The barber's own free times within working hours, for entering a booking by hand.
    @GetMapping("/me/availability")
    public AvailabilityResponseDTO getMyAvailability(@RequestParam int minutes,
                                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                     Authentication authentication) {
        return appointmentService.getOwnAvailability(authentication.getName(), minutes, date);
    }

    @GetMapping("/me/appointments")
    public List<AppointmentResponseDTO> getMyAppointments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication) {
        return appointmentService.getBarberAppointments(authentication.getName(), from, to);
    }

    @PostMapping("/me/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponseDTO bookManually(@Valid @RequestBody ManualAppointmentRequestDTO request,
                                               Authentication authentication) {
        return appointmentService.bookManually(authentication.getName(), request);
    }

    @PostMapping("/me/appointments/{id}/confirm")
    public AppointmentResponseDTO confirm(@PathVariable Long id, Authentication authentication) {
        return appointmentService.confirmByBarber(authentication.getName(), id);
    }

    @PostMapping("/me/appointments/{id}/reject")
    public AppointmentResponseDTO reject(@PathVariable Long id, Authentication authentication) {
        return appointmentService.rejectByBarber(authentication.getName(), id);
    }

    @PostMapping("/me/appointments/{id}/no-show")
    public AppointmentResponseDTO markNoShow(@PathVariable Long id, Authentication authentication) {
        return appointmentService.markNoShow(authentication.getName(), id);
    }

    @PostMapping("/me/appointments/{id}/cancel")
    public AppointmentResponseDTO cancel(@PathVariable Long id, Authentication authentication) {
        return appointmentService.cancelByBarber(authentication.getName(), id);
    }

    @GetMapping("/me/time-off")
    public List<TimeOffResponseDTO> getMyTimeOff(Authentication authentication) {
        return timeOffService.getMyTimeOff(authentication.getName());
    }

    @PostMapping("/me/time-off")
    @ResponseStatus(HttpStatus.CREATED)
    public TimeOffResponseDTO addTimeOff(@Valid @RequestBody TimeOffRequestDTO request,
                                         Authentication authentication) {
        return timeOffService.addTimeOff(authentication.getName(), request);
    }

    @DeleteMapping("/me/time-off/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTimeOff(@PathVariable Long id, Authentication authentication) {
        timeOffService.deleteTimeOff(authentication.getName(), id);
    }

}
