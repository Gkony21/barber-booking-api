package gr.barber.booking.controller;

import gr.barber.booking.dto.WorkingHoursDTO;
import gr.barber.booking.service.WorkingHoursService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/barbers")
public class WorkingHoursController {

    private final WorkingHoursService workingHoursService;

    public WorkingHoursController(WorkingHoursService workingHoursService) {
        this.workingHoursService = workingHoursService;
    }

    @GetMapping("/{barberId}/working-hours")
    public List<WorkingHoursDTO> getWorkingHours(@PathVariable Long barberId) {
        return workingHoursService.getWorkingHours(barberId);
    }

    @GetMapping("/me/working-hours")
    public List<WorkingHoursDTO> getMyWorkingHours(Authentication authentication) {
        return workingHoursService.getMyWorkingHours(authentication.getName());
    }

    @PutMapping("/me/working-hours")
    public List<WorkingHoursDTO> replaceMyWorkingHours(@RequestBody List<@Valid WorkingHoursDTO> request,
                                                       Authentication authentication) {
        return workingHoursService.replaceMyWorkingHours(authentication.getName(), request);
    }

}
