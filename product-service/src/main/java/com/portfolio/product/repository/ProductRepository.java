package com.portfolio.product.repository;

import com.portfolio.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Product 도메인의 영속성 계층.
 *
 * <p>Spring Data JPA의 {@link JpaRepository}를 상속받아
 * 기본 CRUD/페이징/정렬 메서드를 자동으로 제공한다.
 * 별도의 구현 클래스를 만들 필요는 없다 — Spring이 런타임에
 * 프록시 빈을 생성해서 주입한다.
 *
 * <p>{@code @Repository} 어노테이션은 사실 생략 가능하지만
 * (인터페이스 자체로 Spring이 인식),
 * 명시적으로 붙여 의도를 드러내고 IDE 탐색을 쉽게 한다.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    // 기본 CRUD는 JpaRepository가 모두 제공:
    //   findAll(), findById(Long), save(Product), deleteById(Long), existsById(Long), ...
    //
    // 향후 필요해지면 메서드 시그니처만 선언하여 쿼리 메서드 추가:
    //   List<Product> findByNameContaining(String keyword);
    //   List<Product> findByStockLessThan(int threshold);
}