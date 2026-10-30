package com.markethub.service;

import com.markethub.dto.CreateOrderRequest;
import com.markethub.dto.OrderItemRequest;
import com.markethub.dto.OrderResponse;
import com.markethub.entity.Address;
import com.markethub.entity.Order;
import com.markethub.entity.OrderItem;
import com.markethub.entity.OrderStatus;
import com.markethub.entity.Payment;
import com.markethub.entity.PaymentMethod;
import com.markethub.entity.PaymentStatus;
import com.markethub.entity.Product;
import com.markethub.entity.Role;
import com.markethub.entity.User;
import com.markethub.exception.BadRequestException;
import com.markethub.exception.ConflictException;
import com.markethub.exception.NotFoundException;
import com.markethub.repository.OrderRepository;
import com.markethub.repository.UserRepository;
import com.markethub.security.AuthUser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static com.markethub.TestData.address;
import static com.markethub.TestData.auth;
import static com.markethub.TestData.category;
import static com.markethub.TestData.product;
import static com.markethub.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AddressService addressService;
    @Mock
    private ProductService productService;

    private SimpleMeterRegistry meterRegistry;
    private OrderService orderService;

    private User customer;
    private AuthUser customerAuth;
    private Address address;
    private Product phone;
    private final Pageable pageable = PageRequest.of(0, 20);

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        orderService = new OrderService(orderRepository, userRepository, addressService, productService, meterRegistry);
        customer = user(10L, "alice", Role.CUSTOMER);
        customerAuth = auth(10L, "alice", Role.CUSTOMER);
        address = address(5L, customer);
        phone = product(7L, "Phone", "100.00", 10, category(1L, "Смартфоны"));
    }

    @Test
    void createMergesDuplicateLinesDecreasesStockAndCountsMetric() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(addressService.getOwned(customerAuth, 5L)).thenReturn(address);
        when(productService.getEntity(7L)).thenReturn(phone);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(100L);
            return order;
        });

        OrderResponse response = orderService.create(customerAuth, new CreateOrderRequest(5L,
                List.of(new OrderItemRequest(7L, 2), new OrderItemRequest(7L, 1))));

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(OrderStatus.NEW);
        assertThat(response.items()).singleElement().satisfies(item -> assertThat(item.quantity()).isEqualTo(3));
        assertThat(response.totalAmount()).isEqualByComparingTo("300.00");
        assertThat(phone.getStock()).isEqualTo(7);
        assertThat(meterRegistry.get("markethub.orders.created").counter().count()).isEqualTo(1.0);
    }

    @Test
    void createFailsWhenStockIsInsufficient() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(addressService.getOwned(customerAuth, 5L)).thenReturn(address);
        when(productService.getEntity(7L)).thenReturn(phone);

        assertThatThrownBy(() -> orderService.create(customerAuth,
                new CreateOrderRequest(5L, List.of(new OrderItemRequest(7L, 11)))))
                .isInstanceOf(ConflictException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createFailsForInactiveProduct() {
        phone.setActive(false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(addressService.getOwned(customerAuth, 5L)).thenReturn(address);
        when(productService.getEntity(7L)).thenReturn(phone);

        assertThatThrownBy(() -> orderService.create(customerAuth,
                new CreateOrderRequest(5L, List.of(new OrderItemRequest(7L, 1)))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createRejectsAddressOfAnotherUser() {
        User admin = user(1L, "admin", Role.ADMIN);
        AuthUser adminAuth = auth(1L, "admin", Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(addressService.getOwned(adminAuth, 5L)).thenReturn(address);

        assertThatThrownBy(() -> orderService.create(adminAuth,
                new CreateOrderRequest(5L, List.of(new OrderItemRequest(7L, 1)))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createFailsForUnknownUser() {
        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.create(customerAuth,
                new CreateOrderRequest(5L, List.of(new OrderItemRequest(7L, 1)))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void customerCannotSeeForeignOrder() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order(OrderStatus.NEW, 1)));

        assertThatThrownBy(() -> orderService.get(auth(11L, "bob", Role.CUSTOMER), 100L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void staffCanSeeAnyOrder() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order(OrderStatus.NEW, 1)));

        assertThat(orderService.get(auth(2L, "manager", Role.MANAGER), 100L).userId()).isEqualTo(10L);
    }

    @Test
    void getUnknownOrderThrowsNotFound() {
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.get(customerAuth, 404L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listUsesOwnOrdersForCustomerAndAllForStaff() {
        PageImpl<Order> page = new PageImpl<>(List.of(order(OrderStatus.NEW, 1)));
        when(orderRepository.findAllByUserId(10L, pageable)).thenReturn(page);
        when(orderRepository.findAllByUserIdAndStatus(10L, OrderStatus.NEW, pageable)).thenReturn(page);
        when(orderRepository.findAll(pageable)).thenReturn(page);
        when(orderRepository.findAllByStatus(OrderStatus.NEW, pageable)).thenReturn(page);

        AuthUser manager = auth(2L, "manager", Role.MANAGER);
        assertThat(orderService.list(customerAuth, null, pageable).totalElements()).isEqualTo(1);
        assertThat(orderService.list(customerAuth, OrderStatus.NEW, pageable).content()).hasSize(1);
        assertThat(orderService.list(manager, null, pageable).content()).hasSize(1);
        assertThat(orderService.list(manager, OrderStatus.NEW, pageable).content()).hasSize(1);
    }

    @Test
    void changeStatusFollowsLifecycle() {
        Order order = order(OrderStatus.PAID, 1);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThat(orderService.changeStatus(100L, OrderStatus.SHIPPED).status()).isEqualTo(OrderStatus.SHIPPED);
        assertThatThrownBy(() -> orderService.changeStatus(100L, OrderStatus.NEW))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> orderService.changeStatus(100L, OrderStatus.PAID))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void cancelRestoresStockAndRefundsPayment() {
        Order order = order(OrderStatus.PAID, 2);
        Payment payment = new Payment(order, order.getTotalAmount(), PaymentMethod.CARD, "tx-1");
        order.setPayment(payment);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.cancel(customerAuth, 100L);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(phone.getStock()).isEqualTo(10);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(meterRegistry.get("markethub.orders.cancelled").counter().count()).isEqualTo(1.0);
    }

    @Test
    void staffCancelViaStatusChange() {
        Order order = order(OrderStatus.NEW, 1);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThat(orderService.changeStatus(100L, OrderStatus.CANCELLED).status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void deliveredOrderCannotBeCancelled() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order(OrderStatus.DELIVERED, 1)));

        assertThatThrownBy(() -> orderService.cancel(customerAuth, 100L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void updateAddressOnlyByOwnerBeforeShipping() {
        Order order = order(OrderStatus.NEW, 1);
        Address newAddress = address(6L, customer);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(addressService.getOwned(customerAuth, 6L)).thenReturn(newAddress);

        assertThat(orderService.updateAddress(customerAuth, 100L, 6L).addressId()).isEqualTo(6L);

        AuthUser manager = auth(2L, "manager", Role.MANAGER);
        assertThatThrownBy(() -> orderService.updateAddress(manager, 100L, 6L))
                .isInstanceOf(AccessDeniedException.class);

        order.setStatus(OrderStatus.SHIPPED);
        assertThatThrownBy(() -> orderService.updateAddress(customerAuth, 100L, 6L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteAllowedOnlyForCancelledOrders() {
        Order active = order(OrderStatus.NEW, 1);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(active));
        assertThatThrownBy(() -> orderService.delete(100L)).isInstanceOf(ConflictException.class);

        active.setStatus(OrderStatus.CANCELLED);
        orderService.delete(100L);
        verify(orderRepository).delete(active);
    }

    /** Заказ alice с одной позицией телефона; остаток телефона уже списан. */
    private Order order(OrderStatus status, int quantity) {
        Order order = new Order(customer, address);
        order.setId(100L);
        order.addItem(new OrderItem(phone, quantity, phone.getPrice()));
        phone.setStock(10 - quantity);
        order.setStatus(status);
        return order;
    }
}
