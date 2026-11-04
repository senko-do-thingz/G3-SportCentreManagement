package com.sportify.catalog.service;

import com.sportify.catalog.dto.CatalogMaintenanceRequest.*;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.*;
import com.sportify.core.audit.*;
import com.sportify.core.exception.*;
import com.sportify.identity.entity.UserAccount;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class CatalogMaintenanceService {
    private final SportRepository sports;
    private final FacilityRepository facilities;
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AuditService audit;

    public List<SportView> sports() { return sports.findAll().stream().map(this::sport).toList(); }
    public List<FacilityView> facilities() { return facilities.findAll().stream().map(this::facility).toList(); }

    // Serialize maintenance creates without holding every sport row while a scheduler locks a facility.
    private List<Sport> lockCatalog() {
        Integer acquired=jdbc.queryForObject("""
                SET NOCOUNT ON;
                DECLARE @result INT;
                IF @@TRANCOUNT=0 SELECT TOP(1) @result=id FROM sport;
                EXEC @result=sys.sp_getapplock @Resource='sportify.catalog-maintenance',
                    @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
                SELECT @result;
                """,Integer.class);
        if(acquired==null || acquired<0) throw new ConflictException("Catalog maintenance is busy; retry the request");
        return sports.findAll();
    }
    @Transactional
    public SportView saveSport(Long id, SportWrite request, UserAccount actor) {
        var current=lockCatalog();
        Sport target=id==null?new Sport():current.stream().filter(s->s.getId().equals(id)).findFirst()
                .orElseThrow(()->new ResourceNotFoundException("Sport not found"));
        if(id!=null && !target.getCode().equals(request.code()))
            throw new ConflictException("Sport code is immutable because session identities depend on it");
        if(current.stream().anyMatch(s->!Objects.equals(s.getId(),id) &&
                (s.getCode().equalsIgnoreCase(request.code()) || s.getName().trim().equalsIgnoreCase(request.name().trim()))))
            throw new ConflictException("Sport code and name must be unique");
        if(id!=null) em.lock(target,LockModeType.PESSIMISTIC_WRITE);
        if(id!=null && !request.active() && (future("sport_id",id)>0
                || jdbc.queryForObject("SELECT COUNT(*) FROM sport_class WHERE sport_id=? AND is_active=1",Integer.class,id)>0))
            throw new ConflictException("Cancel or complete scheduled sessions before deactivating the sport");
        target.setCode(request.code()); target.setName(request.name().trim()); target.setVenueType(request.venueType());
        target.setDescription(request.description()); target.setDisplayOrder(request.displayOrder()); target.setIsActive(request.active());
        target=sports.saveAndFlush(target);
        record(actor,id==null?"SPORT_CREATED":"SPORT_UPDATED","SPORT",target.getId(),target.getCode());
        return sport(target);
    }
    @Transactional
    public FacilityView saveFacility(Long id, FacilityWrite request, UserAccount actor) {
        lockCatalog();
        Facility target=id==null?new Facility():facilities.findForScheduleUpdate(id)
                .orElseThrow(()->new ResourceNotFoundException("Facility not found"));
        if(id!=null && !target.getCode().equals(request.code())) throw new ConflictException("Facility code is immutable");
        if(facilities.findAll().stream().anyMatch(f->!Objects.equals(f.getId(),id) &&
                (f.getCode().equalsIgnoreCase(request.code()) || f.getName().trim().equalsIgnoreCase(request.name().trim()))))
            throw new ConflictException("Facility code and name must be unique");
        Sport selected=request.sportId()==null?null:sports.findById(request.sportId())
                .orElseThrow(()->new ResourceNotFoundException("Sport not found"));
        if(selected!=null && !Boolean.TRUE.equals(selected.getIsActive())) throw new BusinessRuleException("Facility sport must be active");
        if(id!=null && future("facility_id",id)>0) {
            Long previous=target.getSport()==null?null:target.getSport().getId();
            if(!request.active() || !Objects.equals(previous,request.sportId()) || target.getFacilityType()!=request.facilityType())
                throw new ConflictException("Scheduled facility assignments must be preserved");
            if(jdbc.queryForObject("SELECT COUNT(*) FROM class_session WHERE facility_id=? AND session_date>=? AND status IN ('DRAFT','PUBLISHED') AND capacity>?",Integer.class,id,LocalDate.now(clock),request.capacity())>0)
                throw new ConflictException("Facility capacity cannot be below a scheduled session capacity");
        }
        target.setCode(request.code()); target.setName(request.name().trim()); target.setFacilityType(request.facilityType());
        target.setSport(selected); target.setCapacity(request.capacity()); target.setIsActive(request.active());
        target=facilities.saveAndFlush(target);
        record(actor,id==null?"FACILITY_CREATED":"FACILITY_UPDATED","FACILITY",target.getId(),target.getCode());
        return facility(target);
    }
    private int future(String column,long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM class_session WHERE "+column+"=? AND session_date>=? AND status IN ('DRAFT','PUBLISHED')",Integer.class,id,LocalDate.now(clock));
    }
    private SportView sport(Sport s) { return new SportView(s.getId(),s.getCode(),s.getName(),s.getVenueType(),s.getDescription(),s.getDisplayOrder(),Boolean.TRUE.equals(s.getIsActive())); }
    private FacilityView facility(Facility f) { return new FacilityView(f.getId(),f.getCode(),f.getName(),f.getFacilityType(),f.getSport()==null?null:f.getSport().getId(),f.getCapacity(),Boolean.TRUE.equals(f.getIsActive())); }
    private void record(UserAccount actor,String action,String type,long id,String code) {
        audit.record(AuditEvent.builder().actor(actor).action(action).entityType(type).entityId(id).entityCode(code).summary("Manager catalog maintenance").build());
    }
}
