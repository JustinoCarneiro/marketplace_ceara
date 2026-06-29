package com.onda.marketplace.category;

import com.onda.marketplace.audit.AuditService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
public class CategoryAdminController {

    private final CategoryService categoryService;
    private final AuditService    auditService;

    public CategoryAdminController(CategoryService categoryService, AuditService auditService) {
        this.categoryService = categoryService;
        this.auditService    = auditService;
    }

    private static UUID adminId(Authentication auth) {
        return auth != null ? UUID.fromString(auth.getName()) : UUID.randomUUID();
    }

    @GetMapping
    public ResponseEntity<List<CategoryDto>> listar() {
        return ResponseEntity.ok(categoryService.listar());
    }

    @PostMapping
    public ResponseEntity<CategoryDto> criar(@Valid @RequestBody CreateCategoryRequest req,
                                             Authentication auth) {
        CategoryDto dto = categoryService.criar(req);
        auditService.registrar(adminId(auth), "CRIAR_CATEGORIA", "service_category", dto.id(), dto.nome());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CategoryDto> atualizar(@PathVariable UUID id,
                                                 @RequestBody UpdateCategoryRequest req,
                                                 Authentication auth) {
        CategoryDto dto = categoryService.atualizar(id, req);
        auditService.registrar(adminId(auth), "ATUALIZAR_CATEGORIA", "service_category", id, dto.nome());
        return ResponseEntity.ok(dto);
    }
}
