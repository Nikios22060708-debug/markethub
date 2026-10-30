package com.markethub.service;

import com.markethub.dto.PageResponse;
import com.markethub.dto.PaymentRequest;
import com.markethub.dto.PaymentResponse;
import com.markethub.entity.Order;
import com.markethub.entity.OrderStatus;
import com.markethub.entity.Payment;
import com.markethub.entity.PaymentStatus;
import com.markethub.exception.ConflictException;
import com.markethub.exception.NotFoundException;
import com.markethub.repository.PaymentRepository;
import com.markethub.security.AuthUser;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Оплата заказов (имитация платёжного шлюза) и возвраты.
 */
@Service
@Transactional(readOnly = true)
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final Counter paymentsCompleted;

    public PaymentService(PaymentRepository paymentRepository, OrderService orderService,
                          MeterRegistry meterRegistry) {
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
        this.paymentsCompleted = Counter.builder("markethub.payments.completed")
                .description("Количество успешных оплат").register(meterRegistry);
    }

    public PageResponse<PaymentResponse> list(Pageable pageable) {
        return PageResponse.from(paymentRepository.findAll(pageable).map(PaymentResponse::from));
    }

    public PaymentResponse get(AuthUser authUser, Long id) {
        Payment payment = getEntity(id);
        checkAccess(authUser, payment);
        return PaymentResponse.from(payment);
    }

    /** Оплатить можно только свой заказ в статусе NEW; сумма берётся из заказа, а не от клиента. */
    @Transactional
    public PaymentResponse pay(AuthUser authUser, PaymentRequest request) {
        Order order = orderService.getEntity(request.orderId());
        if (!order.getUser().getId().equals(authUser.id())) {
            throw new AccessDeniedException("Оплатить можно только свой заказ");
        }
        if (paymentRepository.existsByOrderId(order.getId())) {
            throw new ConflictException("Заказ уже оплачен");
        }
        if (order.getStatus() != OrderStatus.NEW) {
            throw new ConflictException("Заказ в статусе " + order.getStatus() + " нельзя оплатить");
        }
        Payment payment = new Payment(order, order.getTotalAmount(), request.method(), UUID.randomUUID().toString());
        order.setPayment(payment);
        order.setStatus(OrderStatus.PAID);
        Payment saved = paymentRepository.save(payment);
        paymentsCompleted.increment();
        log.info("Заказ #{} оплачен: {} ({})", order.getId(), saved.getAmount(), saved.getMethod());
        return PaymentResponse.from(saved);
    }

    /** Возврат средств сотрудником: заказ отменяется, товары возвращаются на склад. */
    @Transactional
    public PaymentResponse refund(Long id) {
        Payment payment = getEntity(id);
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new ConflictException("Платёж уже возвращён");
        }
        orderService.cancel(payment.getOrder());
        payment.setStatus(PaymentStatus.REFUNDED);
        log.info("Платёж #{} возвращён", id);
        return PaymentResponse.from(payment);
    }

    private Payment getEntity(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Платёж " + id + " не найден"));
    }

    private void checkAccess(AuthUser authUser, Payment payment) {
        if (!authUser.isStaff() && !payment.getOrder().getUser().getId().equals(authUser.id())) {
            throw new AccessDeniedException("Это не ваш платёж");
        }
    }
}
