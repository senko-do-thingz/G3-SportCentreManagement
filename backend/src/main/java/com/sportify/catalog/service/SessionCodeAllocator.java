package com.sportify.catalog.service;

import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionCodeAllocator {
    private static final Map<String,String> SPORTS = Map.of("FOOTBALL","FB", "SWIMMING","SW",
            "BADMINTON","BM", "VOLLEYBALL","VB", "BASKETBALL","BK", "TENNIS","TE");
    private static final DateTimeFormatter PREFIX = DateTimeFormatter.ofPattern("ddMMyy", Locale.ROOT);
    private final JdbcTemplate jdbc;

    public static String sportCode(String sport) {
        String code = SPORTS.get(sport == null ? "" : sport);
        if (code == null) throw new BusinessRuleException("Session ID requires one of the six canonical sports");
        return code;
    }

    public static void validateDate(LocalDate date) {
        if (date == null || date.getYear() < 2000 || date.getYear() > 2099)
            throw new BusinessRuleException("Session ID supports scheduled years 2000-2099 only");
    }

    public static String format(LocalDate date, String sport, int allocation) {
        validateDate(date);
        if (allocation < 1 || allocation > 99)
            throw new BusinessRuleException("At most 99 Session IDs can be allocated per date and sport");
        return PREFIX.format(date) + sportCode(sport) + String.format(Locale.ROOT, "%02d", allocation);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String reserve(LocalDate date, String sport) {
        validateDate(date);
        String code = sportCode(sport);
        // Serialize the small allocation section, including absent-key and multi-date batches.
        // A single database-scoped resource avoids cycles between adjacent missing key ranges.
        Integer acquired = jdbc.queryForObject("""
                SET NOCOUNT ON;
                DECLARE @result INT;
                -- A table read starts a lazy JDBC implicit transaction; EXEC alone does not.
                IF @@TRANCOUNT=0 SELECT TOP(1) @result=last_value FROM session_code_counter;
                EXEC @result=sys.sp_getapplock @Resource=?, @LockMode='Exclusive',
                    @LockOwner='Transaction', @LockTimeout=10000;
                SELECT @result;
                """, Integer.class, "sportify.session-identity");
        if (acquired == null || acquired < 0)
            throw new ConflictException("Session ID allocation is busy. Reload and retry the complete request.");
        // The indexed key-range lock also protects a missing counter until transaction completion.
        var current = jdbc.query("SELECT last_value FROM session_code_counter WITH (UPDLOCK,HOLDLOCK) "
                + "WHERE session_date=? AND sport_code=?", (rs,row) -> rs.getInt(1), date, code);
        int next = current.isEmpty() ? 1 : current.getFirst() + 1;
        String identity = format(date, sport, next);
        if (current.isEmpty())
            jdbc.update("INSERT INTO session_code_counter(session_date,sport_code,last_value) VALUES (?,?,?)", date, code, next);
        else
            jdbc.update("UPDATE session_code_counter SET last_value=? WHERE session_date=? AND sport_code=?", next, date, code);
        return identity;
    }
}
