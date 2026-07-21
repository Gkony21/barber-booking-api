package gr.barber.booking.controller;

import gr.barber.booking.service.UserService;
import org.springframework.web.bind.annotation.*;
import gr.barber.booking.dto.UserRequestDTO;
import gr.barber.booking.dto.UserResponseDTO;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public UserResponseDTO createUser(@RequestBody UserRequestDTO request) {
        return userService.saveUser(request);
    }

}