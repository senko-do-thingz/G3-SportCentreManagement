package com.sportify.core.config;

import java.time.LocalTime;
import java.util.List;
import java.util.regex.Pattern;

/** Reads the shipped migration's data specification rather than maintaining a second fixture. */
final class DemoClassesSeedSource {
    static final String RESOURCE = "db/demo/V29__seed_demo_classes_and_sessions.sql";
    static final String SQL = DemoSeedSource.resource(RESOURCE);
    static final List<ClassSpec> CLASSES = Pattern.compile(
            "\\((\\d+), '(DEMO-CL-[^']+)', '([^']+)', '([^']+)', '([^']+)', '([^']+)', '([^']+)', "
                    + "(\\d+), '([^']+)', '([^']+)', '([^']+)', '([^']+)', (\\d+)\\)")
            .matcher(SQL).results().map(m -> new ClassSpec(Integer.parseInt(m.group(1)), m.group(2), m.group(3),
                    m.group(4), m.group(5), m.group(6), m.group(7), Integer.parseInt(m.group(8)),
                    LocalTime.parse(m.group(9)), LocalTime.parse(m.group(10)), LocalTime.parse(m.group(11)),
                    LocalTime.parse(m.group(12)), Integer.parseInt(m.group(13)))).toList();

    record ClassSpec(int ordinal, String code, String name, String sport, String level, String coach,
                     String facility, int offset, LocalTime coachStart, LocalTime coachEnd,
                     LocalTime selfStart, LocalTime selfEnd, int capacity) { }

    private DemoClassesSeedSource() { }
}
