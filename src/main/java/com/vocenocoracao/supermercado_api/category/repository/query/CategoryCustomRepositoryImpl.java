package com.vocenocoracao.supermercado_api.category.repository.query;

import com.vocenocoracao.supermercado_api.category.dto.CategoryFilterDTO;
import com.vocenocoracao.supermercado_api.category.entity.Category;
import com.vocenocoracao.supermercado_api.category.repository.CategoryCustomRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class CategoryCustomRepositoryImpl implements CategoryCustomRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Category> findAll(
            CategoryFilterDTO filter,
            Pageable pageable) {

        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();

        CriteriaQuery<Category> query =
                criteriaBuilder.createQuery(Category.class);

        Root<Category> category =
                query.from(Category.class);

        List<Predicate> predicates =
                buildPredicates(filter, criteriaBuilder, category);

        query.where(predicates.toArray(new Predicate[0]));

        query.orderBy(criteriaBuilder.asc(category.get("name")));

        TypedQuery<Category> typedQuery =
                entityManager.createQuery(query);

        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        return typedQuery.getResultList();
    }

    @Override
    public long count(CategoryFilterDTO filter) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> query =
                cb.createQuery(Long.class);

        Root<Category> category =
                query.from(Category.class);

        List<Predicate> predicates =
                buildPredicates(filter, cb, category);

        query.select(cb.count(category));

        query.where(predicates.toArray(new Predicate[0]));

        return entityManager
                .createQuery(query)
                .getSingleResult();
    }

    private String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private List<Predicate> buildPredicates(
            CategoryFilterDTO filter,
            CriteriaBuilder criteriaBuilder,
            Root<Category> category) {

        List<Predicate> predicates = new ArrayList<>();

        if (filter == null) {
            return predicates;
        }

        if (filter.search() != null && !filter.search().isBlank()) {

            String search = "%" +
                    escapeLike(filter.search().trim().toLowerCase()) +
                    "%";

            predicates.add(
                    criteriaBuilder.like(
                            criteriaBuilder.lower(category.get("name")),
                            search,
                            '\\'
                    )
            );
        }

        if (filter.active() != null) {

            predicates.add(
                    criteriaBuilder.equal(
                            category.get("active"),
                            filter.active()
                    )
            );
        }

        if (filter.createdAtFrom() != null) {

            predicates.add(
                    criteriaBuilder.greaterThanOrEqualTo(
                            category.get("createdAt"),
                            filter.createdAtFrom()
                                    .atStartOfDay()
                                    .toInstant(ZoneOffset.UTC)
                    )
            );
        }

        if (filter.createdAtTo() != null) {

            predicates.add(
                    criteriaBuilder.lessThan(
                            category.get("createdAt"),
                            filter.createdAtTo()
                                    .plusDays(1)
                                    .atStartOfDay()
                                    .toInstant(ZoneOffset.UTC)
                    )
            );
        }

        return predicates;
    }
}