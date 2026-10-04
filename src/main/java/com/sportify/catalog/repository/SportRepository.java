package com.sportify.catalog.repository;

import com.sportify.catalog.entity.Sport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SportRepository extends JpaRepository<Sport, Long> {

    /** Active sports for public pages; {@code id} breaks ties in {@code display_order}. */
    List<Sport> findByIsActiveTrueOrderByDisplayOrderAscIdAsc();
}
