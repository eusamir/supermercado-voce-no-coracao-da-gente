package com.vocenocoracao.supermercado_api.product.repository.query;

import com.vocenocoracao.supermercado_api.category.entity.Category;
import com.vocenocoracao.supermercado_api.product.dto.ProductFilterDTO;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.product.repository.ProductCustomRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class ProductCustomRepositoryImpl implements ProductCustomRepository {

    private static final char ESCAPE = '\\';

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Product> findAllVisible(ProductFilterDTO filter, Pageable pageable) {
        return findAll(filter, pageable, true);
    }

    @Override
    public long countVisible(ProductFilterDTO filter) {
        return count(filter, true);
    }

    @Override
    public List<Product> findAll(ProductFilterDTO filter, Pageable pageable) {
        return findAll(filter, pageable, false);
    }

    @Override
    public long count(ProductFilterDTO filter) {
        return count(filter, false);
    }

    @SuppressWarnings("unchecked")
    private List<Product> findAll(ProductFilterDTO filter, Pageable pageable, boolean visibleOnly) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Product> query = criteriaBuilder.createQuery(Product.class);
        Root<Product> product = query.from(Product.class);
        Join<Product, Category> category = (Join<Product, Category>) (Object) product.fetch("category", JoinType.INNER);

        query.where(buildPredicates(filter, criteriaBuilder, product, category, visibleOnly).toArray(new Predicate[0]));
        query.orderBy(buildOrders(pageable.getSort(), criteriaBuilder, product));

        return entityManager.createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();
    }

    private long count(ProductFilterDTO filter, boolean visibleOnly) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = criteriaBuilder.createQuery(Long.class);
        Root<Product> product = query.from(Product.class);
        Join<Product, Category> category = product.join("category", JoinType.INNER);

        query.select(criteriaBuilder.count(product));
        query.where(buildPredicates(filter, criteriaBuilder, product, category, visibleOnly).toArray(new Predicate[0]));

        return entityManager.createQuery(query).getSingleResult();
    }

    private List<Predicate> buildPredicates(
            ProductFilterDTO filter,
            CriteriaBuilder criteriaBuilder,
            Root<Product> product,
            Join<Product, Category> category,
            boolean visibleOnly) {

        List<Predicate> predicates = new ArrayList<>();

        if (visibleOnly) {
            predicates.add(criteriaBuilder.isTrue(product.get("active")));
            predicates.add(criteriaBuilder.isTrue(category.get("active")));
        }

        if (filter == null) {
            return predicates;
        }

        if (filter.search() != null && !filter.search().isBlank()) {
            predicates.add(((HibernateCriteriaBuilder) criteriaBuilder).ilike(
                    product.get("name"),
                    "%" + escapeLike(filter.search().trim()) + "%",
                    ESCAPE
            ));
        }

        if (!visibleOnly && filter.active() != null) {
            predicates.add(criteriaBuilder.equal(product.get("active"), filter.active()));
        }

        if (filter.categoryId() != null) {
            predicates.add(criteriaBuilder.equal(category.get("id"), filter.categoryId()));
        }

        return predicates;
    }

    private List<Order> buildOrders(Sort sort, CriteriaBuilder criteriaBuilder, Root<Product> product) {
        List<Order> orders = new ArrayList<>();

        for (Sort.Order order : sort) {
            orders.add(order.isAscending()
                    ? criteriaBuilder.asc(product.get(order.getProperty()))
                    : criteriaBuilder.desc(product.get(order.getProperty())));
        }

        orders.add(criteriaBuilder.asc(product.get("id")));
        return orders;
    }

    private String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
