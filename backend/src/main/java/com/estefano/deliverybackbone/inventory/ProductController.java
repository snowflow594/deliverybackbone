package com.estefano.deliverybackbone.inventory;

import com.estefano.deliverybackbone.inventory.ProductDtos.CreateProductRequest;
import com.estefano.deliverybackbone.inventory.ProductDtos.ProductResponse;
import com.estefano.deliverybackbone.inventory.ProductDtos.RestockRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final InventoryService inventory;

    public ProductController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping
    public List<ProductResponse> list(@RequestParam(name = "category", required = false) Long categoryId,
                                      @RequestParam(required = false) String search) {
        return inventory.search(categoryId, search).stream()
                .map(ProductResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable long id) {
        return ProductResponse.from(inventory.getProduct(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        var product = inventory.createProduct(request.categoryId(), request.sku(),
                request.name(), request.price(), request.initialStock());
        return ProductResponse.from(product);
    }

    @PatchMapping("/{id}/restock")
    public ProductResponse restock(@PathVariable long id, @Valid @RequestBody RestockRequest request) {
        return ProductResponse.from(inventory.restock(id, request.quantity()));
    }
}
