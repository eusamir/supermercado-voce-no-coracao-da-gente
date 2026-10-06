package com.vocenocoracao.supermercado_api.cartItem.repository;

import com.vocenocoracao.supermercado_api.cartItem.entity.CartItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    @Query("""
            select i from CartItem i
            join fetch i.product p
            join fetch p.category
            where i.cart.id = :cartId
            order by i.createdAt, i.id
            """)
    List<CartItem> findAllByCartId(@Param("cartId") UUID cartId);

    @Query("""
            select i from CartItem i
            join fetch i.product p
            join fetch p.category
            where i.cart.id = :cartId and p.id = :productId
            """)
    Optional<CartItem> findByCartIdAndProductId(@Param("cartId") UUID cartId, @Param("productId") UUID productId);
}
