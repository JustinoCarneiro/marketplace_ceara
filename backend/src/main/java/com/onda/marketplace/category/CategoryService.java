package com.onda.marketplace.category;

import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Catálogo de categorias gerido pelo admin (US28): listar, criar e atualizar
 * (renomear / ativar / desativar). O {@code slug} é único.
 */
@Service
@SuppressWarnings("null")
public class CategoryService {

    private final ServiceCategoryRepository repository;

    public CategoryService(ServiceCategoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> listar() {
        return repository.findAllByOrderByNomeAsc().stream()
                .map(CategoryDto::from)
                .toList();
    }

    @Transactional
    public CategoryDto criar(CreateCategoryRequest req) {
        String slug = (req.slug() == null || req.slug().isBlank())
                ? slugify(req.nome())
                : req.slug().trim().toLowerCase();

        if (repository.existsBySlug(slug)) {
            throw new BusinessException("CATEGORY_SLUG_IN_USE",
                    "Já existe uma categoria com esse slug.");
        }
        ServiceCategory cat = repository.save(new ServiceCategory(req.nome().trim(), slug));
        return CategoryDto.from(cat);
    }

    @Transactional
    public CategoryDto atualizar(UUID id, UpdateCategoryRequest req) {
        ServiceCategory cat = repository.findById(id)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND",
                        "Categoria não encontrada."));

        if (req.nome() != null && !req.nome().isBlank()) {
            cat.renomear(req.nome().trim());
        }
        if (req.ativa() != null) {
            if (req.ativa()) cat.ativar(); else cat.desativar();
        }
        return CategoryDto.from(repository.save(cat));
    }

    private static String slugify(String nome) {
        return nome.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("(^_|_$)", "");
    }
}
