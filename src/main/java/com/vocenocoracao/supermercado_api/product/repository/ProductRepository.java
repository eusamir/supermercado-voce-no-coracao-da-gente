package com.vocenocoracao.supermercado_api.product.repository;

import com.vocenocoracao.supermercado_api.product.entity.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, ProductCustomRepository {

    @Query("""
            select p from Product p
            join fetch p.category c
            where p.id = :id and p.active = true and c.active = true
            """)
    Optional<Product> findVisibleById(@Param("id") UUID id);

    @Query("""
            select p from Product p
            join fetch p.category
            where p.id = :id
            """)
    Optional<Product> findByIdWithCategory(@Param("id") UUID id);
}
