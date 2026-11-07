package com.sportify.catalog.service;

import com.sportify.catalog.dto.TrainingWriteResponse.DrillDraft;
import java.util.*;

/** Conservative application templates for human Coach review, never a medical or autonomous plan. */
final class TrainingDrillCatalog {
    private TrainingDrillCatalog() {}
    private record Template(String title,String goal,String focus,String preferredGoal,String instructions) {}
    private static final Map<String,List<Template>> TEMPLATES=Map.of(
        "FOOTBALL",List.of(
            new Template("Controlled short passing","Passing accuracy","passing","BUILD_SKILLS","Use a target and distance selected by your coach. Practise short passes at a comfortable pace, pausing to reset. Ask your coach to check control before increasing distance."),
            new Template("Walking ball control","Ball control","control","IMPROVE_FITNESS","In a clear space selected by your coach, move the ball around widely spaced markers at walking pace. Pause between rounds and review control with your coach."),
            new Template("Coach-led passing decisions","Passing decisions","passing","COMPETITION","With your coach, choose between two safe passing targets after a verbal cue. Reset after each attempt and review the decision before adding pace.")),
        "BADMINTON",List.of(
            new Template("Walking shadow footwork","Court movement","footwork","IMPROVE_FITNESS","Without a shuttle, walk between court positions demonstrated by your coach. Return to the starting position between repetitions. Ask your coach to check balance and spacing."),
            new Template("Controlled racket contact","Racket control","control","BUILD_SKILLS","Use the feed and target demonstrated by your coach. Practise gentle, controlled contacts and reset between attempts. Review consistency before increasing pace."),
            new Template("Coach-led placement choices","Shot placement","control","COMPETITION","From a safe feed, choose one of two targets called by your coach. Reset between attempts and review placement before increasing pace.")),
        "BASKETBALL",List.of(
            new Template("Stationary ball control","Ball handling","control","IMPROVE_FITNESS","In a clear area selected by your coach, practise stationary dribbling with each hand at a comfortable pace. Pause to reset and ask your coach to check control."),
            new Template("Short target passing","Passing accuracy","passing","BUILD_SKILLS","Use a target and short distance selected by your coach. Practise controlled passes, pausing between attempts. Review accuracy with your coach before changing distance."),
            new Template("Coach-led passing choices","Passing decisions","passing","COMPETITION","Choose between two marked passing targets after your coach's cue. Reset and review accuracy before changing pace.")),
        "SWIMMING",List.of(
            new Template("Supported stroke practice","Stroke control","stroke","BUILD_SKILLS","In a supervised lane, practise the stroke pattern demonstrated by your coach for short lengths. Stop at the wall and review technique between attempts."),
            new Template("Comfortable supervised lengths","Steady movement","breathing","IMPROVE_FITNESS","Swim short lengths at a comfortable pace in the assigned lane. Rest at the wall and let your coach set the next length."),
            new Template("Coach-led turn sequence","Turn decisions","stroke","COMPETITION","Practise the turn sequence demonstrated by your coach in a supervised lane. Reset at the wall and review timing before repeating.")),
        "VOLLEYBALL",List.of(
            new Template("Controlled forearm passing","Passing control","passing","BUILD_SKILLS","Use gentle feeds and a target chosen by your coach. Reset after each pass and review body position before increasing pace."),
            new Template("Walking court movement","Court movement","movement","IMPROVE_FITNESS","Walk between marked court positions at a comfortable pace without a ball. Pause between rounds and review spacing with your coach."),
            new Template("Coach-led receive choices","Receive decisions","defense","COMPETITION","Respond to one of two safe feeds selected by your coach, then reset. Review positioning before adding pace.")),
        "TENNIS",List.of(
            new Template("Controlled racket contact","Racket control","racket","BUILD_SKILLS","Use a gentle feed and target selected by your coach. Reset after each contact and review control before increasing pace."),
            new Template("Walking court positions","Court movement","footwork","IMPROVE_FITNESS","Walk between widely spaced court markers without a ball. Pause between rounds and review balance with your coach."),
            new Template("Coach-led placement choices","Placement decisions","control","COMPETITION","Choose between two targets after your coach's cue from a safe feed. Reset and review placement before repeating.")));

    static List<DrillDraft> suggest(String sport,String sportName,String level,String feedback,String metric,String goal) {
        if(level==null || sport==null || !Set.of("BEGINNER","INTERMEDIATE","ADVANCED").contains(level))return List.of();
        var templates=TEMPLATES.getOrDefault(sport,List.of());
        String feedbackText=feedback==null?"":feedback.toLowerCase(Locale.ROOT);
        String metricText=metric==null?"":metric.toLowerCase(Locale.ROOT);
        int duration=level.equals("BEGINNER")?5:level.equals("INTERMEDIATE")?7:8;
        var ranked=new ArrayList<>(templates);
        ranked.sort(Comparator.comparingInt((Template item)->
            (feedbackText.contains(item.focus())?4:0)+(metricText.contains(item.focus())?2:0)
                +(item.preferredGoal().equals(goal)?1:0)).reversed());
        return ranked.stream().map(item->{
            String label=sportName==null || sportName.isBlank()?sport:sportName;
            String reason="Matches the "+label+" class at "+level.toLowerCase(Locale.ROOT)+" level. Coach review is required.";
            if(feedbackText.contains(item.focus()))reason+=" Matches the coach feedback focus: "+item.focus()+".";
            if(metricText.contains(item.focus()))reason+=" Matches a recorded assessment metric: "+item.focus()+".";
            if(item.preferredGoal().equals(goal))reason+=" Prioritised for the saved training goal.";
            if(goal!=null && Set.of("BUILD_SKILLS","COMPETITION","IMPROVE_FITNESS").contains(goal))reason+=" Saved member goal: "+goal+".";
            return new DrillDraft(item.title(),item.goal(),duration,item.instructions(),reason,level);
        }).toList();
    }
}
