package com.estefano.deliverybackbone.orders;

import com.estefano.deliverybackbone.orders.OrderDtos.CheckoutRequest;
import com.estefano.deliverybackbone.orders.OrderDtos.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CheckoutService checkout;

    public OrderController(CheckoutService checkout) {
        this.checkout = checkout;
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(@Valid @RequestBody CheckoutRequest request) {
        return checkout.checkout(request);
    }

    @PostMapping("/{id}/pay")
    public OrderResponse pay(@PathVariable long id) {
        return checkout.pay(id);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable long id) {
        return checkout.getOrder(id);
    }

    /** Sin auth todavía: el userId llega como query param (se reemplaza por JWT en fase posterior). */
    @GetMapping("/my")
    public List<OrderResponse> myOrders(@RequestParam long userId) {
        return checkout.getOrdersByUser(userId);
    }
}
