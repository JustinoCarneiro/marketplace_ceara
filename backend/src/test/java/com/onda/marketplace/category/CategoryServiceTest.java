package com.onda.marketplace.category;

import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class CategoryServiceTest {

    @Mock ServiceCategoryRepository repository;

    CategoryService service;

    @BeforeEach
    void setUp() { service = new CategoryService(repository); }

    @Test
    void criar_slugInformado_persisteEmMinusculo() {
        when(repository.existsBySlug("eletrica")).thenReturn(false);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        CategoryDto dto = service.criar(new CreateCategoryRequest("Elétrica", "Eletrica"));

        assertThat(dto.nome()).isEqualTo("Elétrica");
        assertThat(dto.slug()).isEqualTo("eletrica");
        assertThat(dto.ativa()).isTrue();
    }

    @Test
    void criar_semSlug_derivaDoNome() {
        when(repository.existsBySlug(anyString())).thenReturn(false);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<ServiceCategory> captor = ArgumentCaptor.forClass(ServiceCategory.class);

        service.criar(new CreateCategoryRequest("Limpeza Pesada", null));

        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSlug()).isEqualTo("limpeza_pesada");
    }

    @Test
    void criar_slugDuplicado_lancaException() {
        when(repository.existsBySlug("eletrica")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(new CreateCategoryRequest("Elétrica", "eletrica")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CATEGORY_SLUG_IN_USE");
        verify(repository, never()).save(any());
    }

    @Test
    void atualizar_renomeiaEDesativa() {
        UUID id = UUID.randomUUID();
        ServiceCategory cat = new ServiceCategory("Velho", "velho");
        when(repository.findById(id)).thenReturn(Optional.of(cat));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        CategoryDto dto = service.atualizar(id, new UpdateCategoryRequest("Novo", false));

        assertThat(dto.nome()).isEqualTo("Novo");
        assertThat(dto.ativa()).isFalse();
    }

    @Test
    void atualizar_inexistente_lancaException() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(id, new UpdateCategoryRequest("X", null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CATEGORY_NOT_FOUND");
    }

    @Test
    void listar_retornaMapeado() {
        when(repository.findAllByOrderByNomeAsc()).thenReturn(List.of(
                new ServiceCategory("Elétrica", "eletrica"),
                new ServiceCategory("Limpeza", "limpeza")));

        List<CategoryDto> r = service.listar();

        assertThat(r).extracting(CategoryDto::nome).containsExactly("Elétrica", "Limpeza");
    }
}
