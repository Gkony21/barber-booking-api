package gr.barber.booking.controller;

import gr.barber.booking.dto.BarberRequestDTO;
import gr.barber.booking.dto.BarberResponseDTO;
import gr.barber.booking.service.BarberDirectoryService;
import gr.barber.booking.service.BarberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/barbers")
public class BarberController {

    private final BarberService barberService;
    private final BarberDirectoryService directoryService;

    public BarberController(BarberService barberService, BarberDirectoryService directoryService) {
        this.barberService = barberService;
        this.directoryService = directoryService;
    }

    @GetMapping
    public List<BarberResponseDTO> getBarbers(@RequestParam(required = false) String city,
                                              @RequestParam(required = false) Long shopId,
                                              @RequestParam(defaultValue = "false") boolean availableToday) {
        return directoryService.list(city, shopId, availableToday);
    }

    @GetMapping("/{id}")
    public BarberResponseDTO getBarber(@PathVariable Long id) {
        return directoryService.get(id);
    }

    @PostMapping("/me")
    @ResponseStatus(HttpStatus.CREATED)
    public BarberResponseDTO createMyProfile(@Valid @RequestBody BarberRequestDTO request,
                                             Authentication authentication) {
        return barberService.createProfile(authentication.getName(), request);
    }

    @GetMapping("/me")
    public BarberResponseDTO getMyProfile(Authentication authentication) {
        return barberService.getMyProfile(authentication.getName());
    }

    @PutMapping("/me")
    public BarberResponseDTO updateMyProfile(@Valid @RequestBody BarberRequestDTO request,
                                             Authentication authentication) {
        return barberService.updateMyProfile(authentication.getName(), request);
    }

}
