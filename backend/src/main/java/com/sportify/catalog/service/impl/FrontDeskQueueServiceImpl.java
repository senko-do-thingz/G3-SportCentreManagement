package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.FrontDeskCardResponse;
import com.sportify.catalog.dto.FrontDeskPackageResponse;
import com.sportify.catalog.entity.CardStatus;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.mapper.FrontDeskMapper;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.service.FrontDeskQueueService;
import com.sportify.core.common.FrontDeskPaging;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FrontDeskQueueServiceImpl implements FrontDeskQueueService {
    private final MemberCardRepository cards;
    private final SportPackageRegistrationRepository registrations;
    private final FrontDeskMapper mapper;

    @Override
    public Page<FrontDeskCardResponse> pendingCards(Pageable pageable) {
        return cards.findByStatus(CardStatus.PENDING_PAYMENT, FrontDeskPaging.queue(pageable)).map(mapper::toCard);
    }

    @Override
    public Page<FrontDeskPackageResponse> pendingPackages(Pageable pageable) {
        return registrations.findByStatus(PackageRegistrationStatus.PENDING_PAYMENT, FrontDeskPaging.queue(pageable))
                .map(mapper::toPackage);
    }
}
