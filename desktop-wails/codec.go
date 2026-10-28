package main

import (
	"bytes"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
)

const (
	snapshotMagic   = uint32(0x47544B50)
	protocolVersion = int32(7)
	maxRecords      = int32(1_000_000)
	maxTextBytes    = int32(1_000_000)
)

type decoder struct {
	reader      *bytes.Reader
	version     int32
	generatedAt int64
}

func decodeSnapshot(data []byte) (*Snapshot, error) {
	d := decoder{reader: bytes.NewReader(data)}
	magic, err := d.u32()
	if err != nil || magic != snapshotMagic {
		return nil, errors.New("geçersiz senkron dosyası")
	}
	version, err := d.i32()
	if err != nil || version < 1 || version > protocolVersion {
		return nil, fmt.Errorf("desteklenmeyen protokol: %d", version)
	}
	d.version = version
	s := &Snapshot{}
	if s.GeneratedAtMillis, err = d.i64(); err != nil {
		return nil, err
	}
	d.generatedAt = s.GeneratedAtMillis
	if s.ActiveSleepStartMillis, err = d.nullableInt64(); err != nil {
		return nil, err
	}
	if version >= 3 {
		if s.ActiveSleepUpdatedAtMillis, err = d.i64(); err != nil {
			return nil, err
		}
	} else if s.ActiveSleepStartMillis != nil {
		s.ActiveSleepUpdatedAtMillis = *s.ActiveSleepStartMillis
	}
	if s.Sleeps, err = readList(&d, readSleep); err != nil {
		return nil, err
	}
	hasActive, err := d.boolean()
	if err != nil {
		return nil, err
	}
	if hasActive {
		item, readErr := readMeal(&d)
		if readErr != nil {
			return nil, readErr
		}
		s.ActiveMeal = &item
	}
	if s.Meals, err = readList(&d, readMeal); err != nil {
		return nil, err
	}
	if s.Water, err = readList(&d, readWater); err != nil {
		return nil, err
	}
	if s.WaterGoalMl, err = d.i32(); err != nil {
		return nil, err
	}
	if version >= 3 {
		if s.WaterGoalUpdatedAtMillis, err = d.i64(); err != nil {
			return nil, err
		}
	} else {
		s.WaterGoalUpdatedAtMillis = s.GeneratedAtMillis
	}
	if s.Agenda, err = readList(&d, func(d *decoder) (AgendaRecord, error) { return readAgenda(d, version) }); err != nil {
		return nil, err
	}
	if version >= 5 {
		if s.AgendaTemplates, err = readList(&d, readAgendaTemplate); err != nil {
			return nil, err
		}
		if s.AgendaCategories, err = readList(&d, readAgendaCategory); err != nil {
			return nil, err
		}
		if s.HiddenAgendaSuggestions, err = readList(&d, readHiddenAgendaSuggestion); err != nil {
			return nil, err
		}
	}
	if s.PrivateActivities, err = readList(&d, readPrivateActivity); err != nil {
		return nil, err
	}
	if version >= 3 {
		if s.Deletions, err = readList(&d, readDeletion); err != nil {
			return nil, err
		}
	}
	if d.reader.Len() != 0 {
		return nil, errors.New("senkron paketinde beklenmeyen veri var")
	}
	return s, nil
}

func readSleep(d *decoder) (SleepRecord, error) {
	var v SleepRecord
	var err error
	if v.ID, err = d.i64(); err != nil {
		return v, err
	}
	if v.StartMillis, err = d.i64(); err != nil {
		return v, err
	}
	if v.EndMillis, err = d.i64(); err != nil {
		return v, err
	}
	v.UpdatedAtMillis, err = d.i64()
	return v, err
}
func readMeal(d *decoder) (MealRecord, error) {
	var v MealRecord
	var err error
	if v.ID, err = d.i64(); err != nil {
		return v, err
	}
	if v.Type, err = d.text(); err != nil {
		return v, err
	}
	if v.StartMillis, err = d.i64(); err != nil {
		return v, err
	}
	if v.EndMillis, err = d.i64(); err != nil {
		return v, err
	}
	if v.ItemsJSON, err = d.text(); err != nil {
		return v, err
	}
	if v.Quick, err = d.boolean(); err != nil {
		return v, err
	}
	v.UpdatedAtMillis, err = d.i64()
	return v, err
}
func readWater(d *decoder) (WaterRecord, error) {
	var v WaterRecord
	var err error
	if v.ID, err = d.i64(); err != nil {
		return v, err
	}
	if v.AmountMl, err = d.i32(); err != nil {
		return v, err
	}
	if v.TimestampMillis, err = d.i64(); err != nil {
		return v, err
	}
	if d.version >= 3 {
		v.UpdatedAtMillis, err = d.i64()
	} else {
		v.UpdatedAtMillis = v.TimestampMillis
	}
	return v, err
}
func readAgenda(d *decoder, version int32) (AgendaRecord, error) {
	v := AgendaRecord{ProjectPartsJSON: "[]"}
	var err error
	if v.ID, err = d.i64(); err != nil {
		return v, err
	}
	if v.Day, err = d.text(); err != nil {
		return v, err
	}
	if v.Text, err = d.text(); err != nil {
		return v, err
	}
	if v.Task, err = d.boolean(); err != nil {
		return v, err
	}
	if v.Completed, err = d.boolean(); err != nil {
		return v, err
	}
	if v.NotDone, err = d.boolean(); err != nil {
		return v, err
	}
	if v.RepeatDaily, err = d.boolean(); err != nil {
		return v, err
	}
	if version >= 2 {
		if v.GeneralGoal, err = d.boolean(); err != nil {
			return v, err
		}
	}
	if v.Streak, err = d.i32(); err != nil {
		return v, err
	}
	if v.NotifyEnabled, err = d.boolean(); err != nil {
		return v, err
	}
	if v.NotifyHour, err = d.i32(); err != nil {
		return v, err
	}
	if v.NotifyMinute, err = d.i32(); err != nil {
		return v, err
	}
	if version >= 4 {
		if v.Category, err = d.text(); err != nil {
			return v, err
		}
		if v.Subcategory, err = d.text(); err != nil {
			return v, err
		}
	}
	if version >= 5 {
		if v.LastCompletedDay, err = d.nullableText(); err != nil {
			return v, err
		}
	}
	if d.version >= 3 {
		v.UpdatedAtMillis, err = d.i64()
	} else {
		v.UpdatedAtMillis = d.generatedAt
	}
	if err == nil && version >= 6 {
		v.ProjectPartsJSON, err = d.text()
	}
	if err == nil && version >= 7 {
		v.DailyCountsJSON, err = d.text()
	}
	if err == nil {
		_, err = parseDailyCounts(v.DailyCountsJSON)
	}
	if err == nil {
		_, err = parseAgendaParts(v.ProjectPartsJSON)
	}
	return v, err
}
func readAgendaTemplate(d *decoder) (AgendaTemplateRecord, error) {
	var v AgendaTemplateRecord
	var err error
	if v.ID, err = d.text(); err != nil {
		return v, err
	}
	if v.Text, err = d.text(); err != nil {
		return v, err
	}
	if v.Category, err = d.text(); err != nil {
		return v, err
	}
	if v.Subcategory, err = d.text(); err != nil {
		return v, err
	}
	if v.Favorite, err = d.boolean(); err != nil {
		return v, err
	}
	if v.BuiltIn, err = d.boolean(); err != nil {
		return v, err
	}
	if v.Hidden, err = d.boolean(); err != nil {
		return v, err
	}
	v.UpdatedAtMillis, err = d.i64()
	return v, err
}
func readAgendaCategory(d *decoder) (AgendaCategoryRecord, error) {
	var v AgendaCategoryRecord
	var err error
	if v.ID, err = d.i64(); err != nil {
		return v, err
	}
	if v.Category, err = d.text(); err != nil {
		return v, err
	}
	if v.Subcategory, err = d.text(); err != nil {
		return v, err
	}
	if v.Hidden, err = d.boolean(); err != nil {
		return v, err
	}
	v.UpdatedAtMillis, err = d.i64()
	return v, err
}
func readHiddenAgendaSuggestion(d *decoder) (HiddenAgendaSuggestionRecord, error) {
	var v HiddenAgendaSuggestionRecord
	var err error
	if v.Key, err = d.text(); err != nil {
		return v, err
	}
	v.HiddenAtMillis, err = d.i64()
	return v, err
}
func readPrivateActivity(d *decoder) (PrivateActivity, error) {
	var v PrivateActivity
	var err error
	if v.ID, err = d.i64(); err != nil {
		return v, err
	}
	if v.StartMillis, err = d.i64(); err != nil {
		return v, err
	}
	if v.EndMillis, err = d.nullableInt64(); err != nil {
		return v, err
	}
	if d.version >= 3 {
		v.UpdatedAtMillis, err = d.i64()
	} else if v.EndMillis != nil {
		v.UpdatedAtMillis = *v.EndMillis
	} else {
		v.UpdatedAtMillis = v.StartMillis
	}
	return v, err
}

func readDeletion(d *decoder) (DeletionRecord, error) {
	var v DeletionRecord
	var err error
	if v.Kind, err = d.text(); err != nil {
		return v, err
	}
	if v.ID, err = d.i64(); err != nil {
		return v, err
	}
	v.UpdatedAtMillis, err = d.i64()
	return v, err
}
func readList[T any](d *decoder, read func(*decoder) (T, error)) ([]T, error) {
	size, err := d.i32()
	if err != nil {
		return nil, err
	}
	if size < 0 || size > maxRecords {
		return nil, errors.New("geçersiz kayıt sayısı")
	}
	items := make([]T, 0, size)
	for range size {
		item, readErr := read(d)
		if readErr != nil {
			return nil, readErr
		}
		items = append(items, item)
	}
	return items, nil
}
func (d *decoder) i64() (int64, error) {
	var v int64
	err := binary.Read(d.reader, binary.BigEndian, &v)
	return v, err
}
func (d *decoder) i32() (int32, error) {
	var v int32
	err := binary.Read(d.reader, binary.BigEndian, &v)
	return v, err
}
func (d *decoder) u32() (uint32, error) {
	var v uint32
	err := binary.Read(d.reader, binary.BigEndian, &v)
	return v, err
}
func (d *decoder) boolean() (bool, error) {
	value, err := d.reader.ReadByte()
	if err != nil {
		return false, err
	}
	if value > 1 {
		return false, errors.New("geçersiz boolean")
	}
	return value == 1, nil
}
func (d *decoder) nullableInt64() (*int64, error) {
	present, err := d.boolean()
	if err != nil || !present {
		return nil, err
	}
	value, err := d.i64()
	return &value, err
}
func (d *decoder) nullableText() (*string, error) {
	present, err := d.boolean()
	if err != nil || !present {
		return nil, err
	}
	value, err := d.text()
	return &value, err
}
func (d *decoder) text() (string, error) {
	size, err := d.i32()
	if err != nil {
		return "", err
	}
	if size < 0 || size > maxTextBytes {
		return "", errors.New("geçersiz metin boyutu")
	}
	data := make([]byte, size)
	if _, err = io.ReadFull(d.reader, data); err != nil {
		return "", err
	}
	return string(data), nil
}
