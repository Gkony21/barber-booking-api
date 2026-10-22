package gr.barber.booking.controller;

import gr.barber.booking.dto.BarberResponseDTO;
import gr.barber.booking.dto.PhotoCaptionDTO;
import gr.barber.booking.dto.PhotoDTO;
import jakarta.validation.Valid;
import gr.barber.booking.service.BarberPhotoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/barbers")
public class BarberPhotoController {

    private final BarberPhotoService photoService;

    public BarberPhotoController(BarberPhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping("/{barberId}/photos")
    public List<PhotoDTO> getPhotos(@PathVariable Long barberId) {
        return photoService.getPhotos(barberId);
    }

    @GetMapping("/me/photos")
    public List<PhotoDTO> getMyPhotos(Authentication authentication) {
        return photoService.getMyPhotos(authentication.getName());
    }

    @PostMapping(value = "/me/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoDTO addPhoto(@RequestParam("file") MultipartFile file,
                             @RequestParam(value = "caption", required = false) String caption,
                             Authentication authentication) {
        return photoService.addPhoto(authentication.getName(), file, caption);
    }

    @PatchMapping("/me/photos/{photoId}")
    public PhotoDTO updateCaption(@PathVariable Long photoId,
                                  @Valid @RequestBody PhotoCaptionDTO request,
                                  Authentication authentication) {
        return photoService.updateCaption(authentication.getName(), photoId, request.caption());
    }

    @DeleteMapping("/me/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(@PathVariable Long photoId, Authentication authentication) {
        photoService.deletePhoto(authentication.getName(), photoId);
    }

    @PutMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BarberResponseDTO setAvatar(@RequestParam("file") MultipartFile file, Authentication authentication) {
        return photoService.setAvatar(authentication.getName(), file);
    }

    @PutMapping(value = "/me/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BarberResponseDTO setCover(@RequestParam("file") MultipartFile file, Authentication authentication) {
        return photoService.setCover(authentication.getName(), file);
    }

    @DeleteMapping("/me/cover")
    public BarberResponseDTO removeCover(Authentication authentication) {
        return photoService.removeCover(authentication.getName());
    }

    @DeleteMapping("/me/avatar")
    public BarberResponseDTO removeAvatar(Authentication authentication) {
        return photoService.removeAvatar(authentication.getName());
    }
}
