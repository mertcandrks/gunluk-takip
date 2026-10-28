package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/wailsapp/wails/v2/pkg/runtime"
)

func (a *App) StartSleep(startMillis int64) error {
	if startMillis <= 0 || startMillis > time.Now().Add(time.Minute).UnixMilli() {
		return errors.New("geçersiz başlangıç saati")
	}
	return a.mutate(func(s *Snapshot, now int64) error {
		if s.ActiveSleepStartMillis != nil {
			return errors.New("uyku takibi zaten açık")
		}
		s.ActiveSleepStartMillis = &startMillis
		s.ActiveSleepUpdatedAtMillis = now
		return nil
	})
}

func (a *App) FinishSleep(endMillis int64) error {
	return a.mutate(func(s *Snapshot, now int64) error {
		if s.ActiveSleepStartMillis == nil || endMillis <= *s.ActiveSleepStartMillis {
			return errors.New("geçersiz bitiş saati")
		}
		s.Sleeps = append(s.Sleeps, SleepRecord{ID: nextID(s), StartMillis: *s.ActiveSleepStartMillis, EndMillis: endMillis, UpdatedAtMillis: now})
		s.ActiveSleepStartMillis = nil
		s.ActiveSleepUpdatedAtMillis = now
		return nil
	})
}

func (a *App) SaveSleep(item SleepRecord) error {
	if item.StartMillis <= 0 || item.EndMillis <= item.StartMillis {
		return errors.New("geçersiz uyku aralığı")
	}
	return a.mutate(func(s *Snapshot, now int64) error {
		if item.ID == 0 {
			item.ID = nextID(s)
		}
		item.UpdatedAtMillis = now
		s.Sleeps = upsert(s.Sleeps, item, func(v SleepRecord) int64 { return v.ID })
		removeDeletion(s, "sleep", item.ID)
		return nil
	})
}

func (a *App) SaveMeal(item MealRecord) error {
	if !validMealType(item.Type) || item.StartMillis <= 0 || item.EndMillis < item.StartMillis || len(item.ItemsJSON) > int(maxTextBytes) {
		return errors.New("geçersiz öğün")
	}
	return a.mutate(func(s *Snapshot, now int64) error {
		if item.ID == 0 {
			item.ID = nextID(s)
		}
		item.UpdatedAtMillis = now
		s.Meals = upsert(s.Meals, item, func(v MealRecord) int64 { return v.ID })
		removeDeletion(s, "meal", item.ID)
		return nil
	})
}

func (a *App) SaveWater(item WaterRecord) error {
	if item.AmountMl < 10 || item.AmountMl > 5000 || item.TimestampMillis <= 0 {
		return errors.New("geçersiz su kaydı")
	}
	return a.mutate(func(s *Snapshot, now int64) error {
		if item.ID == 0 {
			item.ID = nextID(s)
		}
		item.UpdatedAtMillis = now
		s.Water = upsert(s.Water, item, func(v WaterRecord) int64 { return v.ID })
		removeDeletion(s, "water", item.ID)
		return nil
	})
}

func (a *App) SetWaterGoal(goal int32) error {
	if goal < 500 || goal > 10000 {
		return errors.New("hedef 500-10000 ml arasında olmalı")
	}
	return a.mutate(func(s *Snapshot, now int64) error { s.WaterGoalMl = goal; s.WaterGoalUpdatedAtMillis = now; return nil })
}

func (a *App) SaveAgenda(item AgendaRecord) error {
	item.Text = strings.TrimSpace(item.Text)
	if item.Text == "" || len(item.Text) > 4000 {
		return errors.New("geçersiz ajanda metni")
	}
	item.Category = strings.TrimSpace(item.Category)
	item.Subcategory = strings.TrimSpace(item.Subcategory)
	if len(item.Category) > 60 || len(item.Subcategory) > 60 {
		return errors.New("geçersiz kategori")
	}
	if _, err := time.Parse("2006-01-02", item.Day); err != nil {
		return errors.New("geçersiz tarih")
	}
	if item.NotifyHour < 0 || item.NotifyHour > 23 || item.NotifyMinute < 0 || item.NotifyMinute > 59 {
		return errors.New("geçersiz bildirim saati")
	}
	if item.GeneralGoal && !item.Task {
		return errors.New("genel hedef görev olmalı")
	}
	if item.GeneralGoal {
		item.RepeatDaily = false
		item.NotifyEnabled = false
		item.NotDone = false
	}
	parts, err := parseAgendaParts(item.ProjectPartsJSON)
	if err != nil {
		return err
	}
	if _, err := parseDailyCounts(item.DailyCountsJSON); err != nil {
		return err
	}
	if len(parts) > 0 && (!item.Task || item.RepeatDaily) {
		return errors.New("alt görevler tek seferlik görevlerde kullanılabilir")
	}
	encoded, err := json.Marshal(parts)
	if err != nil {
		return err
	}
	item.ProjectPartsJSON = string(encoded)
	if len(parts) > 0 {
		item.Completed = partsCompleted(parts)
		item.NotDone = false
	}
	return a.mutate(func(s *Snapshot, now int64) error {
		if item.ID == 0 {
			item.ID = nextID(s)
		}
		item.UpdatedAtMillis = now
		s.Agenda = upsert(s.Agenda, item, func(v AgendaRecord) int64 { return v.ID })
		removeDeletion(s, "agenda", item.ID)
		return nil
	})
}

func (a *App) ToggleAgendaCompleted(id int64, day string) error {
	selected, err := time.Parse("2006-01-02", day)
	if err != nil {
		return errors.New("geçersiz tarih")
	}
	return a.mutate(func(s *Snapshot, now int64) error {
		for index := range s.Agenda {
			item := &s.Agenda[index]
			if item.ID != id || !item.Task {
				continue
			}
			if parts, err := parseAgendaParts(item.ProjectPartsJSON); err != nil {
				return err
			} else if len(parts) > 0 {
				return errors.New("ilerlemeyi alt görevlerden güncelle")
			}
			item.Completed = !item.Completed
			if item.Completed {
				item.NotDone = false
				yesterday := selected.AddDate(0, 0, -1).Format("2006-01-02")
				if item.LastCompletedDay != nil && *item.LastCompletedDay == yesterday {
					item.Streak++
				} else if item.LastCompletedDay == nil || *item.LastCompletedDay != day {
					item.Streak = 1
				}
				value := day
				item.LastCompletedDay = &value
			}
			item.UpdatedAtMillis = now
			return nil
		}
		return errors.New("ajanda kaydı bulunamadı")
	})
}

func (a *App) ToggleAgendaNotDone(id int64) error {
	return a.mutate(func(s *Snapshot, now int64) error {
		for index := range s.Agenda {
			if s.Agenda[index].ID == id && s.Agenda[index].Task {
				if parts, err := parseAgendaParts(s.Agenda[index].ProjectPartsJSON); err != nil {
					return err
				} else if len(parts) > 0 {
					return errors.New("ilerlemeyi alt görevlerden güncelle")
				}
				s.Agenda[index].NotDone = !s.Agenda[index].NotDone
				s.Agenda[index].Completed = false
				s.Agenda[index].UpdatedAtMillis = now
				return nil
			}
		}
		return errors.New("ajanda kaydı bulunamadı")
	})
}

func (a *App) MoveAgendaToGeneral(id int64) error {
	return a.mutate(func(s *Snapshot, now int64) error {
		for index := range s.Agenda {
			if s.Agenda[index].ID == id && s.Agenda[index].Task {
				item := &s.Agenda[index]
				item.GeneralGoal = true
				item.RepeatDaily = false
				item.NotifyEnabled = false
				if parts, err := parseAgendaParts(item.ProjectPartsJSON); err != nil {
					return err
				} else if len(parts) > 0 {
					item.Completed = partsCompleted(parts)
				} else {
					item.Completed = false
				}
				item.NotDone = false
				item.UpdatedAtMillis = now
				return nil
			}
		}
		return errors.New("ajanda kaydı bulunamadı")
	})
}

func (a *App) MoveAgendaToDay(id int64, day string) error {
	if _, err := time.Parse("2006-01-02", day); err != nil {
		return errors.New("geçersiz tarih")
	}
	return a.mutate(func(s *Snapshot, now int64) error {
		for index := range s.Agenda {
			if s.Agenda[index].ID == id && s.Agenda[index].Task {
				item := &s.Agenda[index]
				item.GeneralGoal = false
				item.Day = day
				item.RepeatDaily = false
				if parts, err := parseAgendaParts(item.ProjectPartsJSON); err != nil {
					return err
				} else if len(parts) > 0 {
					item.Completed = partsCompleted(parts)
				} else {
					item.Completed = false
				}
				item.NotDone = false
				item.UpdatedAtMillis = now
				return nil
			}
		}
		return errors.New("ajanda kaydı bulunamadı")
	})
}

func (a *App) PostponeAgenda(id int64, day string) error { return a.MoveAgendaToDay(id, day) }

func (a *App) SaveAgendaTemplate(item AgendaTemplateRecord) error {
	item.ID = strings.TrimSpace(item.ID)
	item.Text = strings.TrimSpace(item.Text)
	item.Category = strings.TrimSpace(item.Category)
	item.Subcategory = strings.TrimSpace(item.Subcategory)
	if item.ID == "" || len(item.ID) > 120 || item.Text == "" || len(item.Text) > 4000 || len(item.Category) > 60 || len(item.Subcategory) > 60 {
		return errors.New("geçersiz hazır görev")
	}
	return a.mutate(func(s *Snapshot, now int64) error {
		item.UpdatedAtMillis = now
		item.Hidden = false
		s.AgendaTemplates = upsertString(s.AgendaTemplates, item, func(v AgendaTemplateRecord) string { return v.ID })
		return nil
	})
}

func (a *App) SetAgendaTemplateFavorite(id string, favorite bool) error {
	return a.mutate(func(s *Snapshot, now int64) error {
		for index := range s.AgendaTemplates {
			if s.AgendaTemplates[index].ID == id {
				s.AgendaTemplates[index].Favorite = favorite
				s.AgendaTemplates[index].UpdatedAtMillis = now
				return nil
			}
		}
		return errors.New("hazır görev bulunamadı")
	})
}

func (a *App) RemoveAgendaTemplate(id string) error {
	return a.mutate(func(s *Snapshot, now int64) error {
		for index := range s.AgendaTemplates {
			if s.AgendaTemplates[index].ID == id {
				s.AgendaTemplates[index].Hidden = true
				s.AgendaTemplates[index].UpdatedAtMillis = now
				return nil
			}
		}
		return errors.New("hazır görev bulunamadı")
	})
}

func (a *App) SaveAgendaCategory(item AgendaCategoryRecord) error {
	item.Category = strings.TrimSpace(item.Category)
	item.Subcategory = strings.TrimSpace(item.Subcategory)
	if item.Category == "" || len(item.Category) > 60 || len(item.Subcategory) > 60 {
		return errors.New("geçersiz kategori")
	}
	return a.mutate(func(s *Snapshot, now int64) error {
		for index := range s.AgendaCategories {
			if strings.EqualFold(s.AgendaCategories[index].Category, item.Category) && strings.EqualFold(s.AgendaCategories[index].Subcategory, item.Subcategory) {
				s.AgendaCategories[index].Hidden = false
				s.AgendaCategories[index].UpdatedAtMillis = now
				return nil
			}
		}
		if item.ID == 0 {
			item.ID = nextCategoryID(s)
		}
		item.Hidden = false
		item.UpdatedAtMillis = now
		s.AgendaCategories = upsert(s.AgendaCategories, item, func(v AgendaCategoryRecord) int64 { return v.ID })
		return nil
	})
}

func (a *App) RemoveAgendaCategory(id int64) error {
	return a.mutate(func(s *Snapshot, now int64) error {
		for index := range s.AgendaCategories {
			if s.AgendaCategories[index].ID == id {
				s.AgendaCategories[index].Hidden = true
				s.AgendaCategories[index].UpdatedAtMillis = now
				return nil
			}
		}
		return errors.New("kategori bulunamadı")
	})
}

func (a *App) HideAgendaSuggestion(key string) error {
	key = strings.TrimSpace(key)
	if key == "" || len(key) > 4200 {
		return errors.New("geçersiz öneri")
	}
	return a.mutate(func(s *Snapshot, now int64) error {
		s.HiddenAgendaSuggestions = upsertString(s.HiddenAgendaSuggestions, HiddenAgendaSuggestionRecord{Key: key, HiddenAtMillis: now}, func(v HiddenAgendaSuggestionRecord) string { return v.Key })
		return nil
	})
}

func (a *App) SavePrivate(item PrivateActivity) error {
	if item.StartMillis <= 0 || (item.EndMillis != nil && *item.EndMillis <= item.StartMillis) {
		return errors.New("geçersiz süre")
	}
	return a.mutate(func(s *Snapshot, now int64) error {
		if item.ID == 0 {
			item.ID = nextID(s)
		}
		item.UpdatedAtMillis = now
		s.PrivateActivities = upsert(s.PrivateActivities, item, func(v PrivateActivity) int64 { return v.ID })
		removeDeletion(s, "private", item.ID)
		return nil
	})
}

func (a *App) DeleteRecord(kind string, id int64) error {
	if id == 0 {
		return errors.New("geçersiz kayıt")
	}
	return a.mutate(func(s *Snapshot, now int64) error {
		switch kind {
		case "sleep":
			s.Sleeps = remove(s.Sleeps, id, func(v SleepRecord) int64 { return v.ID })
		case "meal":
			s.Meals = remove(s.Meals, id, func(v MealRecord) int64 { return v.ID })
		case "water":
			s.Water = remove(s.Water, id, func(v WaterRecord) int64 { return v.ID })
		case "agenda":
			s.Agenda = remove(s.Agenda, id, func(v AgendaRecord) int64 { return v.ID })
		case "private":
			s.PrivateActivities = remove(s.PrivateActivities, id, func(v PrivateActivity) int64 { return v.ID })
		default:
			return errors.New("bilinmeyen kayıt türü")
		}
		upsertDeletion(s, DeletionRecord{Kind: kind, ID: id, UpdatedAtMillis: now})
		return nil
	})
}

func upsertDeletion(snapshot *Snapshot, item DeletionRecord) {
	for index := range snapshot.Deletions {
		if snapshot.Deletions[index].Kind == item.Kind && snapshot.Deletions[index].ID == item.ID {
			snapshot.Deletions[index] = item
			return
		}
	}
	snapshot.Deletions = append(snapshot.Deletions, item)
}

func (a *App) mutate(change func(*Snapshot, int64) error) error {
	a.mu.Lock()
	if a.snapshot == nil {
		a.snapshot = &Snapshot{WaterGoalMl: 2500}
	}
	encoded, err := encodeSnapshot(a.snapshot)
	if err != nil {
		a.mu.Unlock()
		return err
	}
	copy, err := decodeSnapshot(encoded)
	if err != nil {
		a.mu.Unlock()
		return err
	}
	now := time.Now().UnixMilli()
	if err = change(copy, now); err != nil {
		a.mu.Unlock()
		return err
	}
	copy.GeneratedAtMillis = now
	encoded, err = encodeSnapshot(copy)
	if err == nil {
		err = saveSnapshot(encoded)
	}
	if err == nil {
		a.snapshot = copy
	}
	a.mu.Unlock()
	if err != nil {
		return err
	}
	if a.ctx != nil {
		runtime.EventsEmit(a.ctx, "sync:updated", a.GetState())
	}
	return nil
}

func nextID(s *Snapshot) int64 {
	minimum := int64(0)
	visit := func(id int64) {
		if id < minimum {
			minimum = id
		}
	}
	for _, v := range s.Sleeps {
		visit(v.ID)
	}
	for _, v := range s.Meals {
		visit(v.ID)
	}
	for _, v := range s.Water {
		visit(v.ID)
	}
	for _, v := range s.Agenda {
		visit(v.ID)
	}
	for _, v := range s.PrivateActivities {
		visit(v.ID)
	}
	if minimum == -9223372036854775808 {
		panic(fmt.Errorf("record id space exhausted"))
	}
	return minimum - 1
}

func nextCategoryID(s *Snapshot) int64 {
	minimum := int64(0)
	for _, item := range s.AgendaCategories {
		if item.ID < minimum {
			minimum = item.ID
		}
	}
	if minimum == -9223372036854775808 {
		panic("category id space exhausted")
	}
	return minimum - 1
}

func upsert[T any](items []T, value T, id func(T) int64) []T {
	result := append([]T{}, items...)
	for index, item := range result {
		if id(item) == id(value) {
			result[index] = value
			return result
		}
	}
	return append(result, value)
}
func upsertString[T any](items []T, value T, id func(T) string) []T {
	result := append([]T{}, items...)
	for index, item := range result {
		if id(item) == id(value) {
			result[index] = value
			return result
		}
	}
	return append(result, value)
}
func remove[T any](items []T, target int64, id func(T) int64) []T {
	result := make([]T, 0, len(items))
	for _, item := range items {
		if id(item) != target {
			result = append(result, item)
		}
	}
	return result
}
func removeDeletion(s *Snapshot, kind string, id int64) {
	result := make([]DeletionRecord, 0, len(s.Deletions))
	for _, item := range s.Deletions {
		if item.Kind != kind || item.ID != id {
			result = append(result, item)
		}
	}
	s.Deletions = result
}
func validMealType(value string) bool {
	return value == "BREAKFAST" || value == "LUNCH" || value == "DINNER" || value == "SNACK"
}
