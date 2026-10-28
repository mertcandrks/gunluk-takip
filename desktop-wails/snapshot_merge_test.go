package main

import "testing"

func TestMergeSnapshotsKeepsNewestRecordAndBothDeviceRecords(t *testing.T) {
	local := &Snapshot{
		Sleeps: []SleepRecord{{ID: 1, StartMillis: 100, EndMillis: 200, UpdatedAtMillis: 300}},
		Water:  []WaterRecord{{ID: -1, AmountMl: 250, TimestampMillis: 100, UpdatedAtMillis: 100}},
	}
	remote := &Snapshot{
		Sleeps: []SleepRecord{{ID: 1, StartMillis: 100, EndMillis: 250, UpdatedAtMillis: 400}},
		Water:  []WaterRecord{{ID: 2, AmountMl: 125, TimestampMillis: 200, UpdatedAtMillis: 200}},
	}

	got := mergeSnapshots(local, remote)
	if len(got.Sleeps) != 1 || got.Sleeps[0].EndMillis != 250 {
		t.Fatalf("newest sleep record was not selected: %#v", got.Sleeps)
	}
	if len(got.Water) != 2 {
		t.Fatalf("records from both devices were not preserved: %#v", got.Water)
	}
}

func TestMergeSnapshotsHonorsNewestDeletion(t *testing.T) {
	local := &Snapshot{
		Agenda: []AgendaRecord{{ID: 7, Day: "2026-09-19", Text: "Görev", UpdatedAtMillis: 100}},
	}
	remote := &Snapshot{
		Deletions: []DeletionRecord{{Kind: "agenda", ID: 7, UpdatedAtMillis: 200}},
	}

	got := mergeSnapshots(local, remote)
	if len(got.Agenda) != 0 || len(got.Deletions) != 1 {
		t.Fatalf("deletion was not applied: %#v", got)
	}
}

func TestMergeSnapshotsKeepsNewestAgendaCatalogChanges(t *testing.T) {
	local := &Snapshot{AgendaTemplates: []AgendaTemplateRecord{{ID: "task-1", Text: "Eski", UpdatedAtMillis: 10}}}
	remote := &Snapshot{
		AgendaTemplates:         []AgendaTemplateRecord{{ID: "task-1", Text: "Yeni", Favorite: true, UpdatedAtMillis: 20}},
		AgendaCategories:        []AgendaCategoryRecord{{ID: 2, Category: "Ev", Subcategory: "Düzen", UpdatedAtMillis: 20}},
		HiddenAgendaSuggestions: []HiddenAgendaSuggestionRecord{{Key: "eski", HiddenAtMillis: 20}},
	}
	got := mergeSnapshots(local, remote)
	if len(got.AgendaTemplates) != 1 || got.AgendaTemplates[0].Text != "Yeni" || len(got.AgendaCategories) != 1 || len(got.HiddenAgendaSuggestions) != 1 {
		t.Fatalf("agenda catalog merge failed: %#v", got)
	}
}

func TestMergeSnapshotsClearsCompletedActiveRecords(t *testing.T) {
	start := int64(100)
	local := &Snapshot{
		ActiveSleepStartMillis:     &start,
		ActiveSleepUpdatedAtMillis: 110,
		ActiveMeal:                 &MealRecord{ID: -4, StartMillis: start, UpdatedAtMillis: 110},
	}
	remote := &Snapshot{
		Sleeps: []SleepRecord{{ID: 3, StartMillis: start, EndMillis: 200, UpdatedAtMillis: 200}},
		Meals:  []MealRecord{{ID: -4, StartMillis: start, EndMillis: 200, UpdatedAtMillis: 200}},
	}

	got := mergeSnapshots(local, remote)
	if got.ActiveSleepStartMillis != nil || got.ActiveMeal != nil {
		t.Fatalf("completed records remained active: %#v", got)
	}
}

func TestUpsertDeletionUsesKindAndID(t *testing.T) {
	snapshot := &Snapshot{Deletions: []DeletionRecord{
		{Kind: "sleep", ID: 5, UpdatedAtMillis: 10},
		{Kind: "meal", ID: 5, UpdatedAtMillis: 20},
	}}

	upsertDeletion(snapshot, DeletionRecord{Kind: "meal", ID: 5, UpdatedAtMillis: 30})
	if len(snapshot.Deletions) != 2 || snapshot.Deletions[0].UpdatedAtMillis != 10 || snapshot.Deletions[1].UpdatedAtMillis != 30 {
		t.Fatalf("wrong tombstone was replaced: %#v", snapshot.Deletions)
	}
}
