package com.markethub.service;

import com.markethub.dto.CategoryRequest;
import com.markethub.dto.CategoryResponse;
import com.markethub.entity.Category;
import com.markethub.exception.ConflictException;
import com.markethub.exception.NotFoundException;
import com.markethub.repository.CategoryRepository;
import com.markethub.repository.ProductRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    public List<CategoryResponse> list() {
        return categoryRepository.findAll(Sort.by("name")).stream().map(CategoryResponse::from).toList();
    }

    public CategoryResponse get(Long id) {
        return CategoryResponse.from(getEntity(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new ConflictException("Категория '" + request.name() + "' уже существует");
        }
        return CategoryResponse.from(categoryRepository.save(new Category(request.name(), request.description())));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = getEntity(id);
        if (categoryRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new ConflictException("Категория '" + request.name() + "' уже существует");
        }
        category.setName(request.name());
        category.setDescription(request.description());
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long id) {
        Category category = getEntity(id);
        if (productRepository.countByCategoryId(id) > 0) {
            throw new ConflictException("В категории есть товары — сначала перенесите или удалите их");
        }
        categoryRepository.delete(category);
    }

    Category getEntity(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Категория " + id + " не найдена"));
    }
}
