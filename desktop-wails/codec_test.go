package main

import (
	"bytes"
	"crypto/aes"
	"crypto/cipher"
	"crypto/sha256"
	"encoding/binary"
	"testing"

	"golang.org/x/crypto/pbkdf2"
)

func TestDecodeAndroidSnapshot(t *testing.T) {
	var data bytes.Buffer
	write := func(value any) {
		if err := binary.Write(&data, binary.BigEndian, value); err != nil {
			t.Fatal(err)
		}
	}
	write(snapshotMagic)
	write(int32(2))
	write(int64(1234))
	write(uint8(0))
	write(int32(1))
	write(int64(7))
	write(int64(100))
	write(int64(200))
	write(int64(201))
	write(uint8(0))
	write(int32(0))
	write(int32(0))
	write(int32(2500))
	write(int32(0))
	write(int32(0))
	snapshot, err := decodeSnapshot(data.Bytes())
	if err != nil {
		t.Fatal(err)
	}
	if len(snapshot.Sleeps) != 1 || snapshot.Sleeps[0].ID != 7 || snapshot.WaterGoalMl != 2500 {
		t.Fatalf("unexpected snapshot: %#v", snapshot)
	}
}

func TestSnapshotV6RoundTrip(t *testing.T) {
	now := int64(123456)
	end := now + 10
	want := &Snapshot{
		GeneratedAtMillis:          now,
		ActiveSleepStartMillis:     &now,
		ActiveSleepUpdatedAtMillis: now,
		Sleeps:                     []SleepRecord{{ID: -1, StartMillis: now, EndMillis: end, UpdatedAtMillis: end}},
		Water:                      []WaterRecord{{ID: -2, AmountMl: 125, TimestampMillis: now, UpdatedAtMillis: now}},
		WaterGoalMl:                2500, WaterGoalUpdatedAtMillis: now,
		Agenda:                  []AgendaRecord{{ID: -3, Day: "2026-09-19", Text: "Valizi sil", Task: true, GeneralGoal: true, LastCompletedDay: ptr("2026-09-18"), UpdatedAtMillis: now, ProjectPartsJSON: `[{"id":"front","title":"Frontend","progress":50}]`}},
		AgendaTemplates:         []AgendaTemplateRecord{{ID: "pc-1", Text: "Valizi sil", Category: "Ev", Favorite: true, UpdatedAtMillis: now}},
		AgendaCategories:        []AgendaCategoryRecord{{ID: -1, Category: "Ev", Subcategory: "Düzen", UpdatedAtMillis: now}},
		HiddenAgendaSuggestions: []HiddenAgendaSuggestionRecord{{Key: "eski\x1fEv\x1f", HiddenAtMillis: now}},
		PrivateActivities:       []PrivateActivity{},
		Deletions:               []DeletionRecord{{Kind: "meal", ID: -4, UpdatedAtMillis: now}},
	}
	encoded, err := encodeSnapshot(want)
	if err != nil {
		t.Fatal(err)
	}
	got, err := decodeSnapshot(encoded)
	if err != nil {
		t.Fatal(err)
	}
	if got.Water[0].AmountMl != 125 || got.Agenda[0].Text != "Valizi sil" || got.Deletions[0].ID != -4 ||
		got.Agenda[0].ProjectPartsJSON != want.Agenda[0].ProjectPartsJSON ||
		got.Agenda[0].LastCompletedDay == nil || *got.Agenda[0].LastCompletedDay != "2026-09-18" ||
		got.AgendaTemplates[0].Text != "Valizi sil" || got.AgendaCategories[0].Category != "Ev" || got.HiddenAgendaSuggestions[0].Key == "" {
		t.Fatalf("unexpected round trip: %#v", got)
	}
}

func ptr(value string) *string { return &value }

func TestReadJavaCompatibleEncryptedPacket(t *testing.T) {
	plain := []byte("gunluk-takip")
	code := "12345678"
	salt := []byte("0123456789abcdef")
	nonce := []byte("0123456789ab")
	key := pbkdf2.Key([]byte(code), salt, 120_000, 32, sha256.New)
	block, _ := aes.NewCipher(key)
	var gcm cipher.AEAD
	gcm, _ = cipher.NewGCM(block)
	encrypted := gcm.Seal(nil, nonce, plain, additionalData)
	var packet bytes.Buffer
	_ = binary.Write(&packet, binary.BigEndian, wireMagic)
	packet.Write(salt)
	packet.Write(nonce)
	_ = binary.Write(&packet, binary.BigEndian, int32(len(encrypted)))
	packet.Write(encrypted)
	decoded, err := readSecurePacket(&packet, code)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(decoded, plain) {
		t.Fatalf("decoded %q", decoded)
	}
}
