package com.sportify.catalog.mapper;

import com.sportify.catalog.dto.SportResponse;
import com.sportify.catalog.entity.Sport;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SportMapper {

    SportResponse toResponse(Sport sport);

    List<SportResponse> toResponses(List<Sport> sports);
}
