package main

import (
	"bytes"
	"os"
	"path/filepath"
	"sort"
	"testing"
	"time"
)

func TestLiveMergedSnapshotRoundTrip(t *testing.T) {
	root := os.Getenv("GUNLUK_LIVE_COMPARE")
	if root == "" {
		t.Skip("requires captured live snapshots")
	}
	read := func(name string) *Snapshot {
		t.Helper()
		raw, err := os.ReadFile(filepath.Join(root, name))
		if err != nil {
			t.Fatal(err)
		}
		snapshot, err := decodeSnapshot(raw)
		if err != nil {
			t.Fatal(err)
		}
		return snapshot
	}
	expected := mergeSnapshots(read("pc-before.gts"), read("phone-before.gts"))
	canonical := func(snapshot *Snapshot) []byte {
		t.Helper()
		normalized := mergeSnapshots(nil, snapshot)
		normalized.GeneratedAtMillis = 0
		sort.Slice(normalized.AgendaTemplates, func(i, j int) bool { return normalized.AgendaTemplates[i].ID < normalized.AgendaTemplates[j].ID })
		sort.Slice(normalized.AgendaCategories, func(i, j int) bool { return normalized.AgendaCategories[i].ID < normalized.AgendaCategories[j].ID })
		sort.Slice(normalized.HiddenAgendaSuggestions, func(i, j int) bool {
			return normalized.HiddenAgendaSuggestions[i].Key < normalized.HiddenAgendaSuggestions[j].Key
		})
		raw, err := encodeSnapshot(normalized)
		if err != nil {
			t.Fatal(err)
		}
		return raw
	}
	reference := canonical(expected)
	for _, name := range []string{"pc-after.gts", "phone-after.gts"} {
		actual := read(name)
		if !bytes.Equal(reference, canonical(actual)) {
			t.Fatalf("%s does not match the expected two-device merge", name)
		}
		t.Logf("%s matches: agenda=%d sleep=%d meals=%d water=%d", name, len(actual.Agenda), len(actual.Sleeps), len(actual.Meals), len(actual.Water))
	}
}

// Opt-in capture: return the phone's unchanged snapshot before testing a merge.
func TestLivePhoneSnapshotBackup(t *testing.T) {
	path := os.Getenv("GUNLUK_LIVE_BACKUP")
	if path == "" {
		t.Skip("requires an explicitly requested live phone transfer")
	}
	identity, err := loadPairingIdentity()
	if err != nil {
		t.Fatal(err)
	}
	done := make(chan struct{}, 1)
	server := &SyncServer{code: identity.Code, serviceID: identity.ID,
		onSnapshot: func(snapshot *Snapshot, raw []byte) ([]byte, error) {
			if err := os.WriteFile(path, raw, 0600); err != nil {
				return nil, err
			}
			t.Logf("Captured snapshot: agenda=%d sleep=%d meals=%d water=%d", len(snapshot.Agenda), len(snapshot.Sleeps), len(snapshot.Meals), len(snapshot.Water))
			return raw, nil
		},
		onStatus: func(status string) {
			if status == "Senkron tamamlandı" {
				select {
				case done <- struct{}{}:
				default:
				}
			}
		},
	}
	go server.Run()
	defer server.Close()
	select {
	case <-done:
	case <-time.After(120 * time.Second):
		t.Fatal("phone transfer timed out")
	}
}
