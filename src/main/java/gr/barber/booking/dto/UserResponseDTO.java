package gr.barber.booking.dto;

import gr.barber.booking.model.Role;

public class UserResponseDTO {

    private Long id;
    private String name;
    private String email;
    private String phoneNumber;
    private Role role;


    public UserResponseDTO(Long id, String name, String email, String phoneNumber, Role role) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.role = role;
    }


    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public Role getRole() {
        return role;
    }
}