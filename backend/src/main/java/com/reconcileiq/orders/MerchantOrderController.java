package com.reconcileiq.orders;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.reconcileiq.orders.OrderDtos.*;

@RestController
@RequestMapping("/api/v1/orders")
@CrossOrigin(origins = "http://localhost:5173")
public class MerchantOrderController {

    private final MerchantOrderService orderService;

    public MerchantOrderController(MerchantOrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MerchantOrderResponse createOrder(
            @RequestBody CreateOrderRequest request) {

        return orderService.createOrder(request);
    }

    @GetMapping
    public List<MerchantOrderResponse> listOrders() {
        return orderService.listOrders();
    }

    @PostMapping("/{paypalOrderId}/capture")
    public java.util.Map<String, Object> capturePayment(@PathVariable String paypalOrderId) {
        return orderService.capturePayment(paypalOrderId);
    }
}