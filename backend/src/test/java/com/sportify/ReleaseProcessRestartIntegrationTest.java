package com.sportify;

import com.fasterxml.jackson.databind.*;
import com.sportify.support.DisposableUnifyDatabase;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/** Real authenticated HTTP through distinct JVMs on one newly owned SQL fixture. */
@Tag("integration")
class ReleaseProcessRestartIntegrationTest {
    private final ObjectMapper json=new ObjectMapper();
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private String base;
    private Process process;
    private final List<Long> pids=new ArrayList<>();
    private int generation;
    private final Path evidence=Path.of("target/release-process-evidence");

    @AfterEach void stopOwnedProcess() throws Exception {
        if(process!=null && process.isAlive()) {
            process.destroy();if(!process.waitFor(20,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor(20,TimeUnit.SECONDS);}
        }
    }
    private void start(DisposableUnifyDatabase fixture,String time) throws Exception {
        Files.createDirectories(evidence);int port;
        try(var socket=new java.net.ServerSocket(0,0,InetAddress.getLoopbackAddress())){port=socket.getLocalPort();}
        base="http://127.0.0.1:"+port;
        String classpath=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        Path args=evidence.resolve("process-"+(++generation)+".args").toAbsolutePath();
        Files.writeString(args,"-cp\n\""+classpath.replace('\\','/')+"\"\ncom.sportify.support.ReleaseProcessApplication\n--server.address=127.0.0.1\n--server.port="+port+"\n--spring.profiles.active=dev\n--spring.jpa.show-sql=false\n--logging.level.root=WARN\n");
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"@"+args);
        builder.environment().put("DB_URL",fixture.url());builder.environment().put("DB_USERNAME",fixture.user());builder.environment().put("DB_PASSWORD",fixture.password());
        builder.environment().put("SPORTIFY_RELEASE_TEST_TIME",time);
        builder.environment().put("PAYMENT_UNIFIED_DEMO_ENABLED","false");builder.environment().put("PAYMENT_LOCAL_DEMO_ENABLED","false");
        builder.environment().put("PAYMENT_SIMULATOR_ENABLED","false");builder.environment().put("SPORTIFY_PAYMENT_LIVE_INSTRUCTIONS_ENABLED","false");
        builder.redirectErrorStream(true).redirectOutput(evidence.resolve("process-"+generation+".log").toFile());
        process=builder.start();pids.add(process.pid());
        long deadline=System.nanoTime()+Duration.ofMinutes(3).toNanos();boolean ready=false;
        while(process.isAlive() && System.nanoTime()<deadline) {
            try {if(client.send(HttpRequest.newBuilder(URI.create(base+"/actuator/health")).timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()==200){ready=true;break;}}
            catch(java.io.IOException ignored) { }
            Thread.sleep(500);
        }
        assertThat(ready).as("Owned process health; diagnostic log is retained under target/release-process-evidence").isTrue();
    }
    private JsonNode call(String method,String path,String token,Object body,int expected) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+"/api/v1"+path)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json").header("Idempotency-Key",UUID.randomUUID().toString());
        if(token!=null)request.header("Authorization","Bearer "+token);
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(method+" "+path).isEqualTo(expected);
        return response.body().isBlank()?json.nullNode():json.readTree(response.body());
    }
    private String login(String email,String password) throws Exception {return call("POST","/auth/login",null,Map.of("email",email,"password",password),200).path("accessToken").asText();}
    private static long id(JsonNode value){assertThat(value.path("id").isIntegralNumber()).isTrue();return value.path("id").asLong();}

    @Test void RELEASE18_fourRoleStorySurvivesTwoActualApplicationRestarts() throws Exception {
        var fixture=DisposableUnifyDatabase.create();start(fixture,"2026-11-02T03:00:00Z");var jdbc=fixture.jdbc();
        String password="ReleaseOnly!7"+UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT user_account(email,password_hash,full_name,role_id,status) SELECT 'manager@release.test',?,'Release Manager',id,'ACTIVE' FROM role WHERE code='MANAGER'",new BCryptPasswordEncoder().encode(password));
        String manager=login("manager@release.test",password);
        long sport=jdbc.queryForObject("SELECT id FROM sport WHERE code='BADMINTON'",Long.class);
        long age=jdbc.queryForObject("SELECT MIN(id) FROM age_group",Long.class);
        call("POST","/manager/users",manager,Map.of("fullName","Release Reception","email","reception@release.test","phone","0900000101","temporaryPassword",password,"role","RECEPTIONIST","sportIds",List.of()),201);
        long coachId=id(call("POST","/manager/users",manager,Map.of("fullName","Release Coach","email","coach@release.test","phone","0900000102","temporaryPassword",password,"role","COACH","sportIds",List.of(sport)),201));
        String reception=login("reception@release.test",password),coach=login("coach@release.test",password);
        call("GET","/manager/users",manager,null,200);
        call("POST","/auth/register",null,Map.of("fullName","Release Peer","email","peer@release.test","password",password),200);
        String peer=login("peer@release.test",password);
        var created=call("POST","/members",reception,Map.of("fullName","Release Learner","email","learner@release.test","phone","0900000103"),201);
        long member=created.path("userId").asLong();String memberPassword=created.path("temporaryPassword").asText();
        assertThat(member).isPositive();String owner=login("learner@release.test",memberPassword);
        call("PUT","/users/me",owner,Map.of("mainGoal","BUILD_SKILLS","currentLevel","BEGINNER"),200);owner=login("learner@release.test",memberPassword);
        assertThat(call("GET","/users/me",owner,null,200).path("memberCode").asText()).isEqualTo(created.path("memberCode").asText());
        call("GET","/members?query=learner%40release.test",reception,null,200);
        call("POST","/manager/card-tiers",manager,Map.of("code","RELEASE_TIER","name","Release Tier","price",900000,"durationMonths",12,"discountPercentage",10,"tierRank",100,"active",true),201);
        long facility=id(call("POST","/manager/catalog/facilities",manager,Map.of("code","RELEASE_ROOM","name","Release Court","facilityType","COURT","sportId",sport,"capacity",10,"active",true),201));
        long classId=id(call("POST","/manager/classes",manager,Map.of("name","Release Badminton","sportId",sport,"ageGroupId",age,"level","BEGINNER","maxMembers",10,"coachId",coachId),201));
        long session=id(call("POST","/manager/classes/"+classId+"/sessions",manager,Map.of("date","2026-11-02","startTime","11:00:00","endTime","12:00:00","facilityId",facility,"coachId",coachId,"capacity",10,"trainingType","COACH_LED","status","PUBLISHED"),201).get(0));
        long next=id(call("POST","/manager/classes/"+classId+"/sessions",manager,Map.of("date","2026-11-03","startTime","11:00:00","endTime","12:00:00","facilityId",facility,"coachId",coachId,"capacity",10,"trainingType","COACH_LED","status","PUBLISHED"),201).get(0));
        long pkg=id(call("POST","/packages",manager,Map.of("code","RELEASE_PACKAGE","name","Release Coached Package","sportId",sport,"trainingFormat","COACH_LED","durationDays",30,"sessionCount",10,"sessionMinutes",60,"priceAmount",1000),201));
        var registration=call("POST","/packages/registrations",reception,Map.of("memberId",member,"packageId",pkg,"paymentMethod","CASH","amountReceived",1000),201);
        long reg=id(registration);long payment=registration.path("paymentId").asLong();assertThat(registration.path("status").asText()).isEqualTo("ACTIVE");
        call("GET","/invoices/by-payment/"+payment,reception,null,200);call("GET","/invoices/by-payment/"+payment,owner,null,200);call("GET","/invoices/by-payment/"+payment,peer,null,403);
        call("GET","/members/"+member+"/overview",reception,null,200);
        call("GET","/sessions/"+session+"/eligibility?memberId="+member,reception,null,200);
        long booking=id(call("POST","/bookings",reception,Map.of("memberId",member,"sessionId",session,"packageRegistrationId",reg),201));
        long future=id(call("POST","/bookings",owner,Map.of("sessionId",next,"packageRegistrationId",reg),201));
        call("GET","/bookings/my",owner,null,200);
        var detail=call("GET","/member/training/classes/"+classId,owner,null,200);assertThat(detail.path("learningClass").path("name").asText()).isEqualTo("Release Badminton");
        call("GET","/coach/training/classes/"+classId+"/members",coach,null,200);
        assertThat(call("GET","/coach/training/classes/"+classId+"/members/"+member+"/profile",coach,null,200).path("mainGoal").asText()).isEqualTo("BUILD_SKILLS");
        call("PUT","/manager/sessions/"+next,manager,Map.of("date","2026-11-04","startTime","11:00:00","endTime","12:00:00","facilityId",facility,"coachId",coachId,"capacity",10,"expectedRevision",0,"rescheduleReason","Release acceptance reschedule"),200);
        assertThat(call("GET","/member/notifications",owner,null,200).path("notices").size()).isEqualTo(1);
        call("DELETE","/bookings/"+future,reception,null,200);
        var issued=call("POST","/member/check-in-pass",owner,null,200);String pass=issued.path("token").asText();
        assertThat(call("POST","/reception/check-in-pass/redeem",reception,Map.of("token",pass,"bookingId",booking),200).path("result").asText()).isEqualTo("ALLOWED");
        long plan=id(call("POST","/coach/training/plans",coach,Map.of("classId",classId,"memberId",member,"bookingId",booking,"title","Release personal plan","steps",List.of(Map.of("title","Footwork","durationMinutes",10,"instructions","Follow Coach guidance"))),201));
        call("POST","/coach/training/class-plans",coach,Map.of("classId",classId,"title","Release class plan","steps",List.of(Map.of("title","Warm up","durationMinutes",5))),201);
        call("POST","/coach/training/attendance",coach,Map.of("bookingId",booking,"status","PRESENT","notes","Actual session participation"),201);
        call("POST","/coach/training/notices",coach,Map.of("classId",classId,"type","HOMEWORK","title","Release homework","content","Practise the assigned steps","dueDate","2026-11-03"),201);
        call("GET","/member/training/notices",owner,null,200);
        long ticket=id(call("POST","/reception/support-requests",reception,Map.of("memberId",member,"category","BOOKING","subject","Class location","description","Please confirm class location","bookingId",booking),201));
        call("PUT","/reception/support-requests/"+ticket+"/status",reception,Map.of("status","IN_PROGRESS"),200);
        call("GET","/member/support-requests",owner,null,200);
        var online=call("POST","/packages/registrations",owner,Map.of("packageId",pkg,"startDate","2026-12-02","paymentMethod","BANK_TRANSFER"),201);
        long pendingPayment=online.path("paymentId").asLong();
        var transfer=call("GET","/payments/"+pendingPayment+"/vietqr-instructions",owner,null,200);assertThat(transfer.path("payable").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT payment_status FROM payment WHERE id=?",String.class,pendingPayment)).isEqualTo("PENDING");
        call("PUT","/packages/registrations/"+id(online)+"/cancel",owner,null,200);
        call("GET","/manager/reports/operations?from=2026-11-02&to=2026-11-02",manager,null,200);
        call("GET","/manager/activity-logs",manager,null,200);
        stopOwnedProcess();start(fixture,"2026-11-02T06:00:00Z");
        reception=login("reception@release.test",password);coach=login("coach@release.test",password);owner=login("learner@release.test",memberPassword);
        call("POST","/reception/check-in-pass/redeem",reception,Map.of("token",pass,"bookingId",booking),409);
        call("GET","/member/training/plans/"+plan,owner,null,200);
        long result=id(call("POST","/coach/training/results",coach,Map.of("sessionId",session,"memberId",member,"notes","Release persisted notes-only outcome","scores",List.of()),201));
        call("POST","/coach/training/plans/"+plan+"/feedback",coach,Map.of("rating",4,"comments","Release persisted Coach feedback"),201);
        stopOwnedProcess();start(fixture,"2026-11-02T06:00:00Z");owner=login("learner@release.test",memberPassword);
        assertThat(call("GET","/member/training/results/"+result,owner,null,200).path("result").path("notes").asText()).isEqualTo("Release persisted notes-only outcome");
        var progress=call("GET","/member/training/progress",owner,null,200);assertThat(progress.path("sessions").size()).isEqualTo(1);assertThat(progress.path("feedback").size()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM check_in WHERE booking_id=? AND result='ALLOWED'",Integer.class,booking)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance_record WHERE booking_id=?",Integer.class,booking)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT remaining_sessions FROM sport_package_registration WHERE id=?",Integer.class,reg)).isEqualTo(9);
        assertThat(new HashSet<>(pids)).hasSize(3);
        System.out.println("RELEASE18 actual authenticated four-role HTTP/SQL story: three distinct JVMs "+pids+"; redeemed pass replay409 after restart; saved result/feedback read after second restart; arrival1, attendance1, credits9. Owned fixture "+fixture.name());
    }
}
