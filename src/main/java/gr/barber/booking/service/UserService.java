package gr.barber.booking.service;

import gr.barber.booking.model.User;
import gr.barber.booking.repository.UserRepository;
import org.springframework.stereotype.Service;
import gr.barber.booking.dto.UserRequestDTO;
import gr.barber.booking.dto.UserResponseDTO;

@Service

public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponseDTO saveUser(UserRequestDTO request) {

        User user = new User(
                request.getName(),
                request.getEmail(),
                request.getPhoneNumber(),
                request.getPassword(),
                request.getRole()
        );

        User savedUser = userRepository.save(user);

        return new UserResponseDTO(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getPhoneNumber(),
                savedUser.getRole()
        );
    }


}
