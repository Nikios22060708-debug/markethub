package com.markethub.service;

import com.markethub.dto.AddressRequest;
import com.markethub.dto.AddressResponse;
import com.markethub.entity.Address;
import com.markethub.entity.User;
import com.markethub.exception.ConflictException;
import com.markethub.exception.NotFoundException;
import com.markethub.repository.AddressRepository;
import com.markethub.repository.OrderRepository;
import com.markethub.repository.UserRepository;
import com.markethub.security.AuthUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Адреса доставки. Пользователь видит и меняет только свои, администратор — любые.
 */
@Service
@Transactional(readOnly = true)
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public AddressService(AddressRepository addressRepository, UserRepository userRepository,
                          OrderRepository orderRepository) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    public List<AddressResponse> listMine(AuthUser authUser) {
        return addressRepository.findAllByUserIdOrderByIdAsc(authUser.id()).stream()
                .map(AddressResponse::from).toList();
    }

    public AddressResponse get(AuthUser authUser, Long id) {
        return AddressResponse.from(getOwned(authUser, id));
    }

    @Transactional
    public AddressResponse create(AuthUser authUser, AddressRequest request) {
        User user = userRepository.findById(authUser.id())
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        Address address = new Address(user, request.label(), request.city(), request.street(), request.zipCode());
        return AddressResponse.from(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse update(AuthUser authUser, Long id, AddressRequest request) {
        Address address = getOwned(authUser, id);
        address.setLabel(request.label());
        address.setCity(request.city());
        address.setStreet(request.street());
        address.setZipCode(request.zipCode());
        return AddressResponse.from(address);
    }

    @Transactional
    public void delete(AuthUser authUser, Long id) {
        Address address = getOwned(authUser, id);
        if (orderRepository.existsByAddressId(id)) {
            throw new ConflictException("Адрес используется в заказах и не может быть удалён");
        }
        addressRepository.delete(address);
    }

    /** Возвращает адрес, если он принадлежит пользователю (или пользователь — администратор). */
    Address getOwned(AuthUser authUser, Long id) {
        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Адрес " + id + " не найден"));
        if (!authUser.isAdmin() && !address.getUser().getId().equals(authUser.id())) {
            throw new AccessDeniedException("Это не ваш адрес");
        }
        return address;
    }
}
