package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

func TestAutomationInboxPersistsBacksUpAndRejectsConflicts(t *testing.T) {
	directory := t.TempDir()
	t.Setenv("APPDATA", directory)
	t.Setenv("XDG_CONFIG_HOME", directory)
	root, err := automationDirectory()
	if err != nil {
		t.Fatal(err)
	}
	if err = os.MkdirAll(filepath.Join(root, "inbox"), 0700); err != nil {
		t.Fatal(err)
	}
	if err = atomicAutomationJSON(filepath.Join(root, "projects.json"), map[string]int64{"project": -1}); err != nil {
		t.Fatal(err)
	}
	snapshot, batch := automationFixture()
	app := NewApp()
	app.snapshot = snapshot
	publish := func(batch AutomationBatch) {
		t.Helper()
		if err := atomicAutomationJSON(filepath.Join(root, "inbox", batch.BatchID+".json"), batch); err != nil {
			t.Fatal(err)
		}
		app.processAutomationInbox()
	}
	publish(batch)
	if _, err = os.Stat(filepath.Join(root, "processed", batch.BatchID+".json")); err != nil {
		t.Fatal(err)
	}
	saved, err := loadSnapshot()
	if err != nil || len(saved.Agenda) != 2 {
		t.Fatalf("snapshot not saved: %v", err)
	}
	backups, _ := filepath.Glob(filepath.Join(root, "backups", "*.gts"))
	if len(backups) != 1 {
		t.Fatalf("expected backup, got %d", len(backups))
	}
	before, _ := json.Marshal(app.snapshot)
	batch.BatchID += "-conflict"
	batch.Parts[0].Progress = 50
	publish(batch)
	if _, err = os.Stat(filepath.Join(root, "rejected", batch.BatchID+".json")); err != nil {
		t.Fatal(err)
	}
	after, _ := json.Marshal(app.snapshot)
	if string(before) != string(after) {
		t.Fatal("rejected batch changed records")
	}
	var exported struct {
		Goals []AgendaRecord `json:"goals"`
	}
	raw, err := os.ReadFile(filepath.Join(root, "state.json"))
	if err != nil || json.Unmarshal(raw, &exported) != nil || len(exported.Goals) != 1 {
		t.Fatalf("invalid project export: %v", err)
	}
}

func TestPairingIdentitySurvivesRestartAndRejectsCorruption(t *testing.T) {
	directory := t.TempDir()
	t.Setenv("APPDATA", directory)
	t.Setenv("XDG_CONFIG_HOME", directory)
	first, err := loadPairingIdentity()
	if err != nil {
		t.Fatal(err)
	}
	second, err := loadPairingIdentity()
	if err != nil || first != second {
		t.Fatalf("pairing identity changed: %v", err)
	}
	path, err := snapshotPath()
	if err != nil {
		t.Fatal(err)
	}
	if err = os.WriteFile(filepath.Join(filepath.Dir(path), "pairing.json"), []byte(`{}`), 0600); err != nil {
		t.Fatal(err)
	}
	if _, err = loadPairingIdentity(); err == nil {
		t.Fatal("corrupt identity accepted")
	}
}

func automationFixture() (*Snapshot, AutomationBatch) {
	title, progress := "Backend", 0
	return &Snapshot{Agenda: []AgendaRecord{{ID: -1, Task: true, Text: "Project", ProjectPartsJSON: `[{"id":"backend","title":"Backend","progress":0},{"id":"manual","title":"Personal part","progress":25}]`}}}, AutomationBatch{
		Version: 1, BatchID: "project-2026-10-04", ProjectKey: "project", GoalID: -1, Day: "2026-10-04", Summary: "Backend implemented; not tested.",
		Parts: []AutomationPart{{ID: "backend", Title: title, Progress: 100, ExpectedTitle: &title, ExpectedProgress: &progress, Evidence: "chat turn 3: implementation completed"}, {ID: "deploy", Title: "Deployment", Progress: 0, Evidence: "chat turn 4: deployment pending"}},
	}
}
func TestAutomationBatchPreservesManualPartsAndIsIdempotent(t *testing.T) {
	snapshot, batch := automationFixture()
	raw, _ := json.Marshal(batch)
	decoded, err := decodeAutomationBatch(raw)
	if err != nil {
		t.Fatal(err)
	}
	if err = applyAutomationBatch(snapshot, decoded, 100); err != nil {
		t.Fatal(err)
	}
	if err = applyAutomationBatch(snapshot, decoded, 200); err != nil {
		t.Fatal(err)
	}
	parts, err := parseAgendaParts(snapshot.Agenda[0].ProjectPartsJSON)
	if err != nil || len(parts) != 3 || parts[0].Progress != 100 || parts[1].ID != "manual" || parts[1].Progress != 25 || len(snapshot.Agenda) != 2 || snapshot.Agenda[0].UpdatedAtMillis != 100 {
		t.Fatalf("unexpected snapshot: %#v, %#v, %v", snapshot, parts, err)
	}
}
func TestAutomationBatchRejectsManualConflictWithoutMutation(t *testing.T) {
	snapshot, batch := automationFixture()
	value := 50
	batch.Parts[0].ExpectedProgress = &value
	original, _ := json.Marshal(snapshot)
	if applyAutomationBatch(snapshot, batch, 100) == nil {
		t.Fatal("expected conflict")
	}
	after, _ := json.Marshal(snapshot)
	if string(original) != string(after) {
		t.Fatal("conflict mutated snapshot")
	}
}
func TestAutomationBatchValidation(t *testing.T) {
	_, batch := automationFixture()
	batch.Parts[0].Evidence = ""
	raw, _ := json.Marshal(batch)
	if _, err := decodeAutomationBatch(raw); err == nil {
		t.Fatal("missing evidence accepted")
	}
	_, batch = automationFixture()
	batch.Parts = append(batch.Parts, batch.Parts[0])
	raw, _ = json.Marshal(batch)
	if _, err := decodeAutomationBatch(raw); err == nil {
		t.Fatal("duplicate part accepted")
	}
	_, batch = automationFixture()
	raw, _ = json.Marshal(batch)
	if _, err := decodeAutomationBatch(append(raw, []byte(`{}`)...)); err == nil {
		t.Fatal("trailing JSON accepted")
	}
}
