package syncmodel

import (
	"bytes"
	"encoding/hex"
	"os"
	"reflect"
	"strings"
	"testing"
	"testing/quick"
)

func TestSharedJavaFixture(t *testing.T) {
	raw, err := os.ReadFile("../../../sync-protocol/src/test/resources/revision-v1.hex")
	if err != nil {
		t.Fatal(err)
	}
	want, err := hex.DecodeString(strings.TrimSpace(string(raw)))
	if err != nil {
		t.Fatal(err)
	}
	encoded, err := Encode([]Revision{rev(1, "note", false)})
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(encoded, want) {
		t.Fatal("wire format differs from Java fixture")
	}
	decoded, err := Decode(want)
	if err != nil || len(decoded) != 1 || decoded[0].Payload != "note" {
		t.Fatalf("%v %v", decoded, err)
	}
	for n := 0; n < len(encoded); n++ {
		if _, err := Decode(encoded[:n]); err == nil {
			t.Fatalf("accepted truncation at %d", n)
		}
	}
	if _, err = Decode(append(encoded, 0)); err == nil {
		t.Fatal("accepted trailing bytes")
	}
}

func TestCodecRoundTrip(t *testing.T) {
	property := func(payload string) bool {
		r, _ := normalize(rev(1, payload, false))
		encoded, err := Encode([]Revision{r})
		if err != nil {
			return false
		}
		decoded, err := Decode(encoded)
		return err == nil && reflect.DeepEqual([]Revision{r}, decoded)
	}
	if err := quick.Check(property, &quick.Config{MaxCount: 500}); err != nil {
		t.Fatal(err)
	}
}

func TestInvalidPacket(t *testing.T) {
	encoded, err := Encode([]Revision{rev(1, "note", false)})
	if err != nil {
		t.Fatal(err)
	}
	encoded[7] = 2
	if _, err = Decode(encoded); err == nil {
		t.Fatal("accepted unknown version")
	}
	if _, err = Encode([]Revision{rev(1, string([]byte{255}), false)}); err == nil {
		t.Fatal("accepted malformed UTF-8")
	}
	encoded[7] = 1
	encoded[len(encoded)-1] = 255
	if _, err = Decode(encoded); err == nil {
		t.Fatal("accepted malformed UTF-8 packet")
	}
}
