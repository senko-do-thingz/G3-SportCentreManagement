package com.sportify.catalog.service;

import com.sportify.catalog.dto.FrontDeskCardResponse;
import com.sportify.catalog.dto.FrontDeskPackageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FrontDeskQueueService {
    Page<FrontDeskCardResponse> pendingCards(Pageable pageable);
    Page<FrontDeskPackageResponse> pendingPackages(Pageable pageable);
}
