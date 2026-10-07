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
            select cartItem from CartItem cartItem
            join fetch cartItem.product product
            join fetch product.category
            where cartItem.cart.id = :cartId
            order by cartItem.createdAt, cartItem.id
            """)
    List<CartItem> findAllByCartId(@Param("cartId") UUID cartId);

    @Query("""
            select cartItem from CartItem cartItem
            join fetch cartItem.product product
            join fetch product.category
            where cartItem.cart.id = :cartId and product.id = :productId
            """)
    Optional<CartItem> findByCartIdAndProductId(@Param("cartId") UUID cartId, @Param("productId") UUID productId);
}
