package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.SportResponse;
import com.sportify.catalog.mapper.SportMapper;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.catalog.service.SportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SportServiceImpl implements SportService {

    private final SportRepository sportRepository;
    private final SportMapper sportMapper;

    @Override
    @Transactional(readOnly = true)
    public List<SportResponse> getActiveSports() {
        return sportMapper.toResponses(sportRepository.findByIsActiveTrueOrderByDisplayOrderAscIdAsc());
    }
}
