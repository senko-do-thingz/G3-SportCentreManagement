package com.sportify.catalog.service;

import com.sportify.catalog.dto.TrainingWriteResponse.DrillDraft;
import java.util.*;

/** Conservative application templates for human Coach review, never a medical or autonomous plan. */
final class TrainingDrillCatalog {
    private TrainingDrillCatalog() {}
    private record Template(String title,String goal,String focus,String instructions) {}
    private static final Map<String,List<Template>> TEMPLATES=Map.of(
        "FOOTBALL",List.of(
            new Template("Controlled short passing","Passing accuracy","passing","Use a target and distance selected by your coach. Practise short passes at a comfortable pace, pausing to reset. Ask your coach to check control before increasing distance."),
            new Template("Walking ball control","Ball control","control","In a clear space selected by your coach, move the ball around widely spaced markers at walking pace. Pause between rounds and review control with your coach.")),
        "BADMINTON",List.of(
            new Template("Walking shadow footwork","Court movement","footwork","Without a shuttle, walk between court positions demonstrated by your coach. Return to the starting position between repetitions. Ask your coach to check balance and spacing."),
            new Template("Controlled racket contact","Racket control","control","Use the feed and target demonstrated by your coach. Practise gentle, controlled contacts and reset between attempts. Review consistency before increasing pace.")),
        "BASKETBALL",List.of(
            new Template("Stationary ball control","Ball handling","control","In a clear area selected by your coach, practise stationary dribbling with each hand at a comfortable pace. Pause to reset and ask your coach to check control."),
            new Template("Short target passing","Passing accuracy","passing","Use a target and short distance selected by your coach. Practise controlled passes, pausing between attempts. Review accuracy with your coach before changing distance.")));

    static List<DrillDraft> suggest(String sport,String level,String feedback,String metric,String goal) {
        if(!Set.of("BEGINNER","INTERMEDIATE","ADVANCED").contains(level))return List.of();
        var templates=TEMPLATES.getOrDefault(sport,List.of());
        String feedbackText=feedback==null?"":feedback.toLowerCase(Locale.ROOT);
        String metricText=metric==null?"":metric.toLowerCase(Locale.ROOT);
        int duration=level.equals("BEGINNER")?5:level.equals("INTERMEDIATE")?7:8;
        var ranked=new ArrayList<>(templates);
        ranked.sort(Comparator.comparingInt((Template item)->
            (feedbackText.contains(item.focus())?2:0)+(metricText.contains(item.focus())?1:0)).reversed());
        return ranked.stream().map(item->{
            String reason="Matches "+sport+" and class level "+level+". Coach review is required.";
            if(feedbackText.contains(item.focus()))reason+=" Matches the coach feedback focus: "+item.focus()+".";
            if(metricText.contains(item.focus()))reason+=" Matches a recorded assessment metric: "+item.focus()+".";
            if(goal!=null && Set.of("BUILD_SKILLS","COMPETITION","IMPROVE_FITNESS").contains(goal))reason+=" Saved member goal: "+goal+".";
            return new DrillDraft(item.title(),item.goal(),duration,item.instructions(),reason,level);
        }).toList();
    }
}
