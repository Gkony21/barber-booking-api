package gr.barber.booking.service;

import gr.barber.booking.dto.BarberResponseDTO;
import gr.barber.booking.dto.PhotoDTO;
import gr.barber.booking.exception.BadRequestException;
import gr.barber.booking.exception.ResourceNotFoundException;
import gr.barber.booking.media.ImageProcessor;
import gr.barber.booking.media.ImageStorage;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.BarberPhoto;
import gr.barber.booking.repository.BarberPhotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class BarberPhotoService {

    public static final int MAX_PHOTOS = 20;
    private static final int PHOTO_MAX_SIDE = 1600;
    private static final int AVATAR_SIDE = 512;
    private static final int COVER_WIDTH = 1600;
    private static final int COVER_HEIGHT = 900;

    private final BarberPhotoRepository photoRepository;
    private final BarberService barberService;
    private final ImageProcessor imageProcessor;
    private final ImageStorage storage;

    public BarberPhotoService(BarberPhotoRepository photoRepository,
                              BarberService barberService,
                              ImageProcessor imageProcessor,
                              ImageStorage storage) {
        this.photoRepository = photoRepository;
        this.barberService = barberService;
        this.imageProcessor = imageProcessor;
        this.storage = storage;
    }

    @Transactional
    public BarberResponseDTO setAvatar(String email, MultipartFile file) {
        Barber barber = barberService.getByEmail(email);
        String key = storage.save(imageProcessor.square(bytes(file), AVATAR_SIDE));
        deleteFileAfterCommit(barber.getAvatarKey());
        barber.setAvatarKey(key);
        return BarberResponseDTO.from(barber);
    }

    // Wide, like the top of the barber's page: 16:9, at most 1600×900.
    @Transactional
    public BarberResponseDTO setCover(String email, MultipartFile file) {
        Barber barber = barberService.getByEmail(email);
        String key = storage.save(imageProcessor.cover(bytes(file), COVER_WIDTH, COVER_HEIGHT));
        deleteFileAfterCommit(barber.getCoverKey());
        barber.setCoverKey(key);
        return BarberResponseDTO.from(barber);
    }

    @Transactional
    public BarberResponseDTO removeCover(String email) {
        Barber barber = barberService.getByEmail(email);
        deleteFileAfterCommit(barber.getCoverKey());
        barber.setCoverKey(null);
        return BarberResponseDTO.from(barber);
    }

    @Transactional
    public BarberResponseDTO removeAvatar(String email) {
        Barber barber = barberService.getByEmail(email);
        deleteFileAfterCommit(barber.getAvatarKey());
        barber.setAvatarKey(null);
        return BarberResponseDTO.from(barber);
    }

    @Transactional(readOnly = true)
    public List<PhotoDTO> getPhotos(Long barberId) {
        return listFor(barberService.getApprovedById(barberId));
    }

    @Transactional(readOnly = true)
    public List<PhotoDTO> getMyPhotos(String email) {
        return listFor(barberService.getByEmail(email));
    }

    @Transactional
    public PhotoDTO addPhoto(String email, MultipartFile file, String caption) {
        Barber barber = barberService.getByEmail(email);
        if (photoRepository.countByBarberId(barber.getId()) >= MAX_PHOTOS) {
            throw new BadRequestException("TOO_MANY_PHOTOS", "You can have up to " + MAX_PHOTOS + " photos; delete one first");
        }
        String key = storage.save(imageProcessor.photo(bytes(file), PHOTO_MAX_SIDE));
        BarberPhoto photo = new BarberPhoto(barber, key);
        photo.setCaption(cleanCaption(caption));
        return PhotoDTO.from(photoRepository.save(photo));
    }

    @Transactional
    public PhotoDTO updateCaption(String email, Long photoId, String caption) {
        Barber barber = barberService.getByEmail(email);
        BarberPhoto photo = photoRepository.findByIdAndBarberId(photoId, barber.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
        photo.setCaption(cleanCaption(caption));
        return PhotoDTO.from(photo);
    }

    private static String cleanCaption(String caption) {
        if (caption == null || caption.isBlank()) {
            return null;
        }
        String trimmed = caption.strip();
        if (trimmed.length() > 80) {
            throw new BadRequestException("CAPTION_TOO_LONG", "Captions can be up to 80 characters");
        }
        return trimmed;
    }

    @Transactional
    public void deletePhoto(String email, Long photoId) {
        Barber barber = barberService.getByEmail(email);
        BarberPhoto photo = photoRepository.findByIdAndBarberId(photoId, barber.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
        photoRepository.delete(photo);
        deleteFileAfterCommit(photo.getImageKey());
    }

    private List<PhotoDTO> listFor(Barber barber) {
        return photoRepository.findAllByBarberIdOrderByCreatedAtDescIdDesc(barber.getId()).stream()
                .map(PhotoDTO::from)
                .toList();
    }

    private static byte[] bytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("NO_IMAGE", "Choose an image to upload");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("NOT_AN_IMAGE", "Could not read the uploaded file");
        }
    }

    // The old file is removed only once the database no longer points to it.
    private void deleteFileAfterCommit(String key) {
        if (key == null) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.delete(key);
            }
        });
    }
}
