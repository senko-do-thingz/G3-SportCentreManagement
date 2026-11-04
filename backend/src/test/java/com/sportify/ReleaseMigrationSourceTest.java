package com.sportify;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class ReleaseMigrationSourceTest {
    @Test void everyDefaultAndOptInDemoMigrationHasAUniqueVersion() throws Exception {
        var versions=new TreeMap<Integer,List<String>>();
        for(String location:List.of("identity","catalog","../demo")) {
            var directory=Path.of("src/main/resources/db/migration",location).normalize();
            try(var paths=Files.list(directory)) {
                paths.filter(p->p.getFileName().toString().matches("V[0-9]+__.*\\.sql")).forEach(path->{
                    int version=Integer.parseInt(path.getFileName().toString().split("__")[0].substring(1));
                    versions.computeIfAbsent(version,key->new ArrayList<>()).add(path.toString());
                });
            }
        }
        assertThat(versions.entrySet().stream().filter(e->e.getValue().size()>1).toList()).isEmpty();
    }
}
