package main

import (
	"path/filepath"
	"testing"
)

func TestDailyCountsPersistAndSyncSeparatelyByDay(t *testing.T) {
	t.Setenv("APPDATA", filepath.Join(t.TempDir(), "appdata"))
	app := &App{snapshot: &Snapshot{WaterGoalMl: 2500}}
	if err := app.SaveAgenda(AgendaRecord{Day: "2026-10-17", Text: "Brush teeth", Task: true, RepeatDaily: true}); err != nil {
		t.Fatal(err)
	}
	id := app.snapshot.Agenda[0].ID
	for _, day := range []string{"2026-10-17", "2026-10-17", "2026-10-18"} {
		if err := app.AdjustAgendaDailyCount(id, day, 1); err != nil {
			t.Fatal(err)
		}
	}
	check := func(want int) {
		t.Helper()
		saved, err := loadSnapshot()
		if err != nil {
			t.Fatal(err)
		}
		wire, err := encodeSnapshot(saved)
		if err != nil {
			t.Fatal(err)
		}
		decoded, err := decodeSnapshot(wire)
		if err != nil {
			t.Fatal(err)
		}
		counts, err := parseDailyCounts(decoded.Agenda[0].DailyCountsJSON)
		if err != nil || counts["2026-10-17"] != want || counts["2026-10-18"] != 1 {
			t.Fatalf("counts lost: %v %v", counts, err)
		}
	}
	check(2)
	for i := 0; i < 3; i++ {
		if err := app.AdjustAgendaDailyCount(id, "2026-10-17", -1); err != nil {
			t.Fatal(err)
		}
	}
	check(0)
	if err := app.AdjustAgendaDailyCount(id, "2026-10-17", 2); err == nil {
		t.Fatal("invalid delta accepted")
	}
	if err := app.AdjustAgendaDailyCount(id, "bad", 1); err == nil {
		t.Fatal("invalid date accepted")
	}
	for _, raw := range []string{`null`, `[]`, `{"2026-10-17":null}`, `{"2026-02-30":1}`, `{"2026-10-17":-1}`, `{"2026-10-17":1.5}`, `{"2026-10-17":10000}`} {
		if _, err := parseDailyCounts(raw); err == nil {
			t.Fatalf("accepted %s", raw)
		}
	}
}
