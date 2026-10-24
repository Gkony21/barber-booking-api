package gr.barber.booking.controller;

import gr.barber.booking.dto.ClientCardDTO;
import gr.barber.booking.dto.ClientDTO;
import gr.barber.booking.dto.ClientNoteRequestDTO;
import gr.barber.booking.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** The barber's customers: everyone who booked with them, by app or by phone. */
@RestController
@RequestMapping("/barbers/me/clients")
public class BarberClientController {

    private final ClientService clientService;

    public BarberClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    // Also the suggestions while typing a name in a new appointment (?q=Κω&limit=8).
    @GetMapping
    public List<ClientDTO> getClients(@RequestParam(required = false) String q,
                                      @RequestParam(defaultValue = "200") int limit,
                                      Authentication authentication) {
        return clientService.getClients(authentication.getName(), q, Math.clamp(limit, 1, 500));
    }

    @GetMapping("/{key}")
    public ClientCardDTO getClient(@PathVariable String key, Authentication authentication) {
        return clientService.getClient(authentication.getName(), key);
    }

    @PutMapping("/{key}/note")
    public ClientDTO saveNote(@PathVariable String key, @Valid @RequestBody ClientNoteRequestDTO request,
                              Authentication authentication) {
        return clientService.saveNote(authentication.getName(), key, request.note());
    }
}
