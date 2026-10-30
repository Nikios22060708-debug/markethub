package com.markethub.service;

import com.markethub.dto.PaymentRequest;
import com.markethub.dto.PaymentResponse;
import com.markethub.entity.Order;
import com.markethub.entity.OrderItem;
import com.markethub.entity.OrderStatus;
import com.markethub.entity.Payment;
import com.markethub.entity.PaymentMethod;
import com.markethub.entity.PaymentStatus;
import com.markethub.entity.Product;
import com.markethub.entity.Role;
import com.markethub.entity.User;
import com.markethub.exception.ConflictException;
import com.markethub.exception.NotFoundException;
import com.markethub.repository.PaymentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderService orderService;

    private PaymentService paymentService;
    private Order order;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, orderService, new SimpleMeterRegistry());
        User alice = user(10L, "alice", Role.CUSTOMER);
        Product phone = product(7L, "Phone", "250.00", 5, category(1L, "C"));
        order = new Order(alice, address(5L, alice));
        order.setId(100L);
        order.addItem(new OrderItem(phone, 2, phone.getPrice()));
    }

    @Test
    void payMarksOrderPaidWithOrderAmount() {
        when(orderService.getEntity(100L)).thenReturn(order);
        when(paymentRepository.existsByOrderId(100L)).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(1L);
            return payment;
        });

        PaymentResponse response = paymentService.pay(auth(10L, "alice", Role.CUSTOMER),
                new PaymentRequest(100L, PaymentMethod.SBP));

        assertThat(response.amount()).isEqualByComparingTo("500.00");
        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.transactionId()).isNotBlank();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPayment()).isNotNull();
    }

    @Test
    void cannotPayForeignOrder() {
        when(orderService.getEntity(100L)).thenReturn(order);

        assertThatThrownBy(() -> paymentService.pay(auth(11L, "bob", Role.CUSTOMER),
                new PaymentRequest(100L, PaymentMethod.CARD))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cannotPayTwice() {
        when(orderService.getEntity(100L)).thenReturn(order);
        when(paymentRepository.existsByOrderId(100L)).thenReturn(true);

        assertThatThrownBy(() -> paymentService.pay(auth(10L, "alice", Role.CUSTOMER),
                new PaymentRequest(100L, PaymentMethod.CARD))).isInstanceOf(ConflictException.class);
    }

    @Test
    void cannotPayCancelledOrder() {
        order.setStatus(OrderStatus.CANCELLED);
        when(orderService.getEntity(100L)).thenReturn(order);
        when(paymentRepository.existsByOrderId(100L)).thenReturn(false);

        assertThatThrownBy(() -> paymentService.pay(auth(10L, "alice", Role.CUSTOMER),
                new PaymentRequest(100L, PaymentMethod.CARD))).isInstanceOf(ConflictException.class);
    }

    @Test
    void refundCancelsOrderAndMarksPaymentRefunded() {
        Payment payment = new Payment(order, order.getTotalAmount(), PaymentMethod.CARD, "tx");
        payment.setId(1L);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.refund(1L);

        verify(orderService).cancel(order);
        assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThatThrownBy(() -> paymentService.refund(1L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void getChecksOwnership() {
        Payment payment = new Payment(order, order.getTotalAmount(), PaymentMethod.CARD, "tx");
        payment.setId(1L);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.findById(2L)).thenReturn(Optional.empty());

        assertThat(paymentService.get(auth(10L, "alice", Role.CUSTOMER), 1L).orderId()).isEqualTo(100L);
        assertThat(paymentService.get(auth(1L, "admin", Role.ADMIN), 1L).id()).isEqualTo(1L);
        assertThatThrownBy(() -> paymentService.get(auth(11L, "bob", Role.CUSTOMER), 1L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> paymentService.get(auth(10L, "alice", Role.CUSTOMER), 2L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listReturnsPage() {
        Payment payment = new Payment(order, order.getTotalAmount(), PaymentMethod.CARD, "tx");
        PageRequest pageable = PageRequest.of(0, 10);
        when(paymentRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(payment), pageable, 1));

        assertThat(paymentService.list(pageable).totalElements()).isEqualTo(1);
    }
}
