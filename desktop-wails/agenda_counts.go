package main

import (
	"encoding/json"
	"errors"
	"time"
)

func parseDailyCounts(raw string) (map[string]int, error) {
	counts := map[string]int{}
	if raw == "" {
		return counts, nil
	}
	if len(raw) > 200000 {
		return nil, errors.New("günlük sayaç kaydı çok büyük")
	}
	var values map[string]*int
	if err := json.Unmarshal([]byte(raw), &values); err != nil {
		return nil, err
	}
	if values == nil || len(values) > 10000 {
		return nil, errors.New("geçersiz günlük sayaç")
	}
	for day, count := range values {
		if parsed, err := time.Parse("2006-01-02", day); err != nil || parsed.Format("2006-01-02") != day || count == nil || *count < 0 || *count > 9999 {
			return nil, errors.New("geçersiz günlük sayaç")
		}
		counts[day] = *count
	}
	return counts, nil
}

func (a *App) AdjustAgendaDailyCount(id int64, day string, delta int) error {
	if _, err := time.Parse("2006-01-02", day); err != nil {
		return err
	}
	if delta != 1 && delta != -1 {
		return errors.New("geçersiz sayaç değişimi")
	}
	return a.mutate(func(s *Snapshot, now int64) error {
		for index := range s.Agenda {
			item := &s.Agenda[index]
			if item.ID != id {
				continue
			}
			if !item.Task || !item.RepeatDaily {
				return errors.New("görev her gün tekrarlanmalı")
			}
			counts, err := parseDailyCounts(item.DailyCountsJSON)
			if err != nil {
				return err
			}
			count := counts[day] + delta
			if count < 0 {
				count = 0
			}
			if count > 9999 {
				return errors.New("günlük adet sınırına ulaşıldı")
			}
			if count == 0 {
				delete(counts, day)
			} else {
				counts[day] = count
			}
			if len(counts) > 10000 {
				return errors.New("günlük sayaç sınırına ulaşıldı")
			}
			raw, err := json.Marshal(counts)
			if err != nil {
				return err
			}
			item.DailyCountsJSON = string(raw)
			item.UpdatedAtMillis = now
			return nil
		}
		return errors.New("ajanda kaydı bulunamadı")
	})
}
