package gr.barber.booking.dto;

import gr.barber.booking.media.MediaController;
import gr.barber.booking.model.BarberPhoto;

public record PhotoDTO(Long id, String url, String caption) {

    public static PhotoDTO from(BarberPhoto photo) {
        return new PhotoDTO(photo.getId(), MediaController.url(photo.getImageKey()), photo.getCaption());
    }
}
