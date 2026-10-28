package main

import (
	"path/filepath"
	"testing"
)

func TestProjectPartsPersistCompleteAndReopen(t *testing.T) {
	for _, general := range []bool{false, true} {
		name := "daily"
		if general {
			name = "general"
		}
		t.Run(name, func(t *testing.T) { testPartsPersistCompleteAndReopen(t, general) })
	}
}

func testPartsPersistCompleteAndReopen(t *testing.T, general bool) {
	t.Setenv("APPDATA", filepath.Join(t.TempDir(), "appdata"))
	app := &App{snapshot: &Snapshot{WaterGoalMl: 2500}}
	item := AgendaRecord{Day: "2026-09-30", Text: "Project", Task: true, GeneralGoal: general,
		ProjectPartsJSON: `[{"id":"front","title":"Frontend","progress":50},{"id":"back","title":"Backend","progress":0}]`}
	if err := app.SaveAgenda(item); err != nil {
		t.Fatal(err)
	}
	item = app.snapshot.Agenda[0]
	if item.GeneralGoal != general || item.Day != "2026-09-30" {
		t.Fatal("saving parts changed task kind or day")
	}
	if item.Completed {
		t.Fatal("partial project was completed")
	}
	if err := app.ToggleAgendaCompleted(item.ID, item.Day); err == nil {
		t.Fatal("manual completion bypassed subtasks")
	}
	item.ProjectPartsJSON = `[{"id":"front","title":"Frontend","progress":100},{"id":"back","title":"Backend","progress":100}]`
	if err := app.SaveAgenda(item); err != nil {
		t.Fatal(err)
	}
	if !app.snapshot.Agenda[0].Completed {
		t.Fatal("finished project was not completed")
	}
	if err := app.MoveAgendaToDay(item.ID, "2026-10-01"); err != nil {
		t.Fatal(err)
	}
	if !app.snapshot.Agenda[0].Completed {
		t.Fatal("moving erased completion")
	}
	item = app.snapshot.Agenda[0]
	item.ProjectPartsJSON = `[{"id":"front","title":"Frontend","progress":50}]`
	if err := app.SaveAgenda(item); err != nil {
		t.Fatal(err)
	}
	if app.snapshot.Agenda[0].Completed {
		t.Fatal("reopening was not reflected")
	}
	saved, err := loadSnapshot()
	if err != nil || saved.Agenda[0].ProjectPartsJSON != item.ProjectPartsJSON {
		t.Fatalf("persistence failed: %v", err)
	}
	encoded, err := encodeSnapshot(saved)
	if err != nil {
		t.Fatal(err)
	}
	decoded, err := decodeSnapshot(encoded)
	if err != nil || decoded.Agenda[0].ProjectPartsJSON != item.ProjectPartsJSON {
		t.Fatalf("sync failed: %v", err)
	}
	for _, bad := range []string{`null`, `{}`, `[{"id":"x","title":"a","progress":101}]`, `[{"id":"x","title":" ","progress":0}]`, `[{"id":"x","title":"a"},{"id":"x","title":"b"}]`} {
		if _, err := parseAgendaParts(bad); err == nil {
			t.Fatalf("accepted %s", bad)
		}
	}
}
