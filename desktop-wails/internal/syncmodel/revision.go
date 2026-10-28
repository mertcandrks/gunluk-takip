// Package syncmodel merges immutable causal revisions without relying on wall clocks.
package syncmodel

import (
	"errors"
	"reflect"
	"regexp"
	"sort"

	"github.com/google/uuid"
)

type Revision struct {
	ID       string   `json:"id"`
	RecordID string   `json:"recordId"`
	DeviceID string   `json:"deviceId"`
	Kind     string   `json:"kind"`
	Parents  []string `json:"parents"`
	Payload  string   `json:"payload"`
	Deleted  bool     `json:"deleted"`
}

var kindPattern = regexp.MustCompile(`^[a-z][a-z0-9_]{0,63}$`)

func canonicalID(id string) bool {
	parsed, err := uuid.Parse(id)
	return err == nil && parsed.String() == id
}

func normalize(r Revision) (Revision, error) {
	if !canonicalID(r.ID) || !canonicalID(r.RecordID) || !canonicalID(r.DeviceID) || !kindPattern.MatchString(r.Kind) {
		return Revision{}, errors.New("invalid revision identity")
	}
	if r.Deleted && r.Payload != "" {
		return Revision{}, errors.New("deletion must not contain a payload")
	}
	r.Parents = append([]string{}, r.Parents...)
	sort.Strings(r.Parents)
	for i, parent := range r.Parents {
		if !canonicalID(parent) || parent == r.ID || (i > 0 && parent == r.Parents[i-1]) {
			return Revision{}, errors.New("invalid revision parent")
		}
	}
	return r, nil
}

// Merge returns a new graph or an error; neither input is modified.
// Missing ancestry must be fetched before accepting a partial batch.
func Merge(local, incoming []Revision) ([]Revision, error) {
	combined := make(map[string]Revision, len(local)+len(incoming))
	for _, batch := range [][]Revision{local, incoming} {
		for _, raw := range batch {
			r, err := normalize(raw)
			if err != nil {
				return nil, err
			}
			if previous, exists := combined[r.ID]; exists && !reflect.DeepEqual(previous, r) {
				return nil, errors.New("operation ID reused with different content")
			}
			combined[r.ID] = r
		}
	}
	kinds := make(map[string]string)
	remaining := make(map[string]int)
	children := make(map[string][]string)
	ready := make([]string, 0)
	for _, r := range combined {
		if kind, exists := kinds[r.RecordID]; exists && kind != r.Kind {
			return nil, errors.New("record kind changed")
		}
		kinds[r.RecordID] = r.Kind
		remaining[r.ID] = len(r.Parents)
		if len(r.Parents) == 0 {
			ready = append(ready, r.ID)
		}
		for _, id := range r.Parents {
			parent, exists := combined[id]
			if !exists || parent.RecordID != r.RecordID {
				return nil, errors.New("missing or unrelated parent")
			}
			children[id] = append(children[id], r.ID)
		}
	}
	visited := 0
	for len(ready) > 0 {
		id := ready[0]
		ready = ready[1:]
		visited++
		for _, child := range children[id] {
			remaining[child]--
			if remaining[child] == 0 {
				ready = append(ready, child)
			}
		}
	}
	if visited != len(combined) {
		return nil, errors.New("cyclic revision history")
	}
	result := make([]Revision, 0, len(combined))
	for _, r := range combined {
		result = append(result, r)
	}
	sort.Slice(result, func(i, j int) bool { return result[i].ID < result[j].ID })
	return result, nil
}

// Heads operates on a validated graph. Multiple results are unresolved conflicts.
func Heads(graph []Revision, recordID string) []Revision {
	superseded := make(map[string]bool)
	for _, r := range graph {
		if r.RecordID == recordID {
			for _, id := range r.Parents {
				superseded[id] = true
			}
		}
	}
	result := make([]Revision, 0)
	for _, r := range graph {
		if r.RecordID == recordID && !superseded[r.ID] {
			r.Parents = append([]string{}, r.Parents...)
			result = append(result, r)
		}
	}
	sort.Slice(result, func(i, j int) bool { return result[i].ID < result[j].ID })
	return result
}
