package main

import (
	"path/filepath"
	"testing"
)

func TestAgendaLifecycleAndCatalogPersistence(t *testing.T) {
	t.Setenv("APPDATA", filepath.Join(t.TempDir(), "appdata"))
	app := &App{snapshot: &Snapshot{WaterGoalMl: 2500}}

	if err := app.SaveAgenda(AgendaRecord{
		Day:          "2026-09-28",
		Text:         "PC agenda test",
		Task:         true,
		Category:     "Work",
		Subcategory:  "Planning",
		NotifyHour:   9,
		NotifyMinute: 30,
	}); err != nil {
		t.Fatal(err)
	}
	if len(app.snapshot.Agenda) != 1 || app.snapshot.Agenda[0].ID >= 0 {
		t.Fatalf("expected one PC-owned agenda record, got %#v", app.snapshot.Agenda)
	}
	id := app.snapshot.Agenda[0].ID

	if err := app.ToggleAgendaCompleted(id, "2026-09-28"); err != nil {
		t.Fatal(err)
	}
	if !app.snapshot.Agenda[0].Completed || app.snapshot.Agenda[0].Streak != 1 {
		t.Fatalf("completion was not persisted: %#v", app.snapshot.Agenda[0])
	}
	if err := app.ToggleAgendaNotDone(id); err != nil {
		t.Fatal(err)
	}
	if app.snapshot.Agenda[0].Completed || !app.snapshot.Agenda[0].NotDone {
		t.Fatalf("not-done state was not persisted: %#v", app.snapshot.Agenda[0])
	}
	if err := app.PostponeAgenda(id, "2026-09-29"); err != nil {
		t.Fatal(err)
	}
	if app.snapshot.Agenda[0].Day != "2026-09-29" || app.snapshot.Agenda[0].NotDone {
		t.Fatalf("postpone did not reset and move the task: %#v", app.snapshot.Agenda[0])
	}
	if err := app.MoveAgendaToGeneral(id); err != nil {
		t.Fatal(err)
	}
	if !app.snapshot.Agenda[0].GeneralGoal {
		t.Fatal("task was not moved to general goals")
	}
	if err := app.MoveAgendaToDay(id, "2026-09-30"); err != nil {
		t.Fatal(err)
	}
	if app.snapshot.Agenda[0].GeneralGoal || app.snapshot.Agenda[0].Day != "2026-09-30" {
		t.Fatalf("general goal was not moved back to the selected day: %#v", app.snapshot.Agenda[0])
	}

	if err := app.SaveAgendaTemplate(AgendaTemplateRecord{ID: "pc-template", Text: "Template", Favorite: true}); err != nil {
		t.Fatal(err)
	}
	if err := app.SetAgendaTemplateFavorite("pc-template", false); err != nil {
		t.Fatal(err)
	}
	if err := app.RemoveAgendaTemplate("pc-template"); err != nil {
		t.Fatal(err)
	}
	if !app.snapshot.AgendaTemplates[0].Hidden || app.snapshot.AgendaTemplates[0].Favorite {
		t.Fatalf("template state was not persisted: %#v", app.snapshot.AgendaTemplates[0])
	}

	if err := app.SaveAgendaCategory(AgendaCategoryRecord{Category: "Work", Subcategory: "Planning"}); err != nil {
		t.Fatal(err)
	}
	categoryID := app.snapshot.AgendaCategories[0].ID
	if categoryID >= 0 {
		t.Fatalf("expected a PC-owned category id, got %d", categoryID)
	}
	if err := app.RemoveAgendaCategory(categoryID); err != nil {
		t.Fatal(err)
	}
	if !app.snapshot.AgendaCategories[0].Hidden {
		t.Fatal("category was not soft-hidden")
	}

	if err := app.HideAgendaSuggestion("Task\x1fWork\x1fPlanning"); err != nil {
		t.Fatal(err)
	}
	if len(app.snapshot.HiddenAgendaSuggestions) != 1 {
		t.Fatalf("hidden suggestion was not persisted: %#v", app.snapshot.HiddenAgendaSuggestions)
	}

	reloaded, err := loadSnapshot()
	if err != nil {
		t.Fatal(err)
	}
	if len(reloaded.Agenda) != 1 || len(reloaded.AgendaTemplates) != 1 || len(reloaded.AgendaCategories) != 1 || len(reloaded.HiddenAgendaSuggestions) != 1 {
		t.Fatalf("saved agenda state did not round-trip: %#v", reloaded)
	}
}
