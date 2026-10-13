package gr.barber.booking.controller;

import gr.barber.booking.dto.AppointmentResponseDTO;
import gr.barber.booking.dto.AvailabilityResponseDTO;
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
                                                   @RequestParam Long serviceId,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return appointmentService.getAvailability(barberId, serviceId, date);
    }

    @GetMapping("/me/appointments")
    public List<AppointmentResponseDTO> getMyAppointments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication) {
        return appointmentService.getBarberAppointments(authentication.getName(), from, to);
    }

    @PostMapping("/me/appointments/{id}/confirm")
    public AppointmentResponseDTO confirm(@PathVariable Long id, Authentication authentication) {
        return appointmentService.confirmByBarber(authentication.getName(), id);
    }

    @PostMapping("/me/appointments/{id}/reject")
    public AppointmentResponseDTO reject(@PathVariable Long id, Authentication authentication) {
        return appointmentService.rejectByBarber(authentication.getName(), id);
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
