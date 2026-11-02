package com.sportify.catalog.controller;
import com.sportify.catalog.dto.ClassLearningResponse.*;
import com.sportify.catalog.service.ClassLearningService;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ClassLearningController {
    private final ClassLearningService service;
    public record Ask(@NotNull @Positive Long classId,@NotBlank @Size(max=200) String subject,@NotBlank @Size(max=2000) String body) {}
    public record Reply(@NotBlank @Size(max=2000) String body) {}
    private static final String MEMBER="hasRole('MEMBER') and principal.enabled";
    private static final String COACH="hasRole('COACH') and principal.enabled";
    @GetMapping("/member/training/classes") @PreAuthorize(MEMBER)
    public List<LearningClass> classes(@AuthenticationPrincipal CustomUserDetails user){return service.memberClasses(user.getUserAccount().getId());}
    @GetMapping("/member/training/classes/{id}") @PreAuthorize(MEMBER)
    public ClassDetail detail(@PathVariable long id,@AuthenticationPrincipal CustomUserDetails user){return service.memberClass(user.getUserAccount().getId(),id);}
    @GetMapping("/member/training/questions") @PreAuthorize(MEMBER)
    public List<Question> memberQuestions(@AuthenticationPrincipal CustomUserDetails user){return service.questions(user.getUserAccount().getId(),false);}
    @PostMapping("/member/training/questions") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize(MEMBER)
    public Map<String,Long> ask(@Valid @RequestBody Ask request,@AuthenticationPrincipal CustomUserDetails user){return Map.of("id",service.ask(user.getUserAccount().getId(),request.classId(),request.subject(),request.body()));}
    @GetMapping("/member/training/questions/{id}") @PreAuthorize(MEMBER)
    public Discussion memberDiscussion(@PathVariable long id,@AuthenticationPrincipal CustomUserDetails user){return service.discussion(user.getUserAccount().getId(),false,id);}
    @PostMapping("/member/training/questions/{id}/messages") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize(MEMBER)
    public Map<String,Long> memberReply(@PathVariable long id,@Valid @RequestBody Reply request,@AuthenticationPrincipal CustomUserDetails user){return Map.of("id",service.reply(user.getUserAccount().getId(),false,id,request.body()));}
    @GetMapping("/coach/training/questions") @PreAuthorize(COACH)
    public List<Question> coachQuestions(@AuthenticationPrincipal CustomUserDetails user){return service.questions(user.getUserAccount().getId(),true);}
    @GetMapping("/coach/training/questions/{id}") @PreAuthorize(COACH)
    public Discussion coachDiscussion(@PathVariable long id,@AuthenticationPrincipal CustomUserDetails user){return service.discussion(user.getUserAccount().getId(),true,id);}
    @PostMapping("/coach/training/questions/{id}/messages") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize(COACH)
    public Map<String,Long> coachReply(@PathVariable long id,@Valid @RequestBody Reply request,@AuthenticationPrincipal CustomUserDetails user){return Map.of("id",service.reply(user.getUserAccount().getId(),true,id,request.body()));}
    @GetMapping("/member/training/guidance") @PreAuthorize(MEMBER)
    public Guidance guidance(@RequestParam(defaultValue="") @Size(max=200) String query,@AuthenticationPrincipal CustomUserDetails user){return service.guidance(user.getUserAccount().getId(),query);}
}
