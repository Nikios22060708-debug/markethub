package com.markethub.dto;

import com.markethub.entity.Address;

public record AddressResponse(Long id, Long userId, String label, String city, String street, String zipCode) {

    public static AddressResponse from(Address address) {
        return new AddressResponse(address.getId(), address.getUser().getId(), address.getLabel(),
                address.getCity(), address.getStreet(), address.getZipCode());
    }
}
