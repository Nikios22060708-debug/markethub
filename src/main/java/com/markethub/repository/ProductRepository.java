package com.markethub.repository;

import com.markethub.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * JpaSpecificationExecutor используется для динамических фильтров каталога
 * (категория, поиск по названию, диапазон цен) вместе с пагинацией и сортировкой.
 */
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    long countByCategoryId(Long categoryId);
}
