package com.vocenocoracao.supermercado_api.product.repository;

import com.vocenocoracao.supermercado_api.product.entity.Product;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, ProductCustomRepository {

    @Query("""
            select product from Product product
            join fetch product.category category
            where product.id = :id and product.active = true and category.active = true
            """)
    Optional<Product> findVisibleById(@Param("id") UUID id);

    @Query("""
            select product from Product product
            join fetch product.category
            where product.id = :id
            """)
    Optional<Product> findByIdWithCategory(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from Product product where product.id in :ids order by product.id")
    List<Product> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);
}
