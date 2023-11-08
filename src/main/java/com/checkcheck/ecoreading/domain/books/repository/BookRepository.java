package com.checkcheck.ecoreading.domain.books.Repository;

import com.checkcheck.ecoreading.domain.books.entity.Books;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Books, Long> {

    List<Books> findAll();

}
