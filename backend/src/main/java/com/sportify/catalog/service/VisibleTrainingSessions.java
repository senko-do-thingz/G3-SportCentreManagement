package com.sportify.catalog.service;

import com.sportify.catalog.repository.ClassSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** Uses the same authoritative predicate as session detail, including facility and coach qualification. */
@Component
@RequiredArgsConstructor
public class VisibleTrainingSessions {
    private final ClassSessionRepository sessions;
    private final Clock clock;
    public Set<Long> filter(Collection<Long> ids) {
        var distinct=new ArrayList<>(new LinkedHashSet<>(ids));
        var visible=new HashSet<Long>();
        // Bound SQL Server parameters while checking every candidate before ranking or limiting.
        for(int offset=0;offset<distinct.size();offset+=500)
            visible.addAll(sessions.findVisibleIds(distinct.subList(offset,Math.min(offset+500,distinct.size())),
                LocalDate.now(clock),LocalTime.now(clock)));
        return visible;
    }
}
