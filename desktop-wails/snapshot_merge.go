package main

import (
	"bytes"
	"encoding/binary"
	"errors"
	"sort"
	"time"
)

func mergeSnapshots(local, remote *Snapshot) *Snapshot {
	if local == nil {
		local = &Snapshot{}
	}
	if remote == nil {
		remote = &Snapshot{}
	}
	result := &Snapshot{GeneratedAtMillis: time.Now().UnixMilli()}
	deletions := make(map[string]DeletionRecord)
	for _, item := range append(append([]DeletionRecord{}, local.Deletions...), remote.Deletions...) {
		key := deletionKey(item.Kind, item.ID)
		if old, ok := deletions[key]; !ok || item.UpdatedAtMillis > old.UpdatedAtMillis {
			deletions[key] = item
		}
	}
	result.Deletions = make([]DeletionRecord, 0, len(deletions))
	for _, item := range deletions {
		result.Deletions = append(result.Deletions, item)
	}
	sort.Slice(result.Deletions, func(i, j int) bool {
		if result.Deletions[i].Kind == result.Deletions[j].Kind {
			return result.Deletions[i].ID < result.Deletions[j].ID
		}
		return result.Deletions[i].Kind < result.Deletions[j].Kind
	})

	result.Sleeps = mergeRecords(local.Sleeps, remote.Sleeps, "sleep", deletions,
		func(v SleepRecord) int64 { return v.ID }, func(v SleepRecord) int64 { return v.UpdatedAtMillis })
	result.Meals = mergeRecords(local.Meals, remote.Meals, "meal", deletions,
		func(v MealRecord) int64 { return v.ID }, func(v MealRecord) int64 { return v.UpdatedAtMillis })
	result.Water = mergeRecords(local.Water, remote.Water, "water", deletions,
		func(v WaterRecord) int64 { return v.ID }, func(v WaterRecord) int64 { return v.UpdatedAtMillis })
	result.Agenda = mergeRecords(local.Agenda, remote.Agenda, "agenda", deletions,
		func(v AgendaRecord) int64 { return v.ID }, func(v AgendaRecord) int64 { return v.UpdatedAtMillis })
	result.AgendaTemplates = mergeCatalogRecords(local.AgendaTemplates, remote.AgendaTemplates,
		func(v AgendaTemplateRecord) string { return v.ID }, func(v AgendaTemplateRecord) int64 { return v.UpdatedAtMillis })
	result.AgendaCategories = mergeCatalogRecords(local.AgendaCategories, remote.AgendaCategories,
		func(v AgendaCategoryRecord) int64 { return v.ID }, func(v AgendaCategoryRecord) int64 { return v.UpdatedAtMillis })
	result.HiddenAgendaSuggestions = mergeCatalogRecords(local.HiddenAgendaSuggestions, remote.HiddenAgendaSuggestions,
		func(v HiddenAgendaSuggestionRecord) string { return v.Key }, func(v HiddenAgendaSuggestionRecord) int64 { return v.HiddenAtMillis })
	result.PrivateActivities = mergeRecords(local.PrivateActivities, remote.PrivateActivities, "private", deletions,
		func(v PrivateActivity) int64 { return v.ID }, func(v PrivateActivity) int64 { return v.UpdatedAtMillis })

	result.ActiveSleepStartMillis = local.ActiveSleepStartMillis
	result.ActiveSleepUpdatedAtMillis = local.ActiveSleepUpdatedAtMillis
	if remote.ActiveSleepStartMillis != nil && remote.ActiveSleepUpdatedAtMillis > result.ActiveSleepUpdatedAtMillis {
		value := *remote.ActiveSleepStartMillis
		result.ActiveSleepStartMillis = &value
		result.ActiveSleepUpdatedAtMillis = remote.ActiveSleepUpdatedAtMillis
	}
	for _, sleep := range result.Sleeps {
		if result.ActiveSleepStartMillis != nil && sleep.StartMillis == *result.ActiveSleepStartMillis && sleep.UpdatedAtMillis >= result.ActiveSleepUpdatedAtMillis {
			result.ActiveSleepStartMillis = nil
			result.ActiveSleepUpdatedAtMillis = sleep.UpdatedAtMillis
		}
	}
	result.ActiveMeal = copyMeal(local.ActiveMeal)
	if remote.ActiveMeal != nil && (result.ActiveMeal == nil || remote.ActiveMeal.UpdatedAtMillis > result.ActiveMeal.UpdatedAtMillis) {
		result.ActiveMeal = copyMeal(remote.ActiveMeal)
	}
	for _, meal := range result.Meals {
		if result.ActiveMeal != nil && meal.ID == result.ActiveMeal.ID && meal.UpdatedAtMillis >= result.ActiveMeal.UpdatedAtMillis {
			result.ActiveMeal = nil
		}
	}
	result.WaterGoalMl, result.WaterGoalUpdatedAtMillis = local.WaterGoalMl, local.WaterGoalUpdatedAtMillis
	if result.WaterGoalMl == 0 || remote.WaterGoalUpdatedAtMillis > result.WaterGoalUpdatedAtMillis {
		result.WaterGoalMl, result.WaterGoalUpdatedAtMillis = remote.WaterGoalMl, remote.WaterGoalUpdatedAtMillis
	}
	if result.WaterGoalMl == 0 {
		result.WaterGoalMl = 2500
	}
	return result
}

func mergeRecords[T any](left, right []T, kind string, deletions map[string]DeletionRecord,
	id func(T) int64, updated func(T) int64) []T {
	items := make(map[int64]T)
	for _, item := range append(append([]T{}, left...), right...) {
		key := id(item)
		previous, exists := items[key]
		if !exists || updated(item) > updated(previous) {
			items[key] = item
		}
	}
	result := make([]T, 0, len(items))
	for key, item := range items {
		if deleted, ok := deletions[deletionKey(kind, key)]; !ok || deleted.UpdatedAtMillis < updated(item) {
			result = append(result, item)
		}
	}
	sort.Slice(result, func(i, j int) bool { return id(result[i]) < id(result[j]) })
	return result
}

func mergeCatalogRecords[T any, K comparable](left, right []T, key func(T) K, updated func(T) int64) []T {
	items := make(map[K]T)
	for _, item := range append(append([]T{}, left...), right...) {
		id := key(item)
		previous, exists := items[id]
		if !exists || updated(item) > updated(previous) {
			items[id] = item
		}
	}
	result := make([]T, 0, len(items))
	for _, item := range items {
		result = append(result, item)
	}
	return result
}

func deletionKey(kind string, id int64) string { return kind + ":" + stringID(id) }
func stringID(id int64) string {
	var data [8]byte
	binary.BigEndian.PutUint64(data[:], uint64(id))
	return string(data[:])
}
func copyMeal(item *MealRecord) *MealRecord {
	if item == nil {
		return nil
	}
	copy := *item
	return &copy
}

func encodeSnapshot(value *Snapshot) ([]byte, error) {
	if value == nil {
		return nil, errors.New("nil snapshot")
	}
	b := new(bytes.Buffer)
	write := func(v any) error { return binary.Write(b, binary.BigEndian, v) }
	text := func(v string) error {
		if len(v) > int(maxTextBytes) {
			return errors.New("text too long")
		}
		if err := write(int32(len(v))); err != nil {
			return err
		}
		_, err := b.WriteString(v)
		return err
	}
	nullable := func(v *int64) error {
		if v == nil {
			return b.WriteByte(0)
		}
		if err := b.WriteByte(1); err != nil {
			return err
		}
		return write(*v)
	}
	nullableText := func(v *string) error {
		if v == nil {
			return b.WriteByte(0)
		}
		if err := b.WriteByte(1); err != nil {
			return err
		}
		return text(*v)
	}
	list := func(length int) error {
		if length > int(maxRecords) {
			return errors.New("too many records")
		}
		return write(int32(length))
	}
	if err := write(snapshotMagic); err != nil {
		return nil, err
	}
	if err := write(protocolVersion); err != nil {
		return nil, err
	}
	if err := write(value.GeneratedAtMillis); err != nil {
		return nil, err
	}
	if err := nullable(value.ActiveSleepStartMillis); err != nil {
		return nil, err
	}
	if err := write(value.ActiveSleepUpdatedAtMillis); err != nil {
		return nil, err
	}
	if err := list(len(value.Sleeps)); err != nil {
		return nil, err
	}
	for _, v := range value.Sleeps {
		for _, n := range []int64{v.ID, v.StartMillis, v.EndMillis, v.UpdatedAtMillis} {
			if err := write(n); err != nil {
				return nil, err
			}
		}
	}
	if value.ActiveMeal == nil {
		_ = b.WriteByte(0)
	} else {
		_ = b.WriteByte(1)
		if err := writeMealSnapshot(b, text, *value.ActiveMeal); err != nil {
			return nil, err
		}
	}
	if err := list(len(value.Meals)); err != nil {
		return nil, err
	}
	for _, v := range value.Meals {
		if err := writeMealSnapshot(b, text, v); err != nil {
			return nil, err
		}
	}
	if err := list(len(value.Water)); err != nil {
		return nil, err
	}
	for _, v := range value.Water {
		if err := write(v.ID); err != nil {
			return nil, err
		}
		if err := write(v.AmountMl); err != nil {
			return nil, err
		}
		if err := write(v.TimestampMillis); err != nil {
			return nil, err
		}
		if err := write(v.UpdatedAtMillis); err != nil {
			return nil, err
		}
	}
	if err := write(value.WaterGoalMl); err != nil {
		return nil, err
	}
	if err := write(value.WaterGoalUpdatedAtMillis); err != nil {
		return nil, err
	}
	if err := list(len(value.Agenda)); err != nil {
		return nil, err
	}
	for _, v := range value.Agenda {
		if err := write(v.ID); err != nil {
			return nil, err
		}
		if err := text(v.Day); err != nil {
			return nil, err
		}
		if err := text(v.Text); err != nil {
			return nil, err
		}
		for _, flag := range []bool{v.Task, v.Completed, v.NotDone, v.RepeatDaily, v.GeneralGoal} {
			if flag {
				_ = b.WriteByte(1)
			} else {
				_ = b.WriteByte(0)
			}
		}
		if err := write(v.Streak); err != nil {
			return nil, err
		}
		if v.NotifyEnabled {
			_ = b.WriteByte(1)
		} else {
			_ = b.WriteByte(0)
		}
		if err := write(v.NotifyHour); err != nil {
			return nil, err
		}
		if err := write(v.NotifyMinute); err != nil {
			return nil, err
		}
		if err := text(v.Category); err != nil {
			return nil, err
		}
		if err := text(v.Subcategory); err != nil {
			return nil, err
		}
		if err := nullableText(v.LastCompletedDay); err != nil {
			return nil, err
		}
		if err := write(v.UpdatedAtMillis); err != nil {
			return nil, err
		}
		if err := text(v.ProjectPartsJSON); err != nil {
			return nil, err
		}
		if err := text(v.DailyCountsJSON); err != nil {
			return nil, err
		}
	}
	if err := list(len(value.AgendaTemplates)); err != nil {
		return nil, err
	}
	for _, v := range value.AgendaTemplates {
		if err := text(v.ID); err != nil {
			return nil, err
		}
		if err := text(v.Text); err != nil {
			return nil, err
		}
		if err := text(v.Category); err != nil {
			return nil, err
		}
		if err := text(v.Subcategory); err != nil {
			return nil, err
		}
		for _, flag := range []bool{v.Favorite, v.BuiltIn, v.Hidden} {
			if flag {
				_ = b.WriteByte(1)
			} else {
				_ = b.WriteByte(0)
			}
		}
		if err := write(v.UpdatedAtMillis); err != nil {
			return nil, err
		}
	}
	if err := list(len(value.AgendaCategories)); err != nil {
		return nil, err
	}
	for _, v := range value.AgendaCategories {
		if err := write(v.ID); err != nil {
			return nil, err
		}
		if err := text(v.Category); err != nil {
			return nil, err
		}
		if err := text(v.Subcategory); err != nil {
			return nil, err
		}
		if v.Hidden {
			_ = b.WriteByte(1)
		} else {
			_ = b.WriteByte(0)
		}
		if err := write(v.UpdatedAtMillis); err != nil {
			return nil, err
		}
	}
	if err := list(len(value.HiddenAgendaSuggestions)); err != nil {
		return nil, err
	}
	for _, v := range value.HiddenAgendaSuggestions {
		if err := text(v.Key); err != nil {
			return nil, err
		}
		if err := write(v.HiddenAtMillis); err != nil {
			return nil, err
		}
	}
	if err := list(len(value.PrivateActivities)); err != nil {
		return nil, err
	}
	for _, v := range value.PrivateActivities {
		if err := write(v.ID); err != nil {
			return nil, err
		}
		if err := write(v.StartMillis); err != nil {
			return nil, err
		}
		if err := nullable(v.EndMillis); err != nil {
			return nil, err
		}
		if err := write(v.UpdatedAtMillis); err != nil {
			return nil, err
		}
	}
	if err := list(len(value.Deletions)); err != nil {
		return nil, err
	}
	for _, v := range value.Deletions {
		if err := text(v.Kind); err != nil {
			return nil, err
		}
		if err := write(v.ID); err != nil {
			return nil, err
		}
		if err := write(v.UpdatedAtMillis); err != nil {
			return nil, err
		}
	}
	return b.Bytes(), nil
}

func writeMealSnapshot(b *bytes.Buffer, text func(string) error, v MealRecord) error {
	write := func(value any) error { return binary.Write(b, binary.BigEndian, value) }
	if err := write(v.ID); err != nil {
		return err
	}
	if err := text(v.Type); err != nil {
		return err
	}
	if err := write(v.StartMillis); err != nil {
		return err
	}
	if err := write(v.EndMillis); err != nil {
		return err
	}
	if err := text(v.ItemsJSON); err != nil {
		return err
	}
	if v.Quick {
		_ = b.WriteByte(1)
	} else {
		_ = b.WriteByte(0)
	}
	return write(v.UpdatedAtMillis)
}
