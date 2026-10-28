package com.uykutakip.sync;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public final class SyncSnapshot {
    public static final int PROTOCOL_VERSION = 7;

    public final long generatedAtMillis;
    public final Long activeSleepStartMillis;
    public final long activeSleepUpdatedAtMillis;
    public final List<Sleep> sleeps;
    public final Meal activeMeal;
    public final List<Meal> meals;
    public final List<Water> water;
    public final int waterGoalMl;
    public final long waterGoalUpdatedAtMillis;
    public final List<Agenda> agenda;
    public final List<AgendaTemplate> agendaTemplates;
    public final List<AgendaCategory> agendaCategories;
    public final List<HiddenAgendaSuggestion> hiddenAgendaSuggestions;
    public final List<PrivateActivity> privateActivities;
    public final List<Deletion> deletions;

    public SyncSnapshot(long generatedAtMillis, Long activeSleepStartMillis, long activeSleepUpdatedAtMillis, List<Sleep> sleeps,
                        Meal activeMeal, List<Meal> meals, List<Water> water, int waterGoalMl,
                        long waterGoalUpdatedAtMillis, List<Agenda> agenda, List<PrivateActivity> privateActivities,
                        List<Deletion> deletions) {
        this(generatedAtMillis, activeSleepStartMillis, activeSleepUpdatedAtMillis, sleeps, activeMeal, meals, water,
                waterGoalMl, waterGoalUpdatedAtMillis, agenda, List.of(), List.of(), List.of(), privateActivities, deletions);
    }

    public SyncSnapshot(long generatedAtMillis, Long activeSleepStartMillis, long activeSleepUpdatedAtMillis, List<Sleep> sleeps,
                        Meal activeMeal, List<Meal> meals, List<Water> water, int waterGoalMl,
                        long waterGoalUpdatedAtMillis, List<Agenda> agenda, List<AgendaTemplate> agendaTemplates,
                        List<AgendaCategory> agendaCategories, List<HiddenAgendaSuggestion> hiddenAgendaSuggestions,
                        List<PrivateActivity> privateActivities, List<Deletion> deletions) {
        this.generatedAtMillis = generatedAtMillis;
        this.activeSleepStartMillis = activeSleepStartMillis;
        this.activeSleepUpdatedAtMillis = activeSleepUpdatedAtMillis;
        this.sleeps = immutableCopy(sleeps);
        this.activeMeal = activeMeal;
        this.meals = immutableCopy(meals);
        this.water = immutableCopy(water);
        this.waterGoalMl = waterGoalMl;
        this.waterGoalUpdatedAtMillis = waterGoalUpdatedAtMillis;
        this.agenda = immutableCopy(agenda);
        this.agendaTemplates = immutableCopy(agendaTemplates);
        this.agendaCategories = immutableCopy(agendaCategories);
        this.hiddenAgendaSuggestions = immutableCopy(hiddenAgendaSuggestions);
        this.privateActivities = immutableCopy(privateActivities);
        this.deletions = immutableCopy(deletions);
    }

    private static <T> List<T> immutableCopy(List<T> items) {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    public static final class Sleep {
        public final long id, startMillis, endMillis, updatedAtMillis;
        public Sleep(long id, long startMillis, long endMillis, long updatedAtMillis) {
            this.id = id; this.startMillis = startMillis; this.endMillis = endMillis; this.updatedAtMillis = updatedAtMillis;
        }
    }

    public static final class Meal {
        public final long id, startMillis, endMillis, updatedAtMillis;
        public final String type, itemsJson;
        public final boolean quick;
        public Meal(long id, String type, long startMillis, long endMillis, String itemsJson, boolean quick, long updatedAtMillis) {
            this.id = id; this.type = type; this.startMillis = startMillis; this.endMillis = endMillis;
            this.itemsJson = itemsJson; this.quick = quick; this.updatedAtMillis = updatedAtMillis;
        }
    }

    public static final class Water {
        public final long id, timestampMillis, updatedAtMillis;
        public final int amountMl;
        public Water(long id, int amountMl, long timestampMillis, long updatedAtMillis) {
            this.id = id; this.amountMl = amountMl; this.timestampMillis = timestampMillis; this.updatedAtMillis = updatedAtMillis;
        }
    }

    public static final class Agenda {
        public final long id;
        public final String day, text, lastCompletedDay;
        public final boolean task, completed, notDone, repeatDaily, generalGoal, notifyEnabled;
        public final int streak, notifyHour, notifyMinute;
        public final long updatedAtMillis;
        public final String category, subcategory;
        public final String projectPartsJson;
        public final String dailyCountsJson;
        public Agenda(long id, String day, String text, boolean task, boolean completed, boolean notDone,
                      boolean repeatDaily, boolean generalGoal, int streak, boolean notifyEnabled, int notifyHour, int notifyMinute,
                      long updatedAtMillis) {
            this(id, day, text, task, completed, notDone, repeatDaily, generalGoal, streak, notifyEnabled, notifyHour, notifyMinute, "", "", updatedAtMillis);
        }
        public Agenda(long id, String day, String text, boolean task, boolean completed, boolean notDone,
                      boolean repeatDaily, boolean generalGoal, int streak, boolean notifyEnabled, int notifyHour, int notifyMinute,
                      String category, String subcategory, long updatedAtMillis) {
            this(id, day, text, task, completed, notDone, repeatDaily, generalGoal, streak, notifyEnabled, notifyHour,
                    notifyMinute, category, subcategory, completed ? day : null, updatedAtMillis);
        }
        public Agenda(long id, String day, String text, boolean task, boolean completed, boolean notDone,
                      boolean repeatDaily, boolean generalGoal, int streak, boolean notifyEnabled, int notifyHour, int notifyMinute,
                      String category, String subcategory, String lastCompletedDay, long updatedAtMillis) {
            this(id, day, text, task, completed, notDone, repeatDaily, generalGoal, streak, notifyEnabled,
                    notifyHour, notifyMinute, category, subcategory, lastCompletedDay, updatedAtMillis, "[]");
        }
        public Agenda(long id, String day, String text, boolean task, boolean completed, boolean notDone,
                      boolean repeatDaily, boolean generalGoal, int streak, boolean notifyEnabled, int notifyHour, int notifyMinute,
                      String category, String subcategory, String lastCompletedDay, long updatedAtMillis, String projectPartsJson) {
            this(id, day, text, task, completed, notDone, repeatDaily, generalGoal, streak, notifyEnabled,
                    notifyHour, notifyMinute, category, subcategory, lastCompletedDay, updatedAtMillis, projectPartsJson, "{}");
        }
        public Agenda(long id, String day, String text, boolean task, boolean completed, boolean notDone,
                      boolean repeatDaily, boolean generalGoal, int streak, boolean notifyEnabled, int notifyHour, int notifyMinute,
                      String category, String subcategory, String lastCompletedDay, long updatedAtMillis, String projectPartsJson, String dailyCountsJson) {
            this.id = id; this.day = day; this.text = text; this.task = task; this.completed = completed;
            this.notDone = notDone; this.repeatDaily = repeatDaily; this.generalGoal = generalGoal; this.streak = streak;
            this.notifyEnabled = notifyEnabled; this.notifyHour = notifyHour; this.notifyMinute = notifyMinute;
            this.category = category; this.subcategory = subcategory;
            this.lastCompletedDay = lastCompletedDay;
            this.updatedAtMillis = updatedAtMillis;
            this.projectPartsJson = projectPartsJson;
            this.dailyCountsJson = dailyCountsJson;
        }
    }

    public static final class AgendaTemplate {
        public final String id, text, category, subcategory;
        public final boolean favorite, builtIn, hidden;
        public final long updatedAtMillis;
        public AgendaTemplate(String id, String text, String category, String subcategory, boolean favorite,
                              boolean builtIn, boolean hidden, long updatedAtMillis) {
            this.id = id; this.text = text; this.category = category; this.subcategory = subcategory;
            this.favorite = favorite; this.builtIn = builtIn; this.hidden = hidden; this.updatedAtMillis = updatedAtMillis;
        }
    }

    public static final class AgendaCategory {
        public final long id, updatedAtMillis;
        public final String category, subcategory;
        public final boolean hidden;
        public AgendaCategory(long id, String category, String subcategory, boolean hidden, long updatedAtMillis) {
            this.id = id; this.category = category; this.subcategory = subcategory;
            this.hidden = hidden; this.updatedAtMillis = updatedAtMillis;
        }
    }

    public static final class HiddenAgendaSuggestion {
        public final String key;
        public final long hiddenAtMillis;
        public HiddenAgendaSuggestion(String key, long hiddenAtMillis) {
            this.key = key; this.hiddenAtMillis = hiddenAtMillis;
        }
    }

    public static final class PrivateActivity {
        public final long id, startMillis, updatedAtMillis;
        public final Long endMillis;
        public PrivateActivity(long id, long startMillis, Long endMillis, long updatedAtMillis) {
            this.id = id; this.startMillis = startMillis; this.endMillis = endMillis; this.updatedAtMillis = updatedAtMillis;
        }
    }

    public static final class Deletion {
        public final String kind;
        public final long id, updatedAtMillis;
        public Deletion(String kind, long id, long updatedAtMillis) {
            this.kind = kind; this.id = id; this.updatedAtMillis = updatedAtMillis;
        }
    }
}
