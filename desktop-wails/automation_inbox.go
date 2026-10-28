package main

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"github.com/wailsapp/wails/v2/pkg/runtime"
)

type AutomationPart struct {
	ID               string  `json:"id"`
	Title            string  `json:"title"`
	Progress         int     `json:"progress"`
	ExpectedTitle    *string `json:"expectedTitle,omitempty"`
	ExpectedProgress *int    `json:"expectedProgress,omitempty"`
	Evidence         string  `json:"evidence"`
}
type AutomationBatch struct {
	Version    int              `json:"version"`
	BatchID    string           `json:"batchId"`
	ProjectKey string           `json:"projectKey"`
	GoalID     int64            `json:"goalId"`
	Day        string           `json:"day"`
	Summary    string           `json:"summary"`
	Parts      []AutomationPart `json:"parts"`
}

var automationKey = regexp.MustCompile(`^[a-zA-Z0-9_-]{1,64}$`)

func decodeAutomationBatch(raw []byte) (AutomationBatch, error) {
	var batch AutomationBatch
	if len(raw) > 262144 {
		return batch, errors.New("update too large")
	}
	decoder := json.NewDecoder(bytes.NewReader(raw))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&batch); err != nil {
		return batch, err
	}
	if decoder.Decode(new(any)) != io.EOF {
		return batch, errors.New("trailing JSON")
	}
	if batch.Version != 1 || !automationKey.MatchString(batch.BatchID) || !automationKey.MatchString(batch.ProjectKey) || batch.GoalID == 0 || len(batch.Parts) == 0 || len(batch.Parts) > 100 || strings.TrimSpace(batch.Summary) == "" || len(batch.Summary) > 1500 {
		return batch, errors.New("invalid batch")
	}
	if _, err := time.Parse("2006-01-02", batch.Day); err != nil {
		return batch, err
	}
	parts := make([]AgendaPart, 0, len(batch.Parts))
	for _, part := range batch.Parts {
		if strings.TrimSpace(part.Evidence) == "" || len(part.Evidence) > 1000 || (part.ExpectedTitle == nil) != (part.ExpectedProgress == nil) {
			return batch, errors.New("missing evidence or incomplete baseline")
		}
		parts = append(parts, AgendaPart{ID: part.ID, Title: part.Title, Progress: part.Progress})
	}
	encoded, _ := json.Marshal(parts)
	_, err := parseAgendaParts(string(encoded))
	return batch, err
}

// Apply on the transaction's copy; a conflict aborts the entire batch.
func applyAutomationBatch(snapshot *Snapshot, batch AutomationBatch, now int64) error {
	hash := sha256.Sum256([]byte(batch.BatchID))
	// IDs cross a JavaScript bridge, so stay inside its exact integer range.
	noteID := -int64(binary.BigEndian.Uint64(hash[:8])&0x000fffffffffffff) - 1
	encodedBatch, err := json.Marshal(batch)
	if err != nil {
		return err
	}
	contentHash := sha256.Sum256(encodedBatch)
	marker := fmt.Sprintf("[batch:%s:%x]", batch.BatchID, contentHash)
	for _, record := range snapshot.Agenda {
		if record.ID == noteID {
			if strings.HasPrefix(record.Text, marker+"\n") {
				return nil
			}
			return errors.New("batch ID collision")
		}
	}
	for _, deleted := range snapshot.Deletions {
		if deleted.Kind == "agenda" && deleted.ID == noteID {
			return errors.New("batch receipt was deleted; manual review required")
		}
	}
	index := -1
	for i, goal := range snapshot.Agenda {
		if goal.ID == batch.GoalID && goal.Task {
			index = i
			break
		}
	}
	if index < 0 {
		return errors.New("project goal missing")
	}
	goal := snapshot.Agenda[index]
	parts, err := parseAgendaParts(goal.ProjectPartsJSON)
	if err != nil {
		return err
	}
	for _, change := range batch.Parts {
		found := -1
		for i, part := range parts {
			if part.ID == change.ID {
				found = i
				break
			}
		}
		if found >= 0 {
			current := parts[found]
			if current.Title == change.Title && current.Progress == change.Progress {
				continue
			}
			if change.ExpectedTitle == nil || current.Title != *change.ExpectedTitle || current.Progress != *change.ExpectedProgress {
				return fmt.Errorf("manual change conflict: %s", change.ID)
			}
			parts[found] = AgendaPart{ID: change.ID, Title: change.Title, Progress: change.Progress}
		} else {
			if change.ExpectedTitle != nil {
				return fmt.Errorf("removed part conflict: %s", change.ID)
			}
			parts = append(parts, AgendaPart{ID: change.ID, Title: change.Title, Progress: change.Progress})
		}
	}
	raw, err := json.Marshal(parts)
	if err != nil {
		return err
	}
	if _, err = parseAgendaParts(string(raw)); err != nil {
		return err
	}
	goal.ProjectPartsJSON = string(raw)
	goal.Completed = partsCompleted(parts)
	goal.NotDone = false
	goal.UpdatedAtMillis = now
	snapshot.Agenda[index] = goal
	snapshot.Agenda = append(snapshot.Agenda, AgendaRecord{ID: noteID, Day: batch.Day, Text: marker + "\n" + batch.Summary, Category: "Proje", Subcategory: batch.ProjectKey, UpdatedAtMillis: now})
	return nil
}

func automationDirectory() (string, error) {
	path, err := snapshotPath()
	return filepath.Join(filepath.Dir(path), "automation"), err
}
func atomicAutomationJSON(path string, value any) error {
	raw, err := json.MarshalIndent(value, "", "  ")
	if err != nil {
		return err
	}
	tmp := path + ".tmp"
	if err = os.WriteFile(tmp, raw, 0600); err != nil {
		return err
	}
	return os.Rename(tmp, path)
}
func (a *App) runAutomationInbox(ctx context.Context) {
	ticker := time.NewTicker(30 * time.Second)
	defer ticker.Stop()
	for {
		a.processAutomationInbox()
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}
	}
}
func (a *App) processAutomationInbox() {
	root, err := automationDirectory()
	if err != nil {
		return
	}
	for _, dir := range []string{"inbox", "processed", "rejected", "backups"} {
		if os.MkdirAll(filepath.Join(root, dir), 0700) != nil {
			return
		}
	}
	bindingsPath := filepath.Join(root, "projects.json")
	if _, err = os.Stat(bindingsPath); errors.Is(err, os.ErrNotExist) {
		// Initial project selected by the user; further projects require an explicit binding.
		if err = atomicAutomationJSON(bindingsPath, map[string]int64{"kickservis": -1790880345175}); err != nil {
			return
		}
	}
	bindingsRaw, err := os.ReadFile(bindingsPath)
	if err != nil {
		return
	}
	bindings := map[string]int64{}
	if json.Unmarshal(bindingsRaw, &bindings) != nil {
		a.setAutomationStatus("Proje eşleştirme dosyası okunamadı")
		return
	}
	files, err := filepath.Glob(filepath.Join(root, "inbox", "*.json"))
	if err != nil {
		return
	}
	for _, file := range files {
		handle, err := os.Open(file)
		if err != nil {
			continue
		}
		raw, err := io.ReadAll(io.LimitReader(handle, 262145))
		handle.Close()
		var batch AutomationBatch
		if err == nil {
			batch, err = decodeAutomationBatch(raw)
		}
		if err == nil && bindings[batch.ProjectKey] != batch.GoalID {
			err = errors.New("project is not bound to this goal")
		}
		if err == nil {
			err = a.mutate(func(s *Snapshot, now int64) error {
				original, encodeErr := encodeSnapshot(s)
				if encodeErr != nil {
					return encodeErr
				}
				backup := filepath.Join(root, "backups", batch.BatchID+"-"+fmt.Sprint(now)+".gts")
				if writeErr := os.WriteFile(backup, original, 0600); writeErr != nil {
					return writeErr
				}
				return applyAutomationBatch(s, batch, now)
			})
		}
		destination := "processed"
		message := "Applied"
		if err != nil {
			destination = "rejected"
			message = err.Error()
		}
		a.setAutomationStatus(map[bool]string{true: "Güncelleme bekletildi: " + message, false: "Ajanda güncellendi: " + batch.Day}[err != nil])
		if receiptErr := atomicAutomationJSON(filepath.Join(root, destination, filepath.Base(file)+".receipt.json"), map[string]any{"batchId": batch.BatchID, "status": destination, "message": message, "time": time.Now().Format(time.RFC3339)}); receiptErr != nil {
			continue
		}
		if os.Rename(file, filepath.Join(root, destination, filepath.Base(file))) != nil {
			continue
		}
	}
	a.mu.RLock()
	goals := []AgendaRecord{}
	if a.snapshot != nil {
		for _, record := range a.snapshot.Agenda {
			for _, id := range bindings {
				if record.Task && record.ID == id {
					goals = append(goals, record)
					break
				}
			}
		}
	}
	a.mu.RUnlock()
	// Export only project tasks, never the health snapshot or pairing secrets.
	if err = atomicAutomationJSON(filepath.Join(root, "state.json"), map[string]any{"version": 1, "exportedAt": time.Now().Format(time.RFC3339), "projects": bindings, "goals": goals}); err != nil {
		a.setAutomationStatus("Ajanda durum dosyası yazılamadı")
	}
}

func (a *App) setAutomationStatus(status string) {
	a.mu.Lock()
	changed := a.automationStatus != status
	a.automationStatus = status
	a.mu.Unlock()
	if changed && a.ctx != nil {
		runtime.EventsEmit(a.ctx, "sync:status", a.GetState())
	}
}
