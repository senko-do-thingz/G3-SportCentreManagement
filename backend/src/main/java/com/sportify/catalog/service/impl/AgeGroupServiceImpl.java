package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.AgeGroupResponse;
import com.sportify.catalog.repository.AgeGroupRepository;
import com.sportify.catalog.service.AgeGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgeGroupServiceImpl implements AgeGroupService {

    private final AgeGroupRepository ageGroupRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AgeGroupResponse> getActiveAgeGroups() {
        return ageGroupRepository.findByIsActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(group -> new AgeGroupResponse(
                        group.getId(), group.getCode(), group.getLabel(), group.getMinAge(), group.getMaxAge()))
                .toList();
    }
}
