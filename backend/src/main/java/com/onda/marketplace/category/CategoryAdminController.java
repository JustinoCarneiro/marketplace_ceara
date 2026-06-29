package com.onda.marketplace.category;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * CRUD do catálogo de categorias (US28). Superfície web do admin — todas as
 * rotas exigem {@code ROLE_ADMIN} (403 caso contrário).
 */
@RestController
@RequestMapping("/api/v1/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
@SuppressWarnings("null")
public class CategoryAdminController {

    private final CategoryService categoryService;

    public CategoryAdminController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<List<CategoryDto>> listar() {
        return ResponseEntity.ok(categoryService.listar());
    }

    @PostMapping
    public ResponseEntity<CategoryDto> criar(@Valid @RequestBody CreateCategoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.criar(req));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CategoryDto> atualizar(@PathVariable UUID id,
                                                 @RequestBody UpdateCategoryRequest req) {
        return ResponseEntity.ok(categoryService.atualizar(id, req));
    }
}
