package com.vocenocoracao.supermercado_api.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vocenocoracao.supermercado_api.category.dto.CategoryFilterDTO;
import com.vocenocoracao.supermercado_api.category.entity.Category;
import com.vocenocoracao.supermercado_api.category.repository.CategoryRepository;
import com.vocenocoracao.supermercado_api.category.service.impl.CategoryServiceImpl;
import com.vocenocoracao.supermercado_api.exceptions.AlreadyExistsException;
import com.vocenocoracao.supermercado_api.exceptions.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository repository;

    @InjectMocks
    private CategoryServiceImpl service;

    private Category category(UUID id, String name, boolean active) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setActive(active);
        return category;
    }

    @Test
    void createSavesNewCategory() {
        Category input = category(null, "Padaria", true);
        when(repository.findByNameIgnoreCase("Padaria")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(input)).thenReturn(input);

        assertThat(service.create(input)).isSameAs(input);
    }

    @Test
    void createFailsWhenNameAlreadyExistsIgnoringCase() {
        Category input = category(null, "padaria", true);
        when(repository.findByNameIgnoreCase("padaria"))
                .thenReturn(Optional.of(category(UUID.randomUUID(), "Padaria", true)));

        assertThatThrownBy(() -> service.create(input)).isInstanceOf(AlreadyExistsException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void createConvertsConcurrentUniqueViolationIntoConflict() {
        Category input = category(null, "Padaria", true);
        when(repository.findByNameIgnoreCase("Padaria")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(input)).thenThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> service.create(input)).isInstanceOf(AlreadyExistsException.class);
    }

    @Test
    void updateFailsWhenRenamingToNameOfAnotherCategory() {
        Category input = category(UUID.randomUUID(), "Açougue", true);
        when(repository.findByNameIgnoreCase("Açougue"))
                .thenReturn(Optional.of(category(UUID.randomUUID(), "Açougue", true)));

        assertThatThrownBy(() -> service.update(input)).isInstanceOf(AlreadyExistsException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updateAllowsRenamingToItself() {
        UUID id = UUID.randomUUID();
        Category input = category(id, "PADARIA", true);
        when(repository.findByNameIgnoreCase("PADARIA")).thenReturn(Optional.of(category(id, "Padaria", true)));
        when(repository.saveAndFlush(input)).thenReturn(input);

        assertThat(service.update(input)).isSameAs(input);
    }

    @Test
    void changeActiveDeactivatesCategory() {
        UUID id = UUID.randomUUID();
        Category existing = category(id, "Padaria", true);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        assertThat(service.changeActive(id, false).isActive()).isFalse();
        verify(repository).save(existing);
    }

    @Test
    void changeActiveIsIdempotent() {
        UUID id = UUID.randomUUID();
        Category existing = category(id, "Padaria", false);
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        assertThat(service.changeActive(id, false).isActive()).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void findActiveByIdTreatsInactiveAsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(category(id, "Padaria", false)));

        assertThatThrownBy(() -> service.findActiveById(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void findAllActiveForcesActiveFilterKeepingTheOthers() {
        Pageable pageable = PageRequest.of(0, 20);
        when(repository.findAll(any(CategoryFilterDTO.class), any(Pageable.class))).thenReturn(List.of());
        when(repository.count(any(CategoryFilterDTO.class))).thenReturn(0L);

        service.findAllActive(new CategoryFilterDTO("pad", false, null, null), pageable);

        ArgumentCaptor<CategoryFilterDTO> captor = ArgumentCaptor.forClass(CategoryFilterDTO.class);
        verify(repository).findAll(captor.capture(), any(Pageable.class));
        assertThat(captor.getValue().active()).isTrue();
        assertThat(captor.getValue().search()).isEqualTo("pad");
    }

    @Test
    void findAllActiveAcceptsNullFilter() {
        Pageable pageable = PageRequest.of(0, 20);
        when(repository.findAll(any(CategoryFilterDTO.class), any(Pageable.class))).thenReturn(List.of());
        when(repository.count(any(CategoryFilterDTO.class))).thenReturn(0L);

        Page<Category> page = service.findAllActive(null, pageable);

        assertThat(page.getTotalElements()).isZero();
    }
}
