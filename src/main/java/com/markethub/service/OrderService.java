package com.markethub.service;

import com.markethub.dto.CreateOrderRequest;
import com.markethub.dto.OrderItemRequest;
import com.markethub.dto.OrderResponse;
import com.markethub.dto.PageResponse;
import com.markethub.entity.Address;
import com.markethub.entity.Order;
import com.markethub.entity.OrderItem;
import com.markethub.entity.OrderStatus;
import com.markethub.entity.PaymentStatus;
import com.markethub.entity.Product;
import com.markethub.entity.User;
import com.markethub.exception.BadRequestException;
import com.markethub.exception.ConflictException;
import com.markethub.exception.NotFoundException;
import com.markethub.repository.OrderRepository;
import com.markethub.repository.UserRepository;
import com.markethub.security.AuthUser;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Оформление и жизненный цикл заказов.
 * Создание заказа — одна транзакция: проверка адреса, списание остатков, фиксация цен.
 * Если хоть одного товара не хватает, откатывается всё.
 */
@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final AddressService addressService;
    private final ProductService productService;
    private final Counter ordersCreated;
    private final Counter ordersCancelled;

    public OrderService(OrderRepository orderRepository, UserRepository userRepository,
                        AddressService addressService, ProductService productService,
                        MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.addressService = addressService;
        this.productService = productService;
        this.ordersCreated = Counter.builder("markethub.orders.created")
                .description("Количество оформленных заказов").register(meterRegistry);
        this.ordersCancelled = Counter.builder("markethub.orders.cancelled")
                .description("Количество отменённых заказов").register(meterRegistry);
    }

    /** Покупатель видит свои заказы, сотрудники — все. Можно фильтровать по статусу. */
    public PageResponse<OrderResponse> list(AuthUser authUser, OrderStatus status, Pageable pageable) {
        Page<Order> page;
        if (authUser.isStaff()) {
            page = status == null ? orderRepository.findAll(pageable) : orderRepository.findAllByStatus(status, pageable);
        } else {
            page = status == null
                    ? orderRepository.findAllByUserId(authUser.id(), pageable)
                    : orderRepository.findAllByUserIdAndStatus(authUser.id(), status, pageable);
        }
        return PageResponse.from(page.map(OrderResponse::from));
    }

    public OrderResponse get(AuthUser authUser, Long id) {
        return OrderResponse.from(getAccessible(authUser, id));
    }

    @Transactional
    public OrderResponse create(AuthUser authUser, CreateOrderRequest request) {
        User user = userRepository.findById(authUser.id())
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        Address address = addressService.getOwned(authUser, request.addressId());
        if (!address.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Адрес доставки должен принадлежать покупателю");
        }

        // Одинаковые товары в запросе объединяем в одну позицию
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderItemRequest item : request.items()) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }

        Order order = new Order(user, address);
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Product product = productService.getEntity(entry.getKey());
            if (!product.isActive()) {
                throw new ConflictException("Товар '" + product.getName() + "' снят с продажи");
            }
            product.decreaseStock(entry.getValue());
            order.addItem(new OrderItem(product, entry.getValue(), product.getPrice()));
        }

        Order saved = orderRepository.save(order);
        ordersCreated.increment();
        log.info("Оформлен заказ #{} пользователем '{}' на сумму {}", saved.getId(), user.getUsername(),
                saved.getTotalAmount());
        return OrderResponse.from(saved);
    }

    /** Смена адреса доставки — только владельцем и только пока заказ не отправлен. */
    @Transactional
    public OrderResponse updateAddress(AuthUser authUser, Long id, Long addressId) {
        Order order = getAccessible(authUser, id);
        if (!order.getUser().getId().equals(authUser.id())) {
            throw new AccessDeniedException("Изменить адрес может только покупатель");
        }
        if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.PAID) {
            throw new ConflictException("Заказ уже отправлен, адрес изменить нельзя");
        }
        order.setAddress(addressService.getOwned(authUser, addressId));
        return OrderResponse.from(order);
    }

    /** Смена статуса сотрудником. Перевод в PAID делается только через оплату. */
    @Transactional
    public OrderResponse changeStatus(Long id, OrderStatus newStatus) {
        Order order = getEntity(id);
        if (newStatus == OrderStatus.PAID) {
            throw new BadRequestException("Статус PAID устанавливается только при оплате");
        }
        if (newStatus == OrderStatus.CANCELLED) {
            cancel(order);
            return OrderResponse.from(order);
        }
        if (!order.getStatus().canTransitionTo(newStatus)) {
            throw new ConflictException("Нельзя перевести заказ из %s в %s".formatted(order.getStatus(), newStatus));
        }
        order.setStatus(newStatus);
        log.info("Заказ #{} переведён в статус {}", id, newStatus);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(AuthUser authUser, Long id) {
        Order order = getAccessible(authUser, id);
        cancel(order);
        return OrderResponse.from(order);
    }

    /**
     * Отмена: остатки возвращаются на склад, оплаченный заказ получает возврат.
     */
    @Transactional
    public void cancel(Order order) {
        if (!order.getStatus().isCancellable()) {
            throw new ConflictException("Заказ в статусе " + order.getStatus() + " отменить нельзя");
        }
        for (OrderItem item : order.getItems()) {
            item.getProduct().increaseStock(item.getQuantity());
        }
        if (order.getPayment() != null && order.getPayment().getStatus() == PaymentStatus.COMPLETED) {
            order.getPayment().setStatus(PaymentStatus.REFUNDED);
        }
        order.setStatus(OrderStatus.CANCELLED);
        ordersCancelled.increment();
        log.info("Заказ #{} отменён", order.getId());
    }

    /** Физическое удаление — только администратором и только отменённых заказов. */
    @Transactional
    public void delete(Long id) {
        Order order = getEntity(id);
        if (order.getStatus() != OrderStatus.CANCELLED) {
            throw new ConflictException("Удалить можно только отменённый заказ");
        }
        orderRepository.delete(order);
    }

    Order getEntity(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Заказ " + id + " не найден"));
    }

    Order getAccessible(AuthUser authUser, Long id) {
        Order order = getEntity(id);
        if (!authUser.isStaff() && !order.getUser().getId().equals(authUser.id())) {
            throw new AccessDeniedException("Это не ваш заказ");
        }
        return order;
    }
}
