package gr.barber.booking.service;

import gr.barber.booking.dto.BarberResponseDTO;
import gr.barber.booking.dto.ShopRequestDTO;
import gr.barber.booking.dto.ShopResponseDTO;
import gr.barber.booking.exception.BadRequestException;
import gr.barber.booking.exception.ConflictException;
import gr.barber.booking.exception.ResourceNotFoundException;
import gr.barber.booking.model.Barber;
import gr.barber.booking.model.BarberStatus;
import gr.barber.booking.model.Shop;
import gr.barber.booking.repository.BarberRepository;
import gr.barber.booking.repository.ShopRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class ShopService {

    private final ShopRepository shopRepository;
    private final BarberRepository barberRepository;
    private final BarberService barberService;

    public ShopService(ShopRepository shopRepository,
                       BarberRepository barberRepository,
                       BarberService barberService) {
        this.shopRepository = shopRepository;
        this.barberRepository = barberRepository;
        this.barberService = barberService;
    }

    @Transactional
    public ShopResponseDTO createShop(String email, ShopRequestDTO request) {

        Barber owner = barberService.getByEmail(email);

        if (owner.getStatus() != BarberStatus.APPROVED) {
            throw new AccessDeniedException("Only approved barbers can create a shop");
        }
        if (owner.getShop() != null) {
            throw new ConflictException("You already belong to a shop");
        }

        Shop shop = new Shop(owner);
        applyDetails(shop, request);
        shop = shopRepository.save(shop);

        owner.setShop(shop);

        return ShopResponseDTO.from(shop);
    }

    @Transactional(readOnly = true)
    public List<ShopResponseDTO> getShops(String city) {

        List<Shop> shops = (city != null && !city.isBlank())
                ? shopRepository.findAllByCityIgnoreCase(city.trim())
                : shopRepository.findAll();

        return shops.stream().map(ShopResponseDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public ShopResponseDTO getShop(Long id) {
        return ShopResponseDTO.from(getById(id));
    }

    @Transactional
    public ShopResponseDTO updateShop(Long id, String email, ShopRequestDTO request) {
        Shop shop = getOwnedShop(id, email);
        applyDetails(shop, request);
        return ShopResponseDTO.from(shop);
    }

    @Transactional
    public void deleteShop(Long id, String email) {
        Shop shop = getOwnedShop(id, email);
        // Other members are detached by the ON DELETE SET NULL foreign key.
        shop.getOwner().setShop(null);
        shopRepository.delete(shop);
    }

    @Transactional
    public BarberResponseDTO addBarber(Long shopId, String ownerEmail, String barberEmail) {

        Shop shop = getOwnedShop(shopId, ownerEmail);

        Barber barber = barberRepository.findByUserEmail(barberEmail)
                .orElseThrow(() -> new ResourceNotFoundException("No barber profile for this email"));

        if (barber.getStatus() != BarberStatus.APPROVED) {
            throw new BadRequestException("Barber is not approved yet");
        }
        if (barber.getShop() != null) {
            throw new ConflictException("Barber already belongs to a shop");
        }

        barber.setShop(shop);
        return BarberResponseDTO.from(barber);
    }

    // The shop owner can remove any barber; a barber can remove themselves (leave the shop).
    @Transactional
    public void removeBarber(Long shopId, Long barberId, String email) {

        Shop shop = getById(shopId);
        Barber requester = barberService.getByEmail(email);

        boolean isOwner = Objects.equals(shop.getOwner().getId(), requester.getId());
        boolean isSelf = Objects.equals(barberId, requester.getId());

        if (!isOwner && !isSelf) {
            throw new AccessDeniedException("You do not have permission to remove this barber");
        }
        if (Objects.equals(barberId, shop.getOwner().getId())) {
            throw new BadRequestException("The owner cannot leave the shop; delete the shop instead");
        }

        Barber barber = barberRepository.findById(barberId)
                .filter(b -> b.getShop() != null && Objects.equals(b.getShop().getId(), shopId))
                .orElseThrow(() -> new ResourceNotFoundException("Barber is not part of this shop"));

        barber.setShop(null);
    }

    private Shop getById(Long id) {
        return shopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
    }

    private Shop getOwnedShop(Long id, String email) {
        Shop shop = getById(id);
        if (!shop.getOwner().getUser().getEmail().equals(email)) {
            throw new AccessDeniedException("Only the shop owner can do this");
        }
        return shop;
    }

    private void applyDetails(Shop shop, ShopRequestDTO request) {
        shop.setName(request.name());
        shop.setAddress(request.address());
        shop.setCity(request.city());
        shop.setPhoneNumber(request.phoneNumber());
        shop.setLatitude(request.latitude());
        shop.setLongitude(request.longitude());
    }
}
