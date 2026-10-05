package com.vocenocoracao.supermercado_api.category.repository;

import com.vocenocoracao.supermercado_api.category.entity.Category;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID>, CategoryCustomRepository {
    Optional<Category> findByNameIgnoreCase(String name);
}
