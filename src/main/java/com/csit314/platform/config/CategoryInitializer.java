package com.csit314.platform.config;

import com.csit314.platform.model.Category;
import com.csit314.platform.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

// PM-01 covers category management going forward; this just seeds a starter list
// so the designer project form has options before Platform Management builds that feature
@Component
public class CategoryInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CategoryInitializer.class);
    private static final List<String> DEFAULTS = List.of(
            "Kitchen", "Living Room", "Bedroom", "Bathroom", "Minimal", "Budget"
    );

    private final CategoryRepository categoryRepository;

    public CategoryInitializer(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (categoryRepository.count() > 0) {
            return;
        }
        DEFAULTS.forEach(name -> categoryRepository.save(new Category(name)));
        log.info("Seeded {} default categories", DEFAULTS.size());
    }
}
