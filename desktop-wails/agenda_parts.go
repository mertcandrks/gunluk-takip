package main

import (
	"encoding/json"
	"errors"
	"strings"
	"unicode/utf16"
)

type AgendaPart struct {
	ID       string `json:"id"`
	Title    string `json:"title"`
	Progress int    `json:"progress"`
}

func parseAgendaParts(raw string) ([]AgendaPart, error) {
	parts := []AgendaPart{}
	if strings.TrimSpace(raw) == "" {
		return parts, nil
	}
	if len(raw) > 100000 || json.Unmarshal([]byte(raw), &parts) != nil || parts == nil || len(parts) > 100 {
		return nil, errors.New("geçersiz alt görevler")
	}
	seen := map[string]bool{}
	for _, part := range parts {
		if strings.TrimSpace(part.ID) == "" || len(utf16.Encode([]rune(part.ID))) > 80 || seen[part.ID] ||
			strings.TrimSpace(part.Title) == "" || len(utf16.Encode([]rune(part.Title))) > 200 || part.Progress < 0 || part.Progress > 100 {
			return nil, errors.New("geçersiz alt görev")
		}
		seen[part.ID] = true
	}
	return parts, nil
}

func partsCompleted(parts []AgendaPart) bool {
	if len(parts) == 0 {
		return false
	}
	for _, part := range parts {
		if part.Progress != 100 {
			return false
		}
	}
	return true
}
